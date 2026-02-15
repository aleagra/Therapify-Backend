package com.example.therapify.dtos.UserDTOs;
import com.example.therapify.enums.Specialty;

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
        Double consultationPrice
) {}
