package com.example.therapify.service;

import com.example.therapify.dtos.ReviewDTOs.ReviewRequestDTO;
import com.example.therapify.enums.Status;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.Review;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import com.example.therapify.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The review gate: only a professional the patient actually had a session with can be reviewed.
 *
 * The exploit this pins shut: book with anyone, never show up, let the professional never
 * confirm, wait for the hour to pass. While the job completed PENDING rows too, that produced a
 * COMPLETED appointment and the gate opened, so a 1-star review could be left by someone who
 * was never seen. Now those rows become EXPIRED, which the gate does not accept — asserted
 * against a real database in
 * {@link com.example.therapify.repository.AppointmentCompletionQueryTest}, and here at the
 * service boundary.
 */
class ReviewServiceEligibilityTest {

    private static final Long DOCTOR_ID = 10L;
    private static final Long PATIENT_ID = 20L;

    private AppointmentRepository appointmentRepository;
    private ReviewRepository reviewRepository;
    private UserService userService;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        userService = mock(UserService.class);

        reviewService = new ReviewService();
        // ReviewService uses field injection, so the collaborators go in the same way rather
        // than reshaping production code for the test.
        ReflectionTestUtils.setField(reviewService, "appointmentRepository", appointmentRepository);
        ReflectionTestUtils.setField(reviewService, "reviewRepository", reviewRepository);
        ReflectionTestUtils.setField(reviewService, "userService", userService);

        User patient = user(PATIENT_ID, "juan@therapify.com", UserType.PACIENTE);
        User doctor = user(DOCTOR_ID, "ana@therapify.com", UserType.DOCTOR);

        when(userService.getAuthenticatedUser()).thenReturn(patient);
        when(userService.findEntityById(DOCTOR_ID)).thenReturn(doctor);
        // Stands in for @PrePersist/@GeneratedValue, which never run against a mocked repository.
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            ReflectionTestUtils.setField(saved, "date", LocalDateTime.now());
            return saved;
        });
    }

    @Test
    void anExpiredAppointmentDoesNotUnlockAReview() {

        // What the gate actually asks: "is there a COMPLETED appointment between these two?".
        // With the turn expired instead of completed, the answer is no.
        when(appointmentRepository.existsByPatientIdAndDoctorIdAndStatus(
                PATIENT_ID, DOCTOR_ID, Status.COMPLETED))
                .thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reviewService.createReview(request()));

        assertEquals("Solo podés dejar reseñas a doctores con los que tuviste un turno.",
                ex.getMessage());
        verify(reviewRepository, never()).save(any(Review.class));

        // And it never widens the query to a status that would let EXPIRED through.
        verify(appointmentRepository).existsByPatientIdAndDoctorIdAndStatus(
                PATIENT_ID, DOCTOR_ID, Status.COMPLETED);
    }

    @Test
    void aCompletedAppointmentDoesUnlockAReview() {

        when(appointmentRepository.existsByPatientIdAndDoctorIdAndStatus(
                PATIENT_ID, DOCTOR_ID, Status.COMPLETED))
                .thenReturn(true);

        reviewService.createReview(request());

        verify(reviewRepository).save(any(Review.class));
        verify(userService).evictDoctorCaches(DOCTOR_ID);
    }

    private ReviewRequestDTO request() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setDoctorId(DOCTOR_ID);
        dto.setValue(1);
        dto.setComment("Reseña de prueba");
        return dto;
    }

    private User user(Long id, String email, UserType type) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail(email);
        u.setFirstName("Nombre");
        u.setLastName("Apellido");
        u.setUserType(type);
        u.setEnabled(true);
        return u;
    }
}
