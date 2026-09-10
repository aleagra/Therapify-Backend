package com.example.therapify.repository;
import com.example.therapify.dtos.ReviewDTOs.DoctorRatingStats;
import com.example.therapify.model.Review;
import com.example.therapify.model.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByPatient(User patient);
    List<Review> findByDoctorId(Long doctorId);
    @Transactional
    void deleteByDoctorOrPatient(User doctor, User patient);

    @Query("SELECT new com.example.therapify.dtos.ReviewDTOs.DoctorRatingStats(" +
            "r.doctor.id, AVG(r.value), COUNT(r)) " +
            "FROM Review r " +
            "WHERE r.doctor.id IN :doctorIds " +
            "GROUP BY r.doctor.id")
    List<DoctorRatingStats> findRatingStatsByDoctorIds(@Param("doctorIds") List<Long> doctorIds);
}