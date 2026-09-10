package com.example.therapify.repository;

import com.example.therapify.dtos.AppointmentDTOs.BookedSlotProjection;
import com.example.therapify.enums.Status;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    boolean existsByDoctorIdAndDateAndStartTime(
            Long doctorId,
            LocalDate date,
            LocalTime startTime
    );
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
