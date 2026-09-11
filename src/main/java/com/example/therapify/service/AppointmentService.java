package com.example.therapify.service;

import com.example.therapify.dtos.AppointmentDTOs.AppointmentDetailDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentListDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentRequestDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentRescheduleRequestDTO;
import com.example.therapify.enums.Status;
import com.example.therapify.enums.UserType;
import com.example.therapify.exception.AccessDeniedException;
import com.example.therapify.exception.AppointmentConflictException;
import com.example.therapify.exception.AppointmentErrorCode;
import com.example.therapify.exception.InvalidAppointmentException;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    /** A turn may be moved twice; the third attempt is a cancel + re-book. */
    public static final int MAX_RESCHEDULES = 2;

    /**
     * Same 24h notice the UI already promises for cancellations ("podés cancelar sin costo hasta
     * 24 h antes"). Rescheduling inside that window would be a free cancellation through the
     * back door, so both rules share one number.
     */
    private static final Duration MIN_RESCHEDULE_NOTICE = Duration.ofHours(24);

    private final AppointmentRepository appointmentRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final AppointmentSlotValidator slotValidator;
    private final Clock clock;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            UserService userService,
            EmailService emailService,
            AppointmentSlotValidator slotValidator,
            Clock clock
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userService = userService;
        this.emailService = emailService;
        this.slotValidator = slotValidator;
        this.clock = clock;
    }

    public enum AppointmentTimeFilter { UPCOMING, PAST }

    /**
     * Books a new appointment.
     *
     * Validations run in the same order, and answer with the same codes, as the reschedule
     * flow - both delegate the slot rules to {@link AppointmentSlotValidator}:
     *   1. malformed / inverted range / past slot / outside the agenda -> 400
     *   2. slot already booked                                        -> 409 SLOT_TAKEN
     *
     * Until this existed the endpoint accepted whatever the client sent, which is how a turn
     * for 08:00 got booked at 17:57 of the same day and was born already expired.
     */
    @Transactional
    public AppointmentDetailDTO createAppointment(AppointmentRequestDTO dto) {

        User patient = userService.getAuthenticatedUser();
        User doctor = userService.findEntityById(dto.getDoctorId());

        LocalDate date = slotValidator.parseDate(dto.getDate());
        LocalTime startTime = slotValidator.parseTime(dto.getStartTime(), "startTime");
        LocalTime endTime = slotValidator.parseTime(dto.getEndTime(), "endTime");

        // 1. Coherent range, in the future, inside the professional's weekly template.
        slotValidator.validateBookableSlot(doctor, date, startTime, endTime);

        // 2. Free slot.
        boolean alreadyUsed = appointmentRepository
                .existsByDoctorIdAndDateAndStartTime(
                        doctor.getId(),
                        date,
                        startTime
                );

        if (alreadyUsed) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.SLOT_TAKEN,
                    "Ese horario ya está reservado.");
        }

        Appointment ap = new Appointment();
        ap.setDoctor(doctor);
        ap.setPatient(patient);
        ap.setDate(date);
        ap.setStartTime(startTime);
        ap.setEndTime(endTime);
        ap.setStatus(Status.PENDING);

        try {
            // The exists-check above is not race-proof under concurrent requests for the
            // same slot; the DB unique constraint on (doctor_id, date, start_time) is the
            // real guard, and this catch turns that violation into a 409 instead of a 500.
            // Flushed explicitly so the violation is raised here rather than at commit time,
            // where it would escape this catch and surface as a 500.
            appointmentRepository.saveAndFlush(ap);
        } catch (DataIntegrityViolationException e) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.SLOT_TAKEN,
                    "Ese horario ya está reservado.");
        }

        userService.evictDoctorCaches(doctor.getId());

        emailService.sendAppointmentConfirmation(
                patient.getEmail(),
                doctor.getEmail(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                date.toString(),
                startTime.toString(),
                endTime.toString()
        );

        return toDetailDTO(ap);
    }

    /**
     * Moves an existing appointment to another slot of the same doctor's calendar.
     *
     * Not folded into the generic PATCH on purpose: freeing one slot and taking another is a
     * single atomic swap with rules of its own (ownership, 24h notice, slot availability, a
     * move limit), none of which a partial field update can express or validate.
     *
     * The whole check-then-write sequence runs in one transaction, and the row is read with
     * SELECT ... FOR UPDATE so two concurrent reschedules of the same appointment queue up
     * instead of both passing the checks. The row is updated in place — the id never changes,
     * so links, reviews and any frontend state keyed by it stay valid.
     *
     * Validations run in the documented order; each one maps to a single HTTP status:
     *   1. not yours / doesn't exist      -> 403
     *   2. already COMPLETED / EXPIRED    -> 409 APPOINTMENT_COMPLETED / APPOINTMENT_EXPIRED
     *   3. under 24h notice               -> 409 RESCHEDULE_WINDOW_EXPIRED
     *   4. malformed / other doctor / past-> 400
     *   5. new slot already booked        -> 409 SLOT_TAKEN
     *   6. move limit reached             -> 409 RESCHEDULE_LIMIT_REACHED
     */
    @Transactional
    public AppointmentDetailDTO rescheduleAppointment(Long id, AppointmentRescheduleRequestDTO dto) {

        // 1. Existence and ownership: either participant, or an admin. Both failures answer
        //    the same 403 — see loadAuthorized.
        Appointment ap = loadAsParticipant(id, "reprogramarlo").appointment();

        // 2. Both terminal states are history, not bookings. EXPIRED would also be caught by
        //    the 24h rule below (it is necessarily in the past), but that would answer
        //    RESCHEDULE_WINDOW_EXPIRED, which reads as "you were too late" instead of "this
        //    turn was never confirmed and is closed".
        if (ap.getStatus() == Status.COMPLETED) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.APPOINTMENT_COMPLETED,
                    "Un turno ya realizado no puede reprogramarse.");
        }

        if (ap.getStatus() == Status.EXPIRED) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.APPOINTMENT_EXPIRED,
                    "Este turno venció sin ser confirmado. Reservá uno nuevo.");
        }

        // 3. Same window as the cancellation policy, measured against the ORIGINAL slot.
        LocalDateTime now = LocalDateTime.now(clock);
        if (!ap.startsAt().isAfter(now.plus(MIN_RESCHEDULE_NOTICE))) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.RESCHEDULE_WINDOW_EXPIRED,
                    "Solo podés reprogramar hasta 24 horas antes del turno.");
        }

        // 4. The new slot: parseable, same professional, in the future, inside their agenda.
        LocalDate newDate = slotValidator.parseDate(dto.getDate());
        LocalTime newStartTime = slotValidator.parseTime(dto.getStartTime(), "startTime");
        LocalTime newEndTime = slotValidator.parseTime(dto.getEndTime(), "endTime");

        User doctor = ap.getDoctor();

        // Reschedule-only rule: a move never changes professional.
        if (dto.getDoctorId() != null && !dto.getDoctorId().equals(doctor.getId())) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.DOCTOR_CHANGE_NOT_ALLOWED,
                    "No se puede cambiar de profesional al reprogramar. Cancelá el turno y reservá uno nuevo.");
        }

        // Reschedule-only rule: moving a turn onto itself would burn one of the two allowed
        // moves for nothing.
        if (newDate.equals(ap.getDate()) && newStartTime.equals(ap.getStartTime())) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.SAME_SLOT,
                    "El turno ya está agendado en ese horario.");
        }

        // Shared with POST /appointments: coherent range, future, inside the weekly template.
        slotValidator.validateBookableSlot(doctor, newDate, newStartTime, newEndTime);

        // 5. Free slot. Excludes this appointment's own row, which still holds the old slot.
        boolean alreadyUsed = appointmentRepository
                .existsByDoctorIdAndDateAndStartTimeAndIdNot(
                        doctor.getId(),
                        newDate,
                        newStartTime,
                        ap.getId()
                );

        if (alreadyUsed) {
            // Same message and code POST /appointments returns, so the frontend's existing 409
            // handling works unchanged.
            throw new AppointmentConflictException(
                    AppointmentErrorCode.SLOT_TAKEN,
                    "Ese horario ya está reservado.");
        }

        // 6. Move limit.
        if (ap.getRescheduleCount() >= MAX_RESCHEDULES) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.RESCHEDULE_LIMIT_REACHED,
                    "Este turno ya fue reprogramado " + MAX_RESCHEDULES
                            + " veces. Cancelalo y reservá uno nuevo.");
        }

        User patient = ap.getPatient();
        String previousDate = ap.getDate().toString();
        String previousStartTime = ap.getStartTime().toString();
        String previousEndTime = ap.getEndTime().toString();

        ap.applyReschedule(newDate, newStartTime, newEndTime);

        try {
            // Flushed explicitly, not left to commit: the unique constraint on
            // (doctor_id, date, start_time) is what really serialises two patients racing for
            // the same new slot, and it has to fire while we are still inside this try to be
            // translatable into a 409 instead of escaping as a 500 at commit time.
            appointmentRepository.saveAndFlush(ap);
        } catch (DataIntegrityViolationException e) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.SLOT_TAKEN,
                    "Ese horario ya está reservado.");
        }

        userService.evictDoctorCaches(doctor.getId());

        // Mail goes out only once the swap is durable, and a webhook failure is logged instead
        // of propagated: the reschedule is already committed, so the response stays 200.
        runAfterCommit(() -> emailService.sendAppointmentRescheduled(
                patient.getEmail(),
                doctor.getEmail(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                previousDate,
                previousStartTime,
                previousEndTime,
                newDate.toString(),
                newStartTime.toString(),
                newEndTime.toString(),
                ap.getStatus().name()
        ));

        return toDetailDTO(ap);
    }

    /**
     * Loads an appointment for a caller who must be one of its two participants — the patient
     * who booked it or the assigned professional — or an admin. Used by rescheduling and
     * cancellation, both of which either side may legitimately do.
     */
    private AuthorizedAppointment loadAsParticipant(Long id, String action) {
        return loadAuthorized(id, action, AppointmentService::isParticipantOrAdmin);
    }

    /**
     * Loads an appointment for a caller who must be the assigned professional (or an admin).
     * Status is the professional's call: the patient books and cancels, but does not get to
     * declare a session confirmed or done.
     */
    private AuthorizedAppointment loadAsAssignedDoctor(Long id, String action) {
        return loadAuthorized(id, action, AppointmentService::isAssignedDoctorOrAdmin);
    }

    /**
     * The one place that turns an id into an appointment the caller is allowed to act on.
     *
     * Two things are deliberate. Existence and permission answer the *same* 403: a 404 for a
     * missing id would let anyone enumerate which appointments exist by walking the path
     * variable, and the message names no participant either. And the row is read with
     * SELECT ... FOR UPDATE, so a check here cannot be invalidated by a concurrent write
     * between the check and the write that follows it.
     *
     * @param action infinitive used in the message ("reprogramarlo", "cancelarlo"), so the
     *               caller sees which operation was refused without learning anything else.
     */
    private AuthorizedAppointment loadAuthorized(
            Long id, String action, BiPredicate<User, Appointment> rule) {
        User requester = userService.getAuthenticatedUser();

        Appointment ap = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> forbidden(action));

        if (!rule.test(requester, ap)) {
            throw forbidden(action);
        }

        return new AuthorizedAppointment(requester, ap);
    }

    /**
     * An appointment plus the caller that was authorised to act on it. The caller matters after
     * the check too: cancellation has to say whether it was the patient who cancelled, and
     * re-reading the authenticated user later would be a second query for something already
     * known.
     */
    private record AuthorizedAppointment(User requester, Appointment appointment) {

        boolean requesterIsThePatient() {
            return requester.getId().equals(appointment.getPatient().getId());
        }
    }

    private static AccessDeniedException forbidden(String action) {
        return new AccessDeniedException(
                "El turno no existe o no tenés permiso para " + action + ".");
    }

    /** Owner patient, the assigned professional, or an admin. */
    private static boolean isParticipantOrAdmin(User requester, Appointment ap) {
        if (requester.getUserType() == UserType.ADMIN) return true;

        Long requesterId = requester.getId();
        return requesterId.equals(ap.getPatient().getId())
                || requesterId.equals(ap.getDoctor().getId());
    }

    /** The assigned professional, or an admin. */
    private static boolean isAssignedDoctorOrAdmin(User requester, Appointment ap) {
        if (requester.getUserType() == UserType.ADMIN) return true;

        return requester.getId().equals(ap.getDoctor().getId());
    }

    /**
     * Runs the action once the surrounding transaction commits, swallowing (and logging) any
     * failure. Falls back to running inline when there is no transaction in progress, so the
     * same code path works in plain unit tests.
     */
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runQuietly(action);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runQuietly(action);
            }
        });
    }

    private void runQuietly(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Falló el envío de un email de turno; la operación ya está confirmada.", e);
        }
    }

    public Page<AppointmentListDTO> getMyAppointments(
            AppointmentTimeFilter timeFilter,
            Status status,
            Pageable pageable
    ) {
        User user = userService.getAuthenticatedUser();
        boolean isAdmin = user.getUserType().name().equals("ADMIN");

        LocalDate today = LocalDate.now();
        LocalDate fromDate = timeFilter == AppointmentTimeFilter.UPCOMING ? today : null;
        LocalDate toDate = timeFilter == AppointmentTimeFilter.PAST ? today : null;

        return appointmentRepository
                .findMyAppointments(
                        isAdmin ? null : user.getId(),
                        fromDate,
                        toDate,
                        status,
                        pageable
                )
                .map(this::toListDTO);
    }

    /**
     * The professional's status update. In practice the frontend calls it for exactly one
     * thing — "Confirmar turno", PENDING -> CONFIRMED — and everything else this endpoint used
     * to accept was unintended capability:
     *
     *   1. no ownership check at all, so any professional could write any other's appointments;
     *   2. no transition rules, so a future turn could be marked COMPLETED, and COMPLETED is
     *      what ReviewService accepts as proof of a past session. That handed out review
     *      rights for pairs that had never met.
     *
     * Both are closed here: the row is loaded through the same authorisation path as
     * rescheduling, and the target status has to be reachable (see validateTransition).
     *
     *   not yours / missing       -> 403
     *   status absent or unknown  -> 400 INVALID_STATUS
     *   transition not allowed    -> 409 INVALID_STATUS_TRANSITION
     */
    @Transactional
    public AppointmentDetailDTO updateAppointmentStatus(Long id, String statusStr) {

        // Authorisation before parsing: a caller with no business touching this row learns
        // nothing about whether their body was well-formed.
        Appointment ap = loadAsAssignedDoctor(id, "modificarlo").appointment();

        Status target = parseStatus(statusStr);
        validateTransition(ap, target);

        ap.setStatus(target);
        appointmentRepository.save(ap);
        userService.evictDoctorCaches(ap.getDoctor().getId());

        return toDetailDTO(ap);
    }

    /**
     * Status arrives as a raw string out of a Map body, so both "absent" and "not a Status"
     * are ordinary client mistakes. They used to be an NPE and an unhandled
     * IllegalArgumentException respectively — two 500s.
     */
    private Status parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.INVALID_STATUS,
                    "El campo status es obligatorio. Valores posibles: " + statusValues() + ".");
        }

        try {
            return Status.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // The received value is not echoed back on purpose; it is attacker-controlled text
            // and the list of valid values is what actually helps the caller.
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.INVALID_STATUS,
                    "El estado enviado no es válido. Valores posibles: " + statusValues() + ".");
        }
    }

    private static String statusValues() {
        return Arrays.stream(Status.values()).map(Status::name).collect(Collectors.joining(", "));
    }

    /**
     * The appointment state machine:
     *
     *   PENDING   -> CONFIRMED   always (the professional accepts the slot)
     *   PENDING   -> EXPIRED     only once the slot has passed
     *   CONFIRMED -> COMPLETED   only once the slot has passed
     *   COMPLETED / EXPIRED      terminal, nothing leaves them
     *
     * "Has passed" is measured the same way the hourly job measures it — end of the
     * appointment against the application clock — so the manual path and the automatic one can
     * never disagree about whether a turn is over. Without that condition, ownership alone
     * would still let a professional mark their own future turn COMPLETED and unlock a review
     * for a patient they have not seen yet.
     */
    private void validateTransition(Appointment ap, Status target) {
        Status current = ap.getStatus();
        boolean finished = ap.endsAt().isBefore(LocalDateTime.now(clock));

        if (current == Status.COMPLETED || current == Status.EXPIRED) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.INVALID_STATUS_TRANSITION,
                    "El turno ya está cerrado (" + current.name() + ") y no puede cambiar de estado.");
        }

        boolean allowed = switch (current) {
            case PENDING -> target == Status.CONFIRMED || (target == Status.EXPIRED && finished);
            case CONFIRMED -> target == Status.COMPLETED && finished;
            case COMPLETED, EXPIRED -> false;
        };

        if (allowed) return;

        if (!finished && (target == Status.COMPLETED || target == Status.EXPIRED)) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.INVALID_STATUS_TRANSITION,
                    "El turno todavía no terminó, así que no puede marcarse como "
                            + target.name() + ".");
        }

        throw new AppointmentConflictException(
                AppointmentErrorCode.INVALID_STATUS_TRANSITION,
                "No se puede pasar de " + current.name() + " a " + target.name() + ".");
    }

    /**
     * Cancels an appointment, which today means deleting the row.
     *
     * It had no ownership check whatsoever: any authenticated professional could delete any
     * other professional's appointment with their patient, irreversibly. It is now restricted
     * to the two participants and admins, through the same authorisation path as rescheduling.
     *
     * Either participant may cancel, and there is no notice window: the booking screen promises
     * cancelling is free up to 24h before, which is a statement about cost, not about being
     * locked out — and there is no billing to make that distinction mean anything yet.
     *
     * Both sides are emailed once the deletion commits — cancelling used to be the only
     * lifecycle event that notified nobody, which left the professional holding a slot for
     * someone who was not coming.
     *
     *   not yours / missing        -> 403
     *   already COMPLETED/EXPIRED  -> 409
     */
    @Transactional
    public boolean deleteAppointment(Long id) {

        AuthorizedAppointment authorized = loadAsParticipant(id, "cancelarlo");
        Appointment ap = authorized.appointment();

        // Terminal states are history: deleting them would erase the record of a session that
        // happened (COMPLETED) or of a slot that went by unconfirmed (EXPIRED).
        if (ap.getStatus() == Status.COMPLETED) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.APPOINTMENT_COMPLETED,
                    "Un turno ya realizado no puede cancelarse.");
        }

        if (ap.getStatus() == Status.EXPIRED) {
            throw new AppointmentConflictException(
                    AppointmentErrorCode.APPOINTMENT_EXPIRED,
                    "Este turno ya venció y no puede cancelarse.");
        }

        User patient = ap.getPatient();
        User doctor = ap.getDoctor();
        Long doctorId = doctor.getId();

        // Read off the entity before it is gone: after the delete the row no longer exists, and
        // the mail runs later, once the transaction has committed.
        String date = ap.getDate().toString();
        String startTime = ap.getStartTime().toString();
        String endTime = ap.getEndTime().toString();
        boolean cancelledByPatient = authorized.requesterIsThePatient();

        appointmentRepository.delete(ap);
        userService.evictDoctorCaches(doctorId);

        // Same contract as rescheduling: the mail goes out only once the cancellation is
        // durable, and a webhook failure is logged instead of propagated, so the response stays
        // 200 for an operation that already happened.
        runAfterCommit(() -> emailService.sendAppointmentCancelled(
                patient.getEmail(),
                doctor.getEmail(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                date,
                startTime,
                endTime,
                cancelledByPatient
        ));

        return true;
    }

    public List<AppointmentListDTO> getAppointmentsByDoctorAndDate(Long doctorId, String date) {

        LocalDate localDate = LocalDate.parse(date);

        return appointmentRepository
                .findByDoctorIdAndDate(doctorId, localDate)
                .stream()
                .map(this::toListDTO)
                .toList();
    }

    private AppointmentDetailDTO toDetailDTO(Appointment ap) {
        AppointmentDetailDTO dto = new AppointmentDetailDTO();

        dto.setId(ap.getId());
        dto.setDoctorId(ap.getDoctor().getId());
        dto.setPatientId(ap.getPatient().getId());
        dto.setDate(ap.getDate().toString());
        dto.setStartTime(ap.getStartTime().toString());
        dto.setEndTime(ap.getEndTime().toString());
        dto.setStatus(ap.getStatus().name());
        dto.setCreatedAt(ap.getCreatedAt().toString());
        dto.setRescheduleCount(ap.getRescheduleCount());

        return dto;
    }

    private AppointmentListDTO toListDTO(Appointment ap) {
        AppointmentListDTO dto = new AppointmentListDTO();

        dto.setId(ap.getId());
        dto.setDate(ap.getDate().toString());
        dto.setStartTime(ap.getStartTime().toString());
        dto.setEndTime(ap.getEndTime().toString());
        dto.setStatus(ap.getStatus().name());
        dto.setDoctorId(ap.getDoctor().getId());
        dto.setPatientId(ap.getPatient().getId());
        dto.setCreatedAt(ap.getCreatedAt().toString());
        dto.setRescheduleCount(ap.getRescheduleCount());

        // ap.getPatient()/ap.getDoctor() are already loaded associations — no need for
        // extra findEntityById lookups (that was a per-row N+1 on every appointment list).
        User patient = ap.getPatient();
        dto.setPatientName(patient.getFirstName() + " " + patient.getLastName());

        User doctor = ap.getDoctor();
        dto.setDoctorName(doctor.getFirstName() + " " + doctor.getLastName());
        dto.setDoctorSpecialty(doctor.getSpecialty() != null ? doctor.getSpecialty().name() : null);

        return dto;
    }
}
