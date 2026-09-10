package com.example.therapify.dtos.ReviewDTOs;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewRequestDTOValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void commentIsOptional() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setDoctorId(1L);
        dto.setValue(5);
        dto.setComment(null);

        Set<ConstraintViolation<ReviewRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "no debería exigir comentario: " + violations);
    }

    @Test
    void commentOverLimitIsRejected() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setDoctorId(1L);
        dto.setValue(5);
        dto.setComment("a".repeat(256));

        Set<ConstraintViolation<ReviewRequestDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
    }

    @Test
    void ratingOutOfRangeIsRejected() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setDoctorId(1L);
        dto.setValue(6);

        Set<ConstraintViolation<ReviewRequestDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
    }
}
