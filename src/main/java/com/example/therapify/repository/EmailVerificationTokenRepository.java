package com.example.therapify.repository;

import com.example.therapify.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    EmailVerificationToken findByToken(String token);
}