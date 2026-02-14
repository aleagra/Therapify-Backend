package com.example.therapify.service;

import com.example.therapify.dtos.ReviewDTOs.ReviewDetailDTO;
import com.example.therapify.dtos.ReviewDTOs.ReviewListDTO;
import com.example.therapify.dtos.ReviewDTOs.ReviewRequestDTO;
import com.example.therapify.enums.Status;
import com.example.therapify.enums.UserType;
import com.example.therapify.exception.AccessDeniedException;
import com.example.therapify.exception.ReviewNotFoundException;
import com.example.therapify.model.Review;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import com.example.therapify.repository.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    public ReviewDetailDTO createReview(ReviewRequestDTO dto) {

        User patient = userService.getAuthenticatedUser();
        User doctor = userService.findEntityById(dto.getDoctorId());

        if (patient.getId().equals(doctor.getId())) {
            throw new IllegalArgumentException(
                    "No podés dejar una reseña sobre vos mismo."
            );
        }

        boolean hadAppointment =
                appointmentRepository.existsByPatientAndDoctorAndStatus(
                        patient,
                        doctor,
                        Status.COMPLETED
                );

        if (!hadAppointment) {
            throw new IllegalArgumentException(
                    "Solo podés dejar reseñas a doctores con los que tuviste un turno."
            );
        }

        Review review = new Review();
        review.setComment(dto.getComment());
        review.setValue(dto.getValue());
        review.setPatient(patient);
        review.setDoctor(doctor);
        reviewRepository.save(review);

        return toDetailDTO(review);
    }

    public ReviewDetailDTO updateReview(Long id, ReviewRequestDTO dto) {

        User auth = userService.getAuthenticatedUser();

        Review review = reviewRepository.findById(id)
                .orElseThrow(() ->
                        new ReviewNotFoundException("Review no encontrada")
                );

        if (!review.getPatient().getId().equals(auth.getId())) {
            throw new AccessDeniedException(
                    "No podés editar reseñas de otros usuarios"
            );
        }

        review.setComment(dto.getComment());
        review.setValue(dto.getValue());

        reviewRepository.save(review);

        return toDetailDTO(review);
    }

    public List<ReviewListDTO> getReviewsByDoctor(Long doctorId) {

        return reviewRepository
                .findByDoctorId(doctorId)
                .stream()
                .map(this::toListDTO)
                .toList();
    }

    public List<ReviewListDTO> getMyReviews() {

        User patient = userService.getAuthenticatedUser();

        return reviewRepository
                .findByPatient(patient)
                .stream()
                .map(this::toListDTO)
                .toList();
    }
    public List<ReviewListDTO> getReviewsByUser(Long userId) {
        User user = userService.findEntityById(userId);
        return reviewRepository.findByPatient(user)
                .stream()
                .map(this::toListDTO)
                .toList();
    }

    public ResponseEntity<Map<String, String>> deleteReview(Long id) {

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Review no encontrada"));

        User currentUser = userService.getAuthenticatedUser();

        boolean isAdmin =
                currentUser.getUserType().equals(UserType.ADMIN);

        boolean isOwner =
                review.getPatient().getId()
                        .equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("No tenés permiso para eliminar esta review");
        }

        reviewRepository.delete(review);

        return ResponseEntity.ok(
                Map.of("message", "Review eliminada correctamente")
        );
    }

    private ReviewDetailDTO toDetailDTO(Review review) {

        ReviewDetailDTO dto = new ReviewDetailDTO();

        dto.setId(review.getId());
        dto.setComment(review.getComment());
        dto.setValue(review.getValue());
        dto.setDate(review.getDate().toLocalDate());
        dto.setPatientId(review.getPatient().getId());
        dto.setPatientName(review.getPatient().getFirstName());
        dto.setPatientLastName(review.getPatient().getLastName());
        dto.setDoctorId(review.getDoctor().getId());
        dto.setDoctorName(review.getDoctor().getFirstName());

        return dto;
    }

    private ReviewListDTO toListDTO(Review review) {

        ReviewListDTO dto = new ReviewListDTO();

        dto.setId(review.getId());
        dto.setComment(review.getComment());
        dto.setValue(review.getValue());
        dto.setDate(review.getDate().toLocalDate());
        dto.setPatientId(review.getPatient().getId());
        dto.setPatientName(review.getPatient().getFirstName());
        dto.setPatientLastName(review.getPatient().getLastName());

        return dto;
    }
}
