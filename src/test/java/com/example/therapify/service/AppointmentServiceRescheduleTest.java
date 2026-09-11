package com.example.therapify.service;

import com.example.therapify.dtos.AppointmentDTOs.AppointmentDetailDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentRescheduleRequestDTO;
import com.example.therapify.enums.Status;
import com.example.therapify.enums.UserType;
import com.example.therapify.exception.AccessDeniedException;
import com.example.therapify.exception.AppointmentConflictException;
import com.example.therapify.exception.AppointmentErrorCode;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rules of PATCH /appointments/{id}/reschedule, exercised against a fixed clock so the 24h
 * window is deterministic.
 *
 * Timeline: "now" is Friday 2026-09-11 09:00, the appointment under test sits on Friday
 * 2026-09-18 14:00 (a week away, comfortably outside the window), and the move targets Friday
 * 2026-09-25 16:00. The doctor's weekly template offers 14:00 and 16:00 on Fridays, so both the
 * old and the new slot are inside their agenda.
 */
class AppointmentServiceRescheduleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 9, 0);

    private static final Long DOCTOR_ID = 10L;
    private static final Long PATIENT_ID = 20L;
    private static final Long OTHER_PATIENT_ID = 30L;
    private static final Long APPOINTMENT_ID = 100L;

    private static final LocalDate ORIGINAL_DATE = LocalDate.of(2026, 9, 18);
    private static final LocalTime ORIGINAL_START = LocalTime.of(14, 0);
    private static final LocalTime ORIGINAL_END = LocalTime.of(15, 0);

    private static final String NEW_DATE = "2026-09-25";
    private static final String NEW_START = "16:00";
    private static final String NEW_END = "17:00";

    private AppointmentRepository appointmentRepository;
    private UserService userService;
    private EmailService emailService;
    private AppointmentService appointmentService;

    private User doctor;
    private User patient;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        userService = mock(UserService.class);
        emailService = mock(EmailService.class);

        Clock fixedClock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        // A real validator, not a mock: the slot rules it owns (future, weekly template) are
        // part of what rescheduling promises, so the tests should exercise them for real.
        appointmentService = new AppointmentService(
                appointmentRepository, userService, emailService,
                new AppointmentSlotValidator(fixedClock), fixedClock);

        doctor = user(DOCTOR_ID, "ana@therapify.com", "Ana", "Lopez", UserType.DOCTOR);
        doctor.setAvailability("{\"FRIDAY\":[\"14:00\",\"16:00\"]}");

        patient = user(PATIENT_ID, "juan@therapify.com", "Juan", "Perez", UserType.PACIENTE);

        // Saving is a no-op: the assertions run against the mutated entity itself, which is
        // exactly the point — the row is updated in place, never deleted and recreated.
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rescheduleMovesTheSameRowKeepsTheIdAndCountsTheMove() {

        Appointment ap = existingAppointment(Status.CONFIRMED, 0);
        authenticateAs(patient);
        slotIsFree();

        AppointmentDetailDTO dto = appointmentService.rescheduleAppointment(APPOINTMENT_ID, request());

        // Same row, same id.
        assertEquals(APPOINTMENT_ID, dto.getId());
        assertEquals(APPOINTMENT_ID, ap.getId());
        verify(appointmentRepository, never()).delete(any(Appointment.class));
        verify(appointmentRepository, never()).save(any(Appointment.class));

        // Moved to the new slot. The professional had confirmed the *old* one, and the patient
        // is the one moving it, so the turn goes back to PENDING for them to accept again.
        assertEquals(NEW_DATE, dto.getDate());
        assertEquals(NEW_START, dto.getStartTime());
        assertEquals(NEW_END, dto.getEndTime());
        assertEquals("PENDING", dto.getStatus());
        assertEquals(Status.PENDING, ap.getStatus());
        assertEquals(LocalDate.of(2026, 9, 25), ap.getDate());
        assertEquals(LocalTime.of(16, 0), ap.getStartTime());

        // The move is counted, and that is what the limit rule reads.
        assertEquals(1, dto.getRescheduleCount());
        assertEquals(1, ap.getRescheduleCount());

        verify(appointmentRepository).saveAndFlush(ap);
        verify(userService).evictDoctorCaches(DOCTOR_ID);

        // Both mails carry the old *and* the new slot.
        verify(emailService).sendAppointmentRescheduled(
                eq("juan@therapify.com"),
                eq("ana@therapify.com"),
                eq("Juan Perez"),
                eq("Ana Lopez"),
                eq("2026-09-18"), eq("14:00"), eq("15:00"),
                eq(NEW_DATE), eq(NEW_START), eq(NEW_END),
                // The status travels to the template, which turns PENDING into an explicit
                // "requiere tu confirmación nuevamente" line for the professional.
                eq("PENDING")
        );
    }

    /**
     * The professional moving their own confirmed turn keeps it confirmed: asking them to
     * re-accept a change they made themselves would be noise, and nothing about the agreement
     * is in doubt.
     */
    @Test
    void theProfessionalMovingTheirOwnTurnKeepsItConfirmed() {

        Appointment ap = existingAppointment(Status.CONFIRMED, 0);
        authenticateAs(doctor);
        slotIsFree();

        AppointmentDetailDTO dto = appointmentService.rescheduleAppointment(APPOINTMENT_ID, request());

        assertEquals("CONFIRMED", dto.getStatus());
        assertEquals(Status.CONFIRMED, ap.getStatus());
    }

    /** An admin move is not the professional's consent either, so it also needs re-confirming. */
    @Test
    void anAdminMoveAlsoSendsTheTurnBackToPending() {

        Appointment ap = existingAppointment(Status.CONFIRMED, 0);
        authenticateAs(user(99L, "admin@therapify.com", "Admin", "Therapify", UserType.ADMIN));
        slotIsFree();

        assertEquals("PENDING",
                appointmentService.rescheduleAppointment(APPOINTMENT_ID, request()).getStatus());
        assertEquals(Status.PENDING, ap.getStatus());
    }

    /** A turn that was never confirmed has nothing to lose: it stays PENDING. */
    @Test
    void movingAPendingTurnLeavesItPending() {

        Appointment ap = existingAppointment(Status.PENDING, 0);
        authenticateAs(patient);
        slotIsFree();

        assertEquals("PENDING",
                appointmentService.rescheduleAppointment(APPOINTMENT_ID, request()).getStatus());
        assertEquals(Status.PENDING, ap.getStatus());
        assertEquals(1, ap.getRescheduleCount());
    }

    @Test
    void takenSlotIsRejectedWithConflictAndTheSameCodeAsBooking() {

        Appointment ap = existingAppointment(Status.PENDING, 0);
        authenticateAs(patient);

        // Someone grabbed the slot between the screen loading and the confirmation.
        when(appointmentRepository.existsByDoctorIdAndDateAndStartTimeAndIdNot(
                DOCTOR_ID, LocalDate.of(2026, 9, 25), LocalTime.of(16, 0), APPOINTMENT_ID))
                .thenReturn(true);

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.rescheduleAppointment(APPOINTMENT_ID, request()));

        assertEquals(AppointmentErrorCode.SLOT_TAKEN, ex.getCode());
        assertEquals("Ese horario ya está reservado.", ex.getMessage());

        // Nothing moved and nobody was emailed.
        assertEquals(ORIGINAL_START, ap.getStartTime());
        assertEquals(0, ap.getRescheduleCount());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verifyNoRescheduleEmail();
    }

    @Test
    void someoneElsesAppointmentIsForbidden() {

        Appointment ap = existingAppointment(Status.PENDING, 0);
        User intruder = user(OTHER_PATIENT_ID, "otro@therapify.com", "Otro", "Paciente", UserType.PACIENTE);
        authenticateAs(intruder);
        slotIsFree();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> appointmentService.rescheduleAppointment(APPOINTMENT_ID, request()));

        assertEquals("El turno no existe o no tenés permiso para reprogramarlo.", ex.getMessage());
        assertEquals(ORIGINAL_START, ap.getStartTime());
        assertEquals(0, ap.getRescheduleCount());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verifyNoRescheduleEmail();
    }

    @Test
    void insideTheLast24HoursTheTurnCanNoLongerBeMoved() {

        // 23h away: Saturday 2026-09-12 08:00 against a "now" of Friday 09:00.
        Appointment ap = existingAppointment(Status.CONFIRMED, 0);
        ReflectionTestUtils.setField(ap, "date", LocalDate.of(2026, 9, 12));
        ReflectionTestUtils.setField(ap, "startTime", LocalTime.of(8, 0));
        authenticateAs(patient);
        slotIsFree();

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.rescheduleAppointment(APPOINTMENT_ID, request()));

        assertEquals(AppointmentErrorCode.RESCHEDULE_WINDOW_EXPIRED, ex.getCode());
        assertEquals("Solo podés reprogramar hasta 24 horas antes del turno.", ex.getMessage());
        assertEquals(0, ap.getRescheduleCount());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verifyNoRescheduleEmail();
    }

    @Test
    void aThirdRescheduleIsRejected() {

        Appointment ap = existingAppointment(Status.CONFIRMED, AppointmentService.MAX_RESCHEDULES);
        authenticateAs(patient);
        slotIsFree();

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.rescheduleAppointment(APPOINTMENT_ID, request()));

        assertEquals(AppointmentErrorCode.RESCHEDULE_LIMIT_REACHED, ex.getCode());
        assertEquals(AppointmentService.MAX_RESCHEDULES, ap.getRescheduleCount());
        assertEquals(ORIGINAL_START, ap.getStartTime());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verifyNoRescheduleEmail();
    }

    /**
     * Rescheduling is one of the flows evaluators are meant to try, so the demo accounts must
     * not be guarded the way their identity fields are on PUT /usuarios.
     */
    @Test
    void demoPatientCanReschedule() {

        User demoPatient = user(PATIENT_ID, "demo.paciente@therapify.com", "Paciente", "Demo", UserType.PACIENTE);
        Appointment ap = existingAppointment(Status.CONFIRMED, 0);
        ReflectionTestUtils.setField(ap, "patient", demoPatient);
        authenticateAs(demoPatient);
        slotIsFree();

        AppointmentDetailDTO dto = appointmentService.rescheduleAppointment(APPOINTMENT_ID, request());

        assertEquals(NEW_START, dto.getStartTime());
        assertEquals(1, dto.getRescheduleCount());
    }

    // ---- helpers ----

    private AppointmentRescheduleRequestDTO request() {
        AppointmentRescheduleRequestDTO dto = new AppointmentRescheduleRequestDTO();
        dto.setDate(NEW_DATE);
        dto.setStartTime(NEW_START);
        dto.setEndTime(NEW_END);
        return dto;
    }

    private Appointment existingAppointment(Status status, int rescheduleCount) {
        Appointment ap = new Appointment();
        ap.setDoctor(doctor);
        ap.setPatient(patient);
        ap.setDate(ORIGINAL_DATE);
        ap.setStartTime(ORIGINAL_START);
        ap.setEndTime(ORIGINAL_END);
        ap.setStatus(status);
        ap.setRescheduleCount(rescheduleCount);
        // id and createdAt are assigned by JPA (@GeneratedValue / @PrePersist), which never
        // runs against a mocked repository.
        ReflectionTestUtils.setField(ap, "id", APPOINTMENT_ID);
        ReflectionTestUtils.setField(ap, "createdAt", NOW.minusDays(3));

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(ap));
        return ap;
    }

    private User user(Long id, String email, String firstName, String lastName, UserType type) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail(email);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setUserType(type);
        u.setEnabled(true);
        return u;
    }

    private void authenticateAs(User user) {
        when(userService.getAuthenticatedUser()).thenReturn(user);
    }

    private void slotIsFree() {
        when(appointmentRepository.existsByDoctorIdAndDateAndStartTimeAndIdNot(
                anyLong(), any(LocalDate.class), any(LocalTime.class), anyLong()))
                .thenReturn(false);
    }

    private void verifyNoRescheduleEmail() {
        verify(emailService, never()).sendAppointmentRescheduled(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }
}
