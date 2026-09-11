package com.example.therapify.service;

import com.example.therapify.dtos.AppointmentDTOs.AppointmentDetailDTO;
import com.example.therapify.enums.Status;
import com.example.therapify.enums.UserType;
import com.example.therapify.exception.AccessDeniedException;
import com.example.therapify.exception.AppointmentConflictException;
import com.example.therapify.exception.AppointmentErrorCode;
import com.example.therapify.exception.InvalidAppointmentException;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Access control and state machine of DELETE /appointments/{id} and PATCH /appointments/{id},
 * the two endpoints that had no ownership check at all.
 *
 * Timeline: "now" is Friday 2026-09-11 at 09:00. A PAST appointment sits on 2026-09-10 (ended
 * yesterday) and a FUTURE one on 2026-09-18, so "has the turn finished?" is unambiguous.
 */
class AppointmentServiceAccessControlTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 9, 0);

    private static final Long DOCTOR_ID = 10L;
    private static final Long PATIENT_ID = 20L;
    private static final Long OTHER_DOCTOR_ID = 30L;
    private static final Long ADMIN_ID = 40L;
    private static final Long APPOINTMENT_ID = 100L;

    private static final LocalDate PAST = LocalDate.of(2026, 9, 10);
    private static final LocalDate FUTURE = LocalDate.of(2026, 9, 18);

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

        appointmentService = new AppointmentService(
                appointmentRepository, userService, emailService,
                new AppointmentSlotValidator(fixedClock), fixedClock);

        doctor = user(DOCTOR_ID, "ana@therapify.com", UserType.DOCTOR);
        patient = user(PATIENT_ID, "juan@therapify.com", UserType.PACIENTE);

        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- DELETE

    /** The destructive one: deleting another professional's appointment was irreversible. */
    @Test
    void anotherProfessionalCannotDeleteTheAppointment() {

        appointment(FUTURE, Status.CONFIRMED);
        authenticateAs(user(OTHER_DOCTOR_ID, "otra@therapify.com", UserType.DOCTOR));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> appointmentService.deleteAppointment(APPOINTMENT_ID));

        // Same message a missing id produces: nothing leaks about which ids exist.
        assertEquals("El turno no existe o no tenés permiso para cancelarlo.", ex.getMessage());
        verify(appointmentRepository, never()).delete(any(Appointment.class));
        verify(userService, never()).evictDoctorCaches(anyLong());
        verifyNoCancellationEmail();
    }

    @Test
    void aMissingAppointmentAnswersTheSameForbidden() {

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.empty());
        authenticateAs(patient);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> appointmentService.deleteAppointment(APPOINTMENT_ID));

        assertEquals("El turno no existe o no tenés permiso para cancelarlo.", ex.getMessage());
    }

    /** The broken feature: the patient's own "Cancelar" button used to get a 403. */
    @Test
    void thePatientCanCancelTheirOwnAppointmentAndBothSidesAreNotified() {

        Appointment ap = appointment(FUTURE, Status.CONFIRMED);
        authenticateAs(patient);

        assertTrue(appointmentService.deleteAppointment(APPOINTMENT_ID));

        verify(appointmentRepository).delete(ap);
        verify(userService).evictDoctorCaches(DOCTOR_ID);

        // The slot the professional gets back is spelled out, and the flag says the patient is
        // the one who cancelled — that is what lets the doctor's mail name them.
        verify(emailService).sendAppointmentCancelled(
                eq("juan@therapify.com"),
                eq("ana@therapify.com"),
                eq("Nombre Apellido"),
                eq("Nombre Apellido"),
                eq("2026-09-18"), eq("10:00"), eq("11:00"),
                eq(true));
    }

    /**
     * Either participant may cancel, so the flag has to distinguish them: claiming "the patient
     * cancelled" in a mail triggered by the professional themselves would be plainly wrong.
     */
    @Test
    void whenTheProfessionalCancelsTheMailDoesNotBlameThePatient() {

        appointment(FUTURE, Status.CONFIRMED);
        authenticateAs(doctor);

        assertTrue(appointmentService.deleteAppointment(APPOINTMENT_ID));

        verify(emailService).sendAppointmentCancelled(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(),
                eq(false));
    }

    /**
     * The cancellation is already committed by the time the mail is attempted, so a webhook
     * failure must not turn a done operation into an error.
     */
    @Test
    void aFailingMailDoesNotBreakTheCancellation() {

        Appointment ap = appointment(FUTURE, Status.PENDING);
        authenticateAs(patient);

        doThrow(new RuntimeException("el webhook no responde"))
                .when(emailService).sendAppointmentCancelled(
                        anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyBoolean());

        assertTrue(appointmentService.deleteAppointment(APPOINTMENT_ID));
        verify(appointmentRepository).delete(ap);
    }

    @Test
    void theAssignedProfessionalAndAdminsCanAlsoCancel() {

        Appointment ap = appointment(FUTURE, Status.PENDING);
        authenticateAs(doctor);
        assertTrue(appointmentService.deleteAppointment(APPOINTMENT_ID));
        verify(appointmentRepository).delete(ap);

        Appointment other = appointment(FUTURE, Status.PENDING);
        authenticateAs(user(ADMIN_ID, "admin@therapify.com", UserType.ADMIN));
        assertTrue(appointmentService.deleteAppointment(APPOINTMENT_ID));
        verify(appointmentRepository).delete(other);
    }

    @Test
    void aTerminalAppointmentCannotBeDeleted() {

        appointment(PAST, Status.COMPLETED);
        authenticateAs(patient);

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.deleteAppointment(APPOINTMENT_ID));

        assertEquals(AppointmentErrorCode.APPOINTMENT_COMPLETED, ex.getCode());
        assertEquals("Un turno ya realizado no puede cancelarse.", ex.getMessage());

        appointment(PAST, Status.EXPIRED);

        AppointmentConflictException expired = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.deleteAppointment(APPOINTMENT_ID));

        assertEquals(AppointmentErrorCode.APPOINTMENT_EXPIRED, expired.getCode());

        verify(appointmentRepository, never()).delete(any(Appointment.class));
        verifyNoCancellationEmail();
    }

    // ---------------------------------------------------------------- PATCH status

    /** Verified in production before the fix: any professional could write any other's row. */
    @Test
    void anotherProfessionalCannotChangeTheStatus() {

        Appointment ap = appointment(FUTURE, Status.PENDING);
        authenticateAs(user(OTHER_DOCTOR_ID, "otra@therapify.com", UserType.DOCTOR));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "COMPLETED"));

        assertEquals("El turno no existe o no tenés permiso para modificarlo.", ex.getMessage());
        assertEquals(Status.PENDING, ap.getStatus());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    /** The patient books and cancels, but does not get to declare a session confirmed or done. */
    @Test
    void thePatientCannotChangeTheStatusOfTheirOwnAppointment() {

        Appointment ap = appointment(PAST, Status.CONFIRMED);
        authenticateAs(patient);

        assertThrows(AccessDeniedException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "COMPLETED"));

        assertEquals(Status.CONFIRMED, ap.getStatus());
    }

    /** The only path the frontend actually uses: the doctor's "Confirmar turno" button. */
    @Test
    void confirmingAPendingAppointmentIsAllowed() {

        Appointment ap = appointment(FUTURE, Status.PENDING);
        authenticateAs(doctor);

        AppointmentDetailDTO dto =
                appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "CONFIRMED");

        assertEquals("CONFIRMED", dto.getStatus());
        assertEquals(Status.CONFIRMED, ap.getStatus());
        verify(appointmentRepository).save(ap);
        verify(userService).evictDoctorCaches(DOCTOR_ID);
    }

    /**
     * The core of the review exploit: ownership alone would still let the professional mark
     * their own future turn COMPLETED, and COMPLETED is what unlocks a review.
     */
    @Test
    void completingAFutureAppointmentIsRejected() {

        Appointment ap = appointment(FUTURE, Status.CONFIRMED);
        authenticateAs(doctor);

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "COMPLETED"));

        assertEquals(AppointmentErrorCode.INVALID_STATUS_TRANSITION, ex.getCode());
        assertEquals("El turno todavía no terminó, así que no puede marcarse como COMPLETED.",
                ex.getMessage());
        assertEquals(Status.CONFIRMED, ap.getStatus());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void completingAConfirmedAppointmentThatAlreadyFinishedIsAllowed() {

        Appointment ap = appointment(PAST, Status.CONFIRMED);
        authenticateAs(doctor);

        assertEquals("COMPLETED",
                appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "COMPLETED").getStatus());
        assertEquals(Status.COMPLETED, ap.getStatus());
    }

    /** The professional's "expire" action, bounded to PENDING turns whose slot already passed. */
    @Test
    void expiringIsAllowedOnlyOnceThePendingSlotHasPassed() {

        Appointment past = appointment(PAST, Status.PENDING);
        authenticateAs(doctor);
        assertEquals("EXPIRED",
                appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "EXPIRED").getStatus());
        assertEquals(Status.EXPIRED, past.getStatus());

        Appointment future = appointment(FUTURE, Status.PENDING);
        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "EXPIRED"));

        assertEquals(AppointmentErrorCode.INVALID_STATUS_TRANSITION, ex.getCode());
        assertEquals(Status.PENDING, future.getStatus());
    }

    @Test
    void aTerminalStatusNeverChangesAgain() {

        Appointment ap = appointment(PAST, Status.COMPLETED);
        authenticateAs(doctor);

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "CONFIRMED"));

        assertEquals(AppointmentErrorCode.INVALID_STATUS_TRANSITION, ex.getCode());
        assertEquals("El turno ya está cerrado (COMPLETED) y no puede cambiar de estado.",
                ex.getMessage());
        assertEquals(Status.COMPLETED, ap.getStatus());
    }

    @Test
    void skippingConfirmationIsRejected() {

        Appointment ap = appointment(PAST, Status.PENDING);
        authenticateAs(doctor);

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "COMPLETED"));

        assertEquals(AppointmentErrorCode.INVALID_STATUS_TRANSITION, ex.getCode());
        assertEquals("No se puede pasar de PENDING a COMPLETED.", ex.getMessage());
        assertEquals(Status.PENDING, ap.getStatus());
    }

    // ---------------------------------------------------------------- malformed body

    /** Used to be an NPE, i.e. a 500, when the body carried no "status" key. */
    @Test
    void aBodyWithoutStatusIsABadRequest() {

        appointment(FUTURE, Status.PENDING);
        authenticateAs(doctor);

        InvalidAppointmentException ex = assertThrows(InvalidAppointmentException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, null));

        assertEquals(AppointmentErrorCode.INVALID_STATUS, ex.getCode());
        assertTrue(ex.getMessage().contains("obligatorio"));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    /** Used to be an unhandled IllegalArgumentException from Status.valueOf, i.e. a 500. */
    @Test
    void anUnknownStatusIsABadRequest() {

        appointment(FUTURE, Status.PENDING);
        authenticateAs(doctor);

        InvalidAppointmentException ex = assertThrows(InvalidAppointmentException.class,
                () -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, "CANCELLED"));

        assertEquals(AppointmentErrorCode.INVALID_STATUS, ex.getCode());
        // The received value is not echoed back; the list of valid ones is.
        assertTrue(ex.getMessage().contains("PENDING, CONFIRMED, COMPLETED, EXPIRED"));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    // ---- helpers ----

    private Appointment appointment(LocalDate date, Status status) {
        Appointment ap = new Appointment();
        ap.setDoctor(doctor);
        ap.setPatient(patient);
        ap.setDate(date);
        ap.setStartTime(LocalTime.of(10, 0));
        ap.setEndTime(LocalTime.of(11, 0));
        ap.setStatus(status);
        ReflectionTestUtils.setField(ap, "id", APPOINTMENT_ID);
        ReflectionTestUtils.setField(ap, "createdAt", NOW.minusDays(5));

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(ap));
        return ap;
    }

    private void verifyNoCancellationEmail() {
        verify(emailService, never()).sendAppointmentCancelled(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyBoolean());
    }

    private User user(Long id, String email, UserType type) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail(email);
        u.setFirstName("Nombre");
        u.setLastName("Apellido");
        u.setUserType(type);
        u.setEnabled(true);
        return u;
    }

    private void authenticateAs(User user) {
        when(userService.getAuthenticatedUser()).thenReturn(user);
    }
}
