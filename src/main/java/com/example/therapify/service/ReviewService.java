package com.example.therapify.service;
import com.example.therapify.dtos.ReviewDTOs.ReviewDetailDTO;
import com.example.therapify.dtos.ReviewDTOs.ReviewListDTO;
import com.example.therapify.dtos.ReviewDTOs.ReviewRequestDTO;
import com.example.therapify.exception.AccessDeniedException;
import com.example.therapify.exception.ReviewNotFoundException;
import com.example.therapify.model.Review;
import com.example.therapify.model.User;
import com.example.therapify.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserService userService;

    public ReviewDetailDTO createReview(ReviewRequestDTO dto) {

        User patient = userService.getAuthenticatedUser(); // paciente logueado
        User doctor = userService.findEntityById(dto.getDoctorId());
        Review review = new Review();
        review.setComment(dto.getComment());
        review.setValue(dto.getValue());
        review.setPatient(patient);
        review.setDoctor(doctor);

        reviewRepository.save(review);

        return toDetailDTO(review);
    }

    // -----------------------------------------------
    // GET: Listar reseñas de un doctor
    // -----------------------------------------------
    public List<ReviewListDTO> getReviewsByDoctor(Long doctorId) {
        return reviewRepository.findByDoctorId(doctorId)
                .stream()
                .map(this::toListDTO)
                .toList();
    }

    // -----------------------------------------------
    // GET: Reseñas creadas por el paciente autenticado
    // -----------------------------------------------
    public List<ReviewListDTO> getMyReviews() {
        User patient = userService.getAuthenticatedUser();

        return reviewRepository.findByPatient(patient)
                .stream()
                .map(this::toListDTO)
                .toList();
    }

    // -----------------------------------------------
    // DELETE: Solo el dueño o el admin puede borrar
    // -----------------------------------------------
    public ResponseEntity<Map<String, String>> deleteReview(Long id) {
        User auth = userService.getAuthenticatedUser();
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ReviewNotFoundException("Review not found: " + id));
        boolean isAdmin = auth.getUserType().name().equals("ADMIN");
        if (!isAdmin) {
            throw new AccessDeniedException("No podés eliminar esta reseña.");
        }
        reviewRepository.delete(review);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Review eliminada correctamente.");

        return ResponseEntity.ok(response);
    }
    // -----------------------------------------------
    // --------- DTO MAPPERS -------------------------
    // -----------------------------------------------

    public ReviewDetailDTO toDetailDTO(Review review) {
        ReviewDetailDTO dto = new ReviewDetailDTO();

        dto.setId(review.getId());
        dto.setComment(review.getComment());
        dto.setValue(review.getValue());
        dto.setDate(review.getDate().toLocalDate());

        if (review.getPatient() != null) {
            dto.setPatientId(review.getPatient().getId());
            dto.setPatientName(review.getPatient().getFirstName());
        }

        if (review.getDoctor() != null) {
            dto.setDoctorId(review.getDoctor().getId());
            dto.setDoctorName(review.getDoctor().getFirstName());
        }

        return dto;
    }


    private ReviewListDTO toListDTO(Review review) {
        ReviewListDTO dto = new ReviewListDTO();

        dto.setId(review.getId());
        dto.setComment(review.getComment());
        dto.setValue(review.getValue());
        dto.setDate(review.getDate().toLocalDate());
        dto.setPatientName(review.getPatient().getFirstName());

        return dto;
    }
}
