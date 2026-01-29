package com.example.therapify.repository;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.Review;
import com.example.therapify.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByPatient(User patient);
    List<Review> findByPatientId(Long patientId);
    List<Review> findByDoctor(User doctor);
    List<Review> findByDoctorId(Long doctorId);
}