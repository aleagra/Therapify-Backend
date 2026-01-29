package com.example.therapify.repository;

import com.example.therapify.model.Appointment;
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
}
