package com.example.therapify.controller;
import com.example.therapify.dtos.ReviewDTOs.ReviewDetailDTO;
import com.example.therapify.dtos.ReviewDTOs.ReviewListDTO;
import com.example.therapify.dtos.ReviewDTOs.ReviewRequestDTO;
import com.example.therapify.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ------------------------------
    // POST: crear reseña
    // ------------------------------
    @PreAuthorize("hasRole('PACIENTE')")
    @PostMapping
    public ResponseEntity<ReviewDetailDTO> createReview(
            @Valid @RequestBody ReviewRequestDTO dto
    ) {
        ReviewDetailDTO saved = reviewService.createReview(dto);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    // ------------------------------
    // GET: reseñas de un doctor
    // ------------------------------
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<ReviewListDTO>> getReviewsByDoctor(
            @PathVariable Long doctorId
    ) {
        List<ReviewListDTO> reviews = reviewService.getReviewsByDoctor(doctorId);
        return ResponseEntity.ok(reviews);
    }

    // ------------------------------
    // GET: reseñas del paciente logueado
    // ------------------------------
    @PreAuthorize("hasRole('PACIENTE')")
    @GetMapping("/my")
    public ResponseEntity<List<ReviewListDTO>> getMyReviews() {
        List<ReviewListDTO> reviews = reviewService.getMyReviews();
        return ResponseEntity.ok(reviews);
    }

    // ------------------------------
    // DELETE: eliminar reseña (solo ADMIN)
    // ------------------------------
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteReview(
            @PathVariable Long id
    ) {
        return reviewService.deleteReview(id);
    }
}
