package com.example.therapify.service;

import com.example.therapify.dtos.AppointmentDTOs.AppointmentDetailDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentRequestDTO;
import com.example.therapify.enums.UserType;
import com.example.therapify.exception.AppointmentConflictException;
import com.example.therapify.exception.AppointmentErrorCode;
import com.example.therapify.exception.InvalidAppointmentException;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

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
 * Validation of POST /appointments, which until now accepted whatever the client sent.
 *
 * Timeline: "now" is Friday 2026-09-11 at 09:00. The professional works Fridays at 09:00,
 * 14:00 and 16:00, so a Friday 14:00 next week is legal, today's 08:00 is already gone, and
 * 03:00 is an hour they never work.
 */
class AppointmentServiceCreateValidationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 9, 0);

    private static final Long DOCTOR_ID = 10L;
    private static final Long PATIENT_ID = 20L;
    private static final Long NEW_APPOINTMENT_ID = 500L;

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

        // The real validator: these tests are about the rules it enforces.
        appointmentService = new AppointmentService(
                appointmentRepository, userService, emailService,
                new AppointmentSlotValidator(fixedClock), fixedClock);

        doctor = user(DOCTOR_ID, "ana@therapify.com", "Ana", "Lopez", UserType.DOCTOR);
        doctor.setAvailability("{\"FRIDAY\":[\"09:00\",\"14:00\",\"16:00\"]}");

        patient = user(PATIENT_ID, "juan@therapify.com", "Juan", "Perez", UserType.PACIENTE);

        when(userService.getAuthenticatedUser()).thenReturn(patient);
        when(userService.findEntityById(DOCTOR_ID)).thenReturn(doctor);

        // Stands in for what JPA does on insert (@GeneratedValue + @PrePersist), which never
        // runs against a mocked repository.
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", NEW_APPOINTMENT_ID);
            ReflectionTestUtils.setField(saved, "createdAt", NOW);
            return saved;
        });
    }

    /**
     * The reported bug: a turn booked at 17:57 for 08:00 of the same day was accepted and born
     * already expired, which is what sent the frontend into its retry loop.
     */
    @Test
    void bookingAPastHourOfTodayIsRejected() {

        InvalidAppointmentException ex = assertThrows(InvalidAppointmentException.class,
                () -> appointmentService.createAppointment(request("2026-09-11", "08:00", "09:00")));

        assertEquals(AppointmentErrorCode.SLOT_IN_PAST, ex.getCode());
        assertEquals("El horario del turno debe ser futuro.", ex.getMessage());
        nothingWasWritten();
    }

    @Test
    void bookingAnHourOutsideTheWeeklyTemplateIsRejected() {

        // A Friday, so the day is right — but the professional does not work at 03:00.
        InvalidAppointmentException ex = assertThrows(InvalidAppointmentException.class,
                () -> appointmentService.createAppointment(request("2026-09-18", "03:00", "04:00")));

        assertEquals(AppointmentErrorCode.SLOT_NOT_IN_SCHEDULE, ex.getCode());
        assertEquals("El profesional no atiende en ese horario.", ex.getMessage());
        nothingWasWritten();
    }

    @Test
    void bookingAnAlreadyTakenSlotIsAConflict() {

        when(appointmentRepository.existsByDoctorIdAndDateAndStartTime(
                DOCTOR_ID, LocalDate.of(2026, 9, 18), LocalTime.of(14, 0)))
                .thenReturn(true);

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.createAppointment(request("2026-09-18", "14:00", "15:00")));

        assertEquals(AppointmentErrorCode.SLOT_TAKEN, ex.getCode());
        assertEquals("Ese horario ya está reservado.", ex.getMessage());
        nothingWasWritten();
    }

    /**
     * The exists-check is not race-proof; under a real race the unique constraint fires on
     * flush, and that has to read as the same 409, not a 500.
     */
    @Test
    void losingTheRaceOnTheUniqueConstraintIsTheSameConflict() {

        when(appointmentRepository.existsByDoctorIdAndDateAndStartTime(anyLong(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_appointments_doctor_date_start"));

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class,
                () -> appointmentService.createAppointment(request("2026-09-18", "14:00", "15:00")));

        assertEquals(AppointmentErrorCode.SLOT_TAKEN, ex.getCode());
        verifyNoConfirmationEmail();
    }

    @Test
    void aFutureSlotInsideTheTemplateIsBooked() {

        when(appointmentRepository.existsByDoctorIdAndDateAndStartTime(anyLong(), any(), any()))
                .thenReturn(false);

        AppointmentDetailDTO dto =
                appointmentService.createAppointment(request("2026-09-18", "14:00", "15:00"));

        assertEquals(NEW_APPOINTMENT_ID, dto.getId());
        assertEquals("2026-09-18", dto.getDate());
        assertEquals("14:00", dto.getStartTime());
        assertEquals("PENDING", dto.getStatus());
        assertEquals(0, dto.getRescheduleCount());

        verify(appointmentRepository).saveAndFlush(any(Appointment.class));
        verify(userService).evictDoctorCaches(DOCTOR_ID);
        verify(emailService).sendAppointmentConfirmation(
                eq("juan@therapify.com"), eq("ana@therapify.com"),
                eq("Juan Perez"), eq("Ana Lopez"),
                eq("2026-09-18"), eq("14:00"), eq("15:00"));
    }

    // ---- helpers ----

    private AppointmentRequestDTO request(String date, String startTime, String endTime) {
        AppointmentRequestDTO dto = new AppointmentRequestDTO();
        dto.setDoctorId(DOCTOR_ID);
        dto.setDate(date);
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        return dto;
    }

    /** A rejected booking must not reach the database, the caches or the mailer. */
    private void nothingWasWritten() {
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(userService, never()).evictDoctorCaches(anyLong());
        verifyNoConfirmationEmail();
    }

    private void verifyNoConfirmationEmail() {
        verify(emailService, never()).sendAppointmentConfirmation(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
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
}
