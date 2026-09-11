package com.example.therapify.service;

import com.example.therapify.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The job's own behaviour: what it asks the database for, and that a failure never escapes.
 * Which rows the statement actually touches is covered against a real database in
 * {@link com.example.therapify.repository.AppointmentCompletionQueryTest}.
 */
class AppointmentCompletionJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 9, 30);

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final AppointmentCompletionJob job = new AppointmentCompletionJob(
            appointmentRepository,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void itAsksForTheTwoBulkUpdatesSplitAtTheCurrentInstant() {

        when(appointmentRepository.markConfirmedAsCompleted(any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(3);
        when(appointmentRepository.markPendingAsExpired(any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(2);

        AppointmentCompletionJob.Outcome outcome = job.closeFinishedAppointments();

        assertEquals(3, outcome.completed());
        assertEquals(2, outcome.expired());

        // Two statements, both addressed at "now" as the application sees it, and nothing else.
        verify(appointmentRepository).markConfirmedAsCompleted(
                eq(LocalDate.of(2026, 9, 11)), eq(LocalTime.of(9, 30)));
        verify(appointmentRepository).markPendingAsExpired(
                eq(LocalDate.of(2026, 9, 11)), eq(LocalTime.of(9, 30)));
        verifyNoMoreInteractions(appointmentRepository);
    }

    @Test
    void aDatabaseFailureNeitherBreaksBootNorKillsTheSchedule() {

        when(appointmentRepository.markConfirmedAsCompleted(any(LocalDate.class), any(LocalTime.class)))
                .thenThrow(new RuntimeException("la base no responde"));

        // Escaping here would abort application startup on one path and cancel the recurring
        // task on the other.
        assertDoesNotThrow(job::completeFinishedAppointmentsOnStartup);
        assertDoesNotThrow(job::completeFinishedAppointmentsScheduled);
    }

    /**
     * Completing a turn is internal bookkeeping: the patient must not be mailed about it. The
     * structural guarantee is that the job has no mailer to call in the first place, so this
     * can't regress by someone adding a send inside it.
     */
    @Test
    void theJobHasNoMailerToCall() {
        boolean hasMailer = Arrays.stream(AppointmentCompletionJob.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(EmailService.class::isAssignableFrom);

        assertFalse(hasMailer, "el job no debe depender de EmailService");
    }

    /** Guards the hourly cadence against an accidental edit of the cron expression. */
    @Test
    void itIsScheduledEveryHour() throws Exception {
        Method scheduled = AppointmentCompletionJob.class
                .getDeclaredMethod("completeFinishedAppointmentsScheduled");

        Scheduled annotation = scheduled.getAnnotation(Scheduled.class);

        assertTrue(annotation != null, "el metodo debe estar anotado con @Scheduled");
        assertEquals("0 0 * * * *", annotation.cron());
    }
}
