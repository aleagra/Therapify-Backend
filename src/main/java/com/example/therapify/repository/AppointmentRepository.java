package com.example.therapify.repository;

import com.example.therapify.enums.Status;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
    boolean existsByPatientAndDoctorAndStatus(
            User patient,
            User doctor,
            Status status
    );
}
