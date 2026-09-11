package com.example.therapify.repository;

import com.example.therapify.dtos.AppointmentDTOs.BookedSlotProjection;
import com.example.therapify.enums.Status;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    boolean existsByDoctorIdAndDateAndStartTime(
            Long doctorId,
            LocalDate date,
            LocalTime startTime
    );
    /**
     * Same slot check as above but ignoring one appointment — the one being rescheduled, which
     * still occupies its old (doctor, date, startTime) row while we validate the new one.
     */
    boolean existsByDoctorIdAndDateAndStartTimeAndIdNot(
            Long doctorId,
            LocalDate date,
            LocalTime startTime,
            Long id
    );

    /**
     * SELECT ... FOR UPDATE on the appointment row, used by the reschedule flow.
     *
     * The unique constraint on (doctor_id, date, start_time) already stops two patients from
     * landing on the same new slot, but it cannot stop two concurrent reschedules of the *same*
     * appointment (both writes target one row, so there is no constraint to violate) from
     * double-incrementing rescheduleCount or slipping past the limit. This lock serialises them.
     *
     * No JOIN FETCH here on purpose: doctor/patient are EAGER @ManyToOne and get their own
     * selects, so FOR UPDATE stays on the appointments row and never locks users rows too.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    List<Appointment> findByDoctorIdAndDate(Long doctorId, LocalDate date);
    List<Appointment> findByDoctorIdOrPatientId(Long doctorId, Long patientId);
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
            "FROM Appointment a " +
            "WHERE a.patient.id = :patientId " +
            "AND a.doctor.id = :doctorId " +
            "AND a.status = :status")
    boolean existsByPatientIdAndDoctorIdAndStatus(
            @Param("patientId") Long patientId,
            @Param("doctorId") Long doctorId,
            @Param("status") Status status
    );
    @Transactional
    void deleteByDoctorOrPatient(User doctor, User patient);

    /**
     * Used by the demo-data reset: clears every appointment booked by the demo patient,
     * regardless of which doctor it was booked with.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Appointment a WHERE a.patient.id = :patientId")
    int deleteByPatientId(@Param("patientId") Long patientId);

    /**
     * Used by the demo-data reset: frees up the demo doctor's calendar for the next N days
     * so evaluators always find open slots, regardless of who booked them.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Appointment a WHERE a.doctor.id = :doctorId AND a.date BETWEEN :from AND :to")
    int deleteByDoctorIdAndDateBetween(
            @Param("doctorId") Long doctorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * Closes finished appointments that the professional had accepted: CONFIRMED -> COMPLETED.
     *
     * Only CONFIRMED. Sweeping PENDING in here too (which is what this query used to do) makes
     * the system assert that a session happened when nobody ever agreed to it, and COMPLETED is
     * what unlocks leaving a review — so a patient could book, never show up, wait for the hour
     * to pass and then review a professional they never met. Those rows go to EXPIRED instead,
     * via {@link #markPendingAsExpired}.
     *
     * Replaces the client-side sync the frontend used to do (it detected past turns on render
     * and PATCHed each one), which got a 403 for patients, had every open browser writing to
     * the DB, and made two simultaneous evaluators race over the same rows.
     *
     * The predicate is the portable spelling of `(date + end_time) < now()`: JPQL cannot add a
     * DATE and a TIME, and a native Postgres expression would not run on the H2 the tests use.
     * Split in two, it compares the same instant while staying index-friendly on `date` and
     * lets the caller pass the application Clock instead of the database's now(), which is what
     * makes the job testable at a fixed instant.
     *
     * A bulk UPDATE on purpose: no entities are loaded, so there is no lifecycle callback and
     * no place for an email to be triggered by this transition.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Appointment a SET a.status = com.example.therapify.enums.Status.COMPLETED " +
            "WHERE a.status = com.example.therapify.enums.Status.CONFIRMED " +
            "AND (a.date < :today OR (a.date = :today AND a.endTime < :timeOfDay))")
    int markConfirmedAsCompleted(
            @Param("today") LocalDate today,
            @Param("timeOfDay") LocalTime timeOfDay
    );

    /**
     * Closes finished appointments the professional never accepted: PENDING -> EXPIRED.
     *
     * The counterpart of {@link #markConfirmedAsCompleted}, same temporal predicate and same
     * bulk-UPDATE properties. EXPIRED records that the slot went by without an agreement, which
     * is all the system actually knows, and deliberately does not unlock a review.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Appointment a SET a.status = com.example.therapify.enums.Status.EXPIRED " +
            "WHERE a.status = com.example.therapify.enums.Status.PENDING " +
            "AND (a.date < :today OR (a.date = :today AND a.endTime < :timeOfDay))")
    int markPendingAsExpired(
            @Param("today") LocalDate today,
            @Param("timeOfDay") LocalTime timeOfDay
    );

    /**
     * Used by the demo-data reset: clears the graveyard of past appointments the shared demo
     * accounts accumulate, covering both roles (demo account as patient and as professional).
     * Scoped to the ids it receives, so no other user's history is ever touched.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Appointment a " +
            "WHERE a.date < :today AND (a.doctor.id IN :userIds OR a.patient.id IN :userIds)")
    int deletePastAppointmentsForUsers(
            @Param("userIds") Collection<Long> userIds,
            @Param("today") LocalDate today
    );

    /**
     * Used by the demo-data reset: puts the reschedule counter back to 0 on any appointment of
     * the demo accounts that survives the deletes above (the demo doctor's rows booked by real
     * patients outside the 7-day window), so an evaluator always starts with both reschedules
     * available.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Appointment a SET a.rescheduleCount = 0 " +
            "WHERE a.doctor.id = :doctorId OR a.patient.id = :patientId")
    int resetRescheduleCountForDemoAccounts(
            @Param("doctorId") Long doctorId,
            @Param("patientId") Long patientId
    );

    /**
     * Only doctorId/date/startTime are selected (no entity/join) to batch-compute
     * available slots for many doctors at once without loading full Appointment/User graphs.
     */
    @Query("SELECT a.doctor.id AS doctorId, a.date AS date, a.startTime AS startTime " +
            "FROM Appointment a " +
            "WHERE a.doctor.id IN :doctorIds " +
            "AND a.date BETWEEN :from AND :to")
    List<BookedSlotProjection> findBookedSlots(
            @Param("doctorIds") List<Long> doctorIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * "Mis turnos": scopeUserId null means no restriction (admin sees all).
     * fromDate/toDate/status are optional filters (null = ignored).
     * JOIN FETCH avoids per-row N+1 lookups of doctor/patient (both are single-valued
     * associations, so pagination + fetch join here is safe).
     */
    @Query("SELECT a FROM Appointment a " +
            "JOIN FETCH a.doctor JOIN FETCH a.patient " +
            "WHERE (:scopeUserId IS NULL OR a.doctor.id = :scopeUserId OR a.patient.id = :scopeUserId) " +
            "AND (:fromDate IS NULL OR a.date >= :fromDate) " +
            "AND (:toDate IS NULL OR a.date < :toDate) " +
            "AND (:status IS NULL OR a.status = :status)")
    Page<Appointment> findMyAppointments(
            @Param("scopeUserId") Long scopeUserId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") Status status,
            Pageable pageable
    );
}
