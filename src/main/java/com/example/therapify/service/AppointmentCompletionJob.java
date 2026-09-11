package com.example.therapify.service;

import com.example.therapify.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Closes finished appointments server-side, once per hour, splitting them by whether there ever
 * was an agreement: CONFIRMED becomes COMPLETED (the professional accepted the slot, so the
 * session is assumed to have happened), while PENDING becomes EXPIRED (nobody ever agreed, and
 * the system has no way to claim otherwise).
 *
 * That split is a data-integrity fix, not cosmetics: COMPLETED is what ReviewService accepts as
 * proof of a past session, so completing PENDING rows let a patient book, never show up, and
 * then review a professional they never met.
 *
 * This used to be the frontend's job: on rendering /turnos it detected past appointments and
 * PATCHed each one to COMPLETED. That was wrong three ways — a patient gets 403 on a status
 * change (correctly), every open browser wrote to the database, and two evaluators with the
 * page open raced each other over the same rows. The transition depends only on the clock, so
 * it belongs here.
 *
 * Deliberately has no EmailService dependency: closing a turn is internal bookkeeping, not
 * something the patient should be mailed about. The repository does it as one bulk UPDATE, so
 * no entity is loaded and no lifecycle callback can fire either.
 *
 * Worth knowing, since the application runs with spring.main.lazy-initialization=true: a lazy
 * bean is never instantiated, which would leave its @Scheduled method unregistered and the job
 * silently dead. Boot covers this itself (ScheduledBeanLazyInitializationExcludeFilter excludes
 * beans carrying @Scheduled from lazy initialisation), so no extra annotation is needed here —
 * AppointmentCompletionSchedulingTest pins that down by booting with lazy init on and asserting
 * the cron is actually armed.
 */
@Component
public class AppointmentCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentCompletionJob.class);

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public AppointmentCompletionJob(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    /** Top of every hour. */
    @Scheduled(cron = "0 0 * * * *")
    public void completeFinishedAppointmentsScheduled() {
        runSafely("programada");
    }

    /**
     * One pass as soon as the application is up, so appointments that expired while the service
     * was down (or before this job existed) are normalised without waiting for the next hour.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void completeFinishedAppointmentsOnStartup() {
        runSafely("de arranque");
    }

    /**
     * Never lets a failure escape: on the scheduled path an exception would kill the recurring
     * task, and on the startup path it would abort application boot.
     */
    private void runSafely(String runLabel) {
        try {
            Outcome outcome = closeFinishedAppointments();
            log.info("Cierre automático de turnos ({}): {} CONFIRMED -> COMPLETED, "
                            + "{} PENDING -> EXPIRED.",
                    runLabel, outcome.completed(), outcome.expired());
        } catch (Exception e) {
            log.error("Falló el cierre automático de turnos ({}).", runLabel, e);
        }
    }

    /** How many rows each of the two transitions touched on a run. */
    record Outcome(int completed, int expired) {}

    /**
     * Package-private so tests can invoke the work itself and read the row counts, without
     * going through the swallow-everything wrapper.
     *
     * Two statements rather than one transaction: they address disjoint sets of rows (one reads
     * CONFIRMED, the other PENDING), and both are idempotent, so a failure between them leaves
     * nothing inconsistent — the next hourly run finishes the work.
     */
    Outcome closeFinishedAppointments() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime timeOfDay = now.toLocalTime();

        int completed = appointmentRepository.markConfirmedAsCompleted(today, timeOfDay);
        int expired = appointmentRepository.markPendingAsExpired(today, timeOfDay);

        return new Outcome(completed, expired);
    }
}
