package com.example.therapify.dtos.UserDTOs;
import com.example.therapify.enums.Specialty;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record UserDetailDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String userType,
        String companyName,
        String gender,
        String address,
        Double latitude,
        Double longitude,
        Double distanceKm,
        String description,
        String specialty,
        Map<String, Boolean> schedule,
        Map<String, List<String>> availability,
        Double consultationPrice,
        Double averageRating,
        Integer totalReviews,
        Integer availableSlotsCount,
        List<LocalDate> nextAvailableDates,

        /**
         * Derived from the configured demo emails (see DemoGuard), never persisted, so the
         * frontend stops comparing the email against hardcoded strings. Explicit @JsonProperty
         * because Jackson would otherwise strip the "is" prefix and serialize this as "demo".
         */
        @JsonProperty("isDemo") boolean isDemo
) {}
