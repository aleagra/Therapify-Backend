package com.example.therapify.repository;
import com.example.therapify.model.Review;
import com.example.therapify.model.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByPatient(User patient);
    List<Review> findByDoctorId(Long doctorId);
    @Transactional
    void deleteByDoctorOrPatient(User doctor, User patient);

}