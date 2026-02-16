package com.example.therapify.repository;

import com.example.therapify.enums.Status;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import jakarta.transaction.Transactional;
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
}
