package com.example.therapify.repository;

import com.example.therapify.enums.Status;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two bulk UPDATEs behind the completion job, against a real (in-memory) database.
 *
 * This is the test that matters for the rules "only finished appointments are touched" and
 * "only what was confirmed gets completed": the job unit test can only prove which parameters
 * were passed, while the risk lives in the predicates — an off-by-one on the boundary would
 * silently close turns that have not happened yet, and a status mix-up would hand out review
 * rights for sessions that never took place.
 *
 * Dates are built relative to LocalDate.now() so the test does not rot, and the cutoff time is
 * passed explicitly (09:00), which is exactly how the job feeds it the application clock.
 */
@DataJpaTest
@ActiveProfiles("test")
class AppointmentCompletionQueryTest {

    /**
     * The application is annotated @EnableCaching, but the JPA test slice does not autoconfigure
     * a CacheManager, so the context would fail to start without one. A no-op is right here:
     * nothing in this test reads through a cache.
     */
    @TestConfiguration
    static class NoCacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new NoOpCacheManager();
        }
    }

    private static final LocalTime CUTOFF = LocalTime.of(9, 0);

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private LocalDate today;
    private User doctor;
    private User patient;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        doctor = persistUser("doc@therapify.com", "Ana", "Lopez", UserType.DOCTOR);
        patient = persistUser("pac@therapify.com", "Juan", "Perez", UserType.PACIENTE);
    }

    /**
     * The whole point of the split: what the professional accepted is completed, what they never
     * accepted only expires. Completing a PENDING row would assert a session that nobody agreed
     * to, and COMPLETED is what unlocks reviews.
     */
    @Test
    void itCompletesWhatWasConfirmedAndOnlyExpiresWhatWasNot() {

        Long confirmedYesterday = persistAppointment(
                today.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0), Status.CONFIRMED);

        Long pendingYesterday = persistAppointment(
                today.minusDays(1), LocalTime.of(14, 0), LocalTime.of(15, 0), Status.PENDING);

        Long confirmedEarlierToday = persistAppointment(
                today, LocalTime.of(7, 0), LocalTime.of(8, 0), Status.CONFIRMED);

        Long pendingEarlierToday = persistAppointment(
                today, LocalTime.of(5, 0), LocalTime.of(6, 0), Status.PENDING);

        // Boundary: still running right now (started 09:00, ends 10:00) — must stay open.
        Long confirmedStillRunning = persistAppointment(
                today, LocalTime.of(9, 0), LocalTime.of(10, 0), Status.CONFIRMED);

        Long pendingStillRunning = persistAppointment(
                today, LocalTime.of(9, 30), LocalTime.of(10, 30), Status.PENDING);

        Long confirmedTomorrow = persistAppointment(
                today.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0), Status.CONFIRMED);

        Long pendingTomorrow = persistAppointment(
                today.plusDays(1), LocalTime.of(11, 0), LocalTime.of(12, 0), Status.PENDING);

        Long alreadyCompleted = persistAppointment(
                today.minusDays(1), LocalTime.of(12, 0), LocalTime.of(13, 0), Status.COMPLETED);

        Long alreadyExpired = persistAppointment(
                today.minusDays(1), LocalTime.of(16, 0), LocalTime.of(17, 0), Status.EXPIRED);

        assertEquals(2, appointmentRepository.markConfirmedAsCompleted(today, CUTOFF));
        assertEquals(2, appointmentRepository.markPendingAsExpired(today, CUTOFF));

        // Finished, and there was an agreement.
        assertEquals(Status.COMPLETED, statusOf(confirmedYesterday));
        assertEquals(Status.COMPLETED, statusOf(confirmedEarlierToday));

        // Finished, but never accepted: expired, NOT completed.
        assertEquals(Status.EXPIRED, statusOf(pendingYesterday));
        assertEquals(Status.EXPIRED, statusOf(pendingEarlierToday));

        // Not finished yet: untouched in both states.
        assertEquals(Status.CONFIRMED, statusOf(confirmedStillRunning));
        assertEquals(Status.PENDING, statusOf(pendingStillRunning));
        assertEquals(Status.CONFIRMED, statusOf(confirmedTomorrow));
        assertEquals(Status.PENDING, statusOf(pendingTomorrow));

        // Terminal states are left alone.
        assertEquals(Status.COMPLETED, statusOf(alreadyCompleted));
        assertEquals(Status.EXPIRED, statusOf(alreadyExpired));
    }

    @Test
    void itIsIdempotentSoTheHourlyRunStaysQuiet() {

        persistAppointment(today.minusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0), Status.CONFIRMED);
        persistAppointment(today.minusDays(2), LocalTime.of(14, 0), LocalTime.of(15, 0), Status.PENDING);

        assertEquals(1, appointmentRepository.markConfirmedAsCompleted(today, CUTOFF));
        assertEquals(1, appointmentRepository.markPendingAsExpired(today, CUTOFF));

        assertEquals(0, appointmentRepository.markConfirmedAsCompleted(today, CUTOFF));
        assertEquals(0, appointmentRepository.markPendingAsExpired(today, CUTOFF));
    }

    /**
     * The review gate reads exactly this query. An EXPIRED appointment must not satisfy it, or
     * the hole this change closes reopens: book, never show up, wait an hour, leave a review.
     */
    @Test
    void anExpiredAppointmentDoesNotCountAsAPastSessionForReviews() {

        persistAppointment(today.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0), Status.PENDING);
        appointmentRepository.markPendingAsExpired(today, CUTOFF);

        assertFalse(appointmentRepository.existsByPatientIdAndDoctorIdAndStatus(
                        patient.getId(), doctor.getId(), Status.COMPLETED),
                "un turno EXPIRED no debe habilitar reseña");

        // Control: the same pair with a genuinely completed session does satisfy the gate.
        persistAppointment(today.minusDays(1), LocalTime.of(15, 0), LocalTime.of(16, 0), Status.CONFIRMED);
        appointmentRepository.markConfirmedAsCompleted(today, CUTOFF);

        assertTrue(appointmentRepository.existsByPatientIdAndDoctorIdAndStatus(
                patient.getId(), doctor.getId(), Status.COMPLETED));
    }

    // ---- helpers ----

    private Status statusOf(Long id) {
        return entityManager.find(Appointment.class, id).getStatus();
    }

    private Long persistAppointment(LocalDate date, LocalTime start, LocalTime end, Status status) {
        Appointment ap = new Appointment();
        ap.setDoctor(doctor);
        ap.setPatient(patient);
        ap.setDate(date);
        ap.setStartTime(start);
        ap.setEndTime(end);
        ap.setStatus(status);
        return entityManager.persistAndGetId(ap, Long.class);
    }

    private User persistUser(String email, String firstName, String lastName, UserType type) {
        User u = new User();
        u.setEmail(email);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setPassword("irrelevante");
        u.setUserType(type);
        u.setEnabled(true);
        return entityManager.persistAndFlush(u);
    }
}
