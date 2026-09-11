package com.example.therapify.service;

import com.example.therapify.config.DemoGuard;
import com.example.therapify.config.JwtService;
import com.example.therapify.dtos.UserDTOs.UserDetailDTO;
import com.example.therapify.dtos.UserDTOs.UserRequestDTO;
import com.example.therapify.enums.UserType;
import com.example.therapify.exception.AccessDeniedException;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import com.example.therapify.repository.EmailVerificationTokenRepository;
import com.example.therapify.repository.PasswordResetTokenRepository;
import com.example.therapify.repository.ReviewRepository;
import com.example.therapify.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the demo-account hardening on PUT /usuarios: a demo account must come back 200 with
 * its identity fields untouched, while a regular account keeps updating them normally.
 */
class UserServiceDemoProtectionTest {

    private static final String DEMO_DOCTOR_EMAIL = "demo.terapeuta@therapify.com";
    private static final String DEMO_PATIENT_EMAIL = "demo.paciente@therapify.com";

    private static final String ORIGINAL_PASSWORD_HASH = "hash-de-Demo1234";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private DemoGuard demoGuard;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        demoGuard = new DemoGuard(DEMO_DOCTOR_EMAIL, DEMO_PATIENT_EMAIL);

        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash-de-" + inv.getArgument(0));
        when(jwtService.create(anyString(), anyString())).thenReturn("token-nuevo");
        // Saving is a no-op here: assertions run against the mutated entity itself.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService = new UserService(
                userRepository,
                passwordEncoder,
                jwtService,
                mock(PasswordResetTokenRepository.class),
                mock(GeocodingService.class),
                mock(EmailService.class),
                mock(EmailVerificationTokenRepository.class),
                mock(AppointmentRepository.class),
                mock(ReviewRepository.class),
                mock(CacheManager.class),
                demoGuard
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void demoAccountKeepsPasswordAndFirstNameOnUpdate() {

        User demo = existingPatient(1L, DEMO_PATIENT_EMAIL, "Paciente", "Demo");
        authenticateAs(demo);

        UserRequestDTO req = new UserRequestDTO();
        req.setFirstName("Hackeado");
        req.setLastName("Tambien");
        req.setEmail("otro@correo.com");
        req.setPassword("NuevaClave123");
        req.setAddress("Av. Siempre Viva 742");   // editable, sirve de control
        req.setGender("otro");

        ResponseEntity<Map<String, Object>> response = userService.modificarMiUsuario(req);

        // Se ignora en silencio: 200, no 403.
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Campos protegidos intactos.
        assertEquals(ORIGINAL_PASSWORD_HASH, demo.getPassword());
        assertEquals("Paciente", demo.getFirstName());
        assertEquals("Demo", demo.getLastName());
        assertEquals(DEMO_PATIENT_EMAIL, demo.getEmail());

        // Los campos que sí queremos que el evaluador pruebe se actualizan igual.
        assertEquals("Av. Siempre Viva 742", demo.getAddress());
        assertEquals("otro", demo.getGender());

        UserDetailDTO dto = (UserDetailDTO) response.getBody().get("user");
        assertEquals("Paciente", dto.firstName());
        assertEquals(DEMO_PATIENT_EMAIL, dto.email());
        assertTrue(dto.isDemo());
    }

    @Test
    void regularAccountDoesUpdatePasswordAndFirstName() {

        User regular = existingPatient(2L, "juan@therapify.com", "Juan", "Perez");
        authenticateAs(regular);

        UserRequestDTO req = new UserRequestDTO();
        req.setFirstName("Juan Carlos");
        req.setLastName("Gomez");
        req.setPassword("NuevaClave123");

        ResponseEntity<Map<String, Object>> response = userService.modificarMiUsuario(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("hash-de-NuevaClave123", regular.getPassword());
        assertEquals("Juan Carlos", regular.getFirstName());
        assertEquals("Gomez", regular.getLastName());

        UserDetailDTO dto = (UserDetailDTO) response.getBody().get("user");
        assertEquals("Juan Carlos", dto.firstName());
        assertFalse(dto.isDemo());
    }

    @Test
    void deletingADemoAccountIsRejected() {

        User demo = existingPatient(1L, DEMO_DOCTOR_EMAIL, "Terapeuta", "Demo");
        when(userRepository.findById(1L)).thenReturn(Optional.of(demo));

        AccessDeniedException ex =
                assertThrows(AccessDeniedException.class, () -> userService.eliminarUsuario(1L));

        assertEquals("Las cuentas de demostración no pueden eliminarse.", ex.getMessage());
    }

    /** PACIENTE on purpose: it skips the DOCTOR-only stats queries in mapToDTO. */
    private User existingPatient(Long id, String email, String firstName, String lastName) {
        User u = new User();
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(email);
        u.setPassword(ORIGINAL_PASSWORD_HASH);
        u.setUserType(UserType.PACIENTE);
        u.setEnabled(true);
        return u;
    }

    private void authenticateAs(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "n/a", user.getAuthorities())
        );
    }
}
