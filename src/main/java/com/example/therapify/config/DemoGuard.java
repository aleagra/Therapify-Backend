package com.example.therapify.config;

import com.example.therapify.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "is this a demo account?".
 *
 * Reads the same configuration keys (therapify.demo.*) that seed the accounts in
 * {@link com.example.therapify.service.DemoService}, so the emails are never hardcoded twice.
 * Deliberately dependency-free: DemoService already depends on UserService, so injecting
 * DemoService into UserService would create a constructor cycle. This tiny component can be
 * injected anywhere without that risk.
 */
@Component
public class DemoGuard {

    private final String doctorEmail;
    private final String patientEmail;

    public DemoGuard(
            @Value("${therapify.demo.doctor-email:demo.terapeuta@therapify.com}") String doctorEmail,
            @Value("${therapify.demo.patient-email:demo.paciente@therapify.com}") String patientEmail
    ) {
        this.doctorEmail = doctorEmail;
        this.patientEmail = patientEmail;
    }

    public String doctorEmail() {
        return doctorEmail;
    }

    public String patientEmail() {
        return patientEmail;
    }

    public boolean isDemoAccount(String email) {
        return email != null
                && (doctorEmail.equalsIgnoreCase(email) || patientEmail.equalsIgnoreCase(email));
    }

    public boolean isDemoAccount(User user) {
        return user != null && isDemoAccount(user.getEmail());
    }
}
