package com.example.therapify.service;

import com.example.therapify.config.DemoGuard;
import com.example.therapify.enums.Specialty;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import com.example.therapify.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Manages the two fixed demo accounts (a doctor and a patient) used by recruiters/evaluators
 * to try the booking flow end-to-end, and keeps their test data isolated and self-healing.
 *
 * Note on "available slots": this codebase never persists an Appointment row for an open
 * slot (Appointment.patient is non-nullable and Status has no AVAILABLE value) — availability
 * is computed on the fly from a doctor's weekly recurring template (User.availability) minus
 * whatever is already booked (see AvailabilityCalculator). So "regenerating available slots"
 * for the demo doctor means: clear its booked appointments for the window, and make sure its
 * weekly template still has slots that fall inside that window.
 */
@Service
public class DemoService {

    private static final int RESET_WINDOW_DAYS = 7;

    // MONDAY/WEDNESDAY/FRIDAY with a handful of slots each: any 7-day window contains at
    // least one of each, landing the "available slots" count in the 5-10 range asked for.
    private static final Map<String, List<String>> DEFAULT_DOCTOR_AVAILABILITY = Map.of(
            "MONDAY", List.of("09:00", "11:00"),
            "WEDNESDAY", List.of("09:00", "11:00", "15:00"),
            "FRIDAY", List.of("09:00", "11:00", "15:00")
    );

    private static final Map<String, Boolean> DEFAULT_DOCTOR_SCHEDULE = Map.of(
            "MONDAY", true,
            "WEDNESDAY", true,
            "FRIDAY", true
    );

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final DemoGuard demoGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public DemoService(
            UserRepository userRepository,
            AppointmentRepository appointmentRepository,
            PasswordEncoder passwordEncoder,
            UserService userService,
            DemoGuard demoGuard
    ) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.demoGuard = demoGuard;
    }

    /**
     * Used by DemoController's @PreAuthorize to gate the reset endpoint. Delegates to
     * {@link DemoGuard}, the single place that knows which emails are demo accounts.
     */
    public boolean isDemoAccount(String email) {
        return demoGuard.isDemoAccount(email);
    }

    /**
     * Idempotent seed: creates the demo doctor/patient if they don't exist yet, and backfills
     * the doctor's availability template if it's missing. Safe to call on every startup.
     */
    @Transactional
    public void ensureDemoAccountsSeeded() {
        User demoDoctor = userRepository.findByEmail(demoGuard.doctorEmail()).orElseGet(this::createDemoDoctor);
        userRepository.findByEmail(demoGuard.patientEmail()).orElseGet(this::createDemoPatient);

        if (demoDoctor.getAvailability() == null || demoDoctor.getAvailability().isBlank()) {
            applyDefaultAvailability(demoDoctor);
            userRepository.save(demoDoctor);
        }
    }

    /**
     * Resets the demo sandbox: wipes appointments belonging to the two demo accounts only
     * (never touches any other doctor's or patient's data, since every query below is scoped
     * to demoDoctor.getId()/demoPatient.getId()) and restores the doctor's weekly template.
     */
    @Transactional
    public Map<String, String> resetDemoData() {
        User demoDoctor = userRepository.findByEmail(demoGuard.doctorEmail())
                .orElseThrow(() -> new IllegalStateException("La cuenta demo del terapeuta no existe"));
        User demoPatient = userRepository.findByEmail(demoGuard.patientEmail())
                .orElseThrow(() -> new IllegalStateException("La cuenta demo del paciente no existe"));

        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(RESET_WINDOW_DAYS);

        // 1. Elimina cualquier reserva del paciente demo, sin importar con qué doctor.
        appointmentRepository.deleteByPatientId(demoPatient.getId());

        // 2. Libera la agenda del terapeuta demo en los próximos 7 días.
        appointmentRepository.deleteByDoctorIdAndDateBetween(demoDoctor.getId(), today, windowEnd);

        // 3. Restablece la plantilla semanal de disponibilidad por si un evaluador la editó.
        applyDefaultAvailability(demoDoctor);
        userRepository.save(demoDoctor);

        userService.evictDoctorCaches(demoDoctor.getId());

        return Map.of("message", "Datos de prueba restablecidos correctamente");
    }

    private void applyDefaultAvailability(User demoDoctor) {
        try {
            demoDoctor.setAvailability(objectMapper.writeValueAsString(DEFAULT_DOCTOR_AVAILABILITY));
            demoDoctor.setSchedule(objectMapper.writeValueAsString(DEFAULT_DOCTOR_SCHEDULE));
        } catch (Exception e) {
            throw new RuntimeException("Error serializando la disponibilidad demo", e);
        }
    }

    private User createDemoDoctor() {
        User doctor = new User();
        doctor.setFirstName("Terapeuta");
        doctor.setLastName("Demo");
        doctor.setEmail(demoGuard.doctorEmail());
        doctor.setPassword(passwordEncoder.encode("Demo1234"));
        doctor.setUserType(UserType.DOCTOR);
        doctor.setEnabled(true);
        doctor.setSpecialty(Specialty.PSICOLOGIA_CLINICA);
        doctor.setConsultationPrice(1000.0);
        doctor.setDescription("Cuenta demo para evaluación técnica de Therapify.");
        applyDefaultAvailability(doctor);
        return userRepository.save(doctor);
    }

    private User createDemoPatient() {
        User patient = new User();
        patient.setFirstName("Paciente");
        patient.setLastName("Demo");
        patient.setEmail(demoGuard.patientEmail());
        patient.setPassword(passwordEncoder.encode("Demo1234"));
        patient.setUserType(UserType.PACIENTE);
        patient.setEnabled(true);
        return userRepository.save(patient);
    }
}
