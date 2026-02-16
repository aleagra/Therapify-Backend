package com.example.therapify.service;

import com.example.therapify.config.GeoUtils;
import com.example.therapify.config.JwtService;
import com.example.therapify.dtos.UserDTOs.UserDetailDTO;
import com.example.therapify.dtos.UserDTOs.UserRequestDTO;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.EmailVerificationToken;
import com.example.therapify.model.PasswordResetToken;
import com.example.therapify.model.User;
import com.example.therapify.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserService implements UserDetailsService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordResetTokenRepository tokenRepository;
    private final GeocodingService geocodingService;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository emailTokenRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;


    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, PasswordResetTokenRepository tokenRepository, GeocodingService geocodingService, EmailService emailService, EmailVerificationTokenRepository emailTokenRepository, AppointmentRepository appointmentRepository, ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenRepository = tokenRepository;
        this.geocodingService = geocodingService;
        this.emailService = emailService;
        this.emailTokenRepository = emailTokenRepository;
        this.appointmentRepository = appointmentRepository;
        this.reviewRepository = reviewRepository;
    }

    @PreAuthorize("permitAll()")
    @Transactional
    public UserDetailDTO crearUsuario(UserRequestDTO req) {

        Optional<User> existing = userRepository.findByEmail(req.getEmail());

        if (existing.isPresent()) {
            if (!existing.get().isEnabled()) {
                throw new IllegalArgumentException(
                        "Ya existe una cuenta con ese email. Revisá tu correo para verificarla."
                );
            }
            throw new IllegalArgumentException("El email ya está registrado");
        }

        User u = new User();
        u.setFirstName(req.getFirstName());
        u.setLastName(req.getLastName());
        u.setEmail(req.getEmail());
        u.setCompanyName(req.getCompanyName());
        u.setUserType(req.getUserType());
        u.setGender(req.getGender());
        u.setDescription(req.getDescription());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setEnabled(false);

        if (req.getAddress() != null && !req.getAddress().isBlank()) {
            u.setAddress(req.getAddress());

            if (req.getUserType() == UserType.DOCTOR) {
                double[] coords = geocodingService.getCoordinates(req.getAddress());
                u.setLatitude(coords[0]);
                u.setLongitude(coords[1]);
            }
        }

        if (req.getUserType() == UserType.DOCTOR) {

            u.setSpecialty(req.getSpecialty());
            u.setConsultationPrice(req.getConsultationPrice());

            if (req.getSchedule() != null) {
                try {
                    String scheduleJson = new ObjectMapper()
                            .writeValueAsString(req.getSchedule());
                    u.setSchedule(scheduleJson);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializando schedule", e);
                }
            }

            if (req.getAvailability() != null) {
                try {
                    String availabilityJson = new ObjectMapper()
                            .writeValueAsString(req.getAvailability());
                    u.setAvailability(availabilityJson);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializando availability", e);
                }
            }


        }

        User saved;
        try {
            saved = userRepository.save(u);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                    "Ya existe un usuario registrado con ese email"
            );
        }

        String token = UUID.randomUUID().toString();

        EmailVerificationToken verificationToken =
                new EmailVerificationToken(
                        token,
                        saved,
                        LocalDateTime.now().plusHours(24)
                );

        emailTokenRepository.save(verificationToken);

        String link =
                "http://localhost:4200/verify-email?token=" + token;

        try {
            emailService.sendEmailVerification(saved.getEmail(), link);
        } catch (Exception e) {
            System.err.println("❌ Error enviando email de verificación");
            e.printStackTrace();
        }

        return mapToDTO(saved);
    }

    private UserDetailDTO mapToDTO(User u) {

        Map<String, Boolean> scheduleMap = null;
        Map<String, List<String>> availabilityMap = null;

        if (u.getUserType() == UserType.DOCTOR) {

            try {

                if (u.getSchedule() != null && !u.getSchedule().isBlank()) {
                    scheduleMap = objectMapper.readValue(
                            u.getSchedule(),
                            new TypeReference<Map<String, Boolean>>() {}
                    );
                }

                if (u.getAvailability() != null && !u.getAvailability().isBlank()) {
                    availabilityMap = objectMapper.readValue(
                            u.getAvailability(),
                            new TypeReference<Map<String, List<String>>>() {}
                    );
                }

            } catch (Exception e) {
                throw new RuntimeException("Error convirtiendo JSON a Map en mapToDTO", e);
            }
        }

        return new UserDetailDTO(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getUserType().name(),
                u.getCompanyName(),
                u.getGender(),
                u.getAddress(),
                u.getLatitude(),
                u.getLongitude(),
                null,
                u.getDescription(),
                u.getSpecialty() != null ? u.getSpecialty().name() : null,
                scheduleMap,
                availabilityMap,
                u.getConsultationPrice()
        );
    }


    private UserDetailDTO mapToDTOWithDistance(User u, double distance) {

        Map<String, Boolean> scheduleMap = null;

        if (u.getUserType() == UserType.DOCTOR && u.getSchedule() != null && !u.getSchedule().isBlank()) {
            try {
                scheduleMap = objectMapper.readValue(
                        u.getSchedule(),
                        new TypeReference<Map<String, Boolean>>() {}
                );
            } catch (Exception e) {
                scheduleMap = null;
            }
        }

        Map<String, List<String>> availabilityMap =
                u.getUserType() == UserType.DOCTOR
                        ? u.getAvailabilityMap()
                        : null;

        return new UserDetailDTO(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getUserType().name(),
                u.getCompanyName(),
                u.getGender(),
                u.getAddress(),
                u.getLatitude(),
                u.getLongitude(),
                distance,
                u.getDescription(),
                u.getSpecialty() != null ? u.getSpecialty().name() : null,
                scheduleMap,
                availabilityMap,
                u.getConsultationPrice()
        );
    }

    public User getAuthenticatedUser() {
        String email = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
    }

    public List<UserDetailDTO> listarUsuarios() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    public User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
    }
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));
    }
    public ResponseEntity<Map<String, Object>> modificarMiUsuario(UserRequestDTO req) {

        User u = getAuthenticatedUser();

        if (req.getFirstName() != null)
            u.setFirstName(req.getFirstName());

        if (req.getLastName() != null)
            u.setLastName(req.getLastName());

        if (req.getEmail() != null)
            u.setEmail(req.getEmail());

        if (req.getCompanyName() != null)
            u.setCompanyName(req.getCompanyName());

        if (req.getGender() != null)
            u.setGender(req.getGender());

        if (req.getAddress() != null && !req.getAddress().isBlank()) {

            String nuevaDireccion = req.getAddress().trim();

            if (!nuevaDireccion.equalsIgnoreCase(
                    u.getAddress() != null ? u.getAddress().trim() : ""
            )) {

                u.setAddress(nuevaDireccion);

                if (u.getUserType() == UserType.DOCTOR) {

                    try {
                        double[] coords =
                                geocodingService.getCoordinates(nuevaDireccion);

                        u.setLatitude(coords[0]);
                        u.setLongitude(coords[1]);

                    } catch (Exception e) {
                        System.out.println("⚠ No se pudo geocodificar la dirección: " + e.getMessage());
                    }
                }
            }
        }

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        if (u.getUserType() == UserType.DOCTOR) {

            try {

                if (req.getSchedule() != null) {
                    String scheduleJson =
                            objectMapper.writeValueAsString(req.getSchedule());
                    u.setSchedule(scheduleJson);
                }

                if (req.getAvailability() != null) {
                    String availabilityJson =
                            objectMapper.writeValueAsString(req.getAvailability());
                    u.setAvailability(availabilityJson);
                }

                if (req.getSpecialty() != null)
                    u.setSpecialty(req.getSpecialty());

                if (req.getConsultationPrice() != null)
                    u.setConsultationPrice(req.getConsultationPrice());

                if (req.getDescription() != null)
                    u.setDescription(req.getDescription());

            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error convirtiendo schedule/availability a JSON", e);
            }
        }

        User updatedUser = userRepository.save(u);
        String newToken = jwtService.create(
                updatedUser.getEmail(),
                updatedUser.getUserType().name()
        );

        UserDetailDTO dto = mapToDTO(updatedUser);

        Map<String, Object> res = new HashMap<>();
        res.put("mensaje", "Usuario actualizado correctamente");
        res.put("user", dto);
        res.put("token", newToken);

        return ResponseEntity.ok(res);
    }

    public ResponseEntity<Map<String, String>> eliminarUsuario(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe usuario con ID " + id));
        userRepository.delete(u);

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Usuario eliminado correctamente");
        return ResponseEntity.ok(res);
    }

    public List<UserDetailDTO> findByUserType(String type) {
        return userRepository.findByUserType(UserType.valueOf(type)).stream()
                .map(this::mapToDTO)
                .toList();
    }

    public List<UserDetailDTO> findByFirstName(String firstName) {
        return userRepository.findByFirstName(firstName).stream()
                .map(this::mapToDTO)
                .toList();
    }

    public List<UserDetailDTO> findByLastName(String lastName) {
        return userRepository.findByLastName(lastName).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + u.getUserType());

        return new org.springframework.security.core.userdetails.User(
                u.getEmail(),
                u.getPassword(),
                u.isEnabled(),
                true,
                true,
                true,
                List.of(authority)
        );
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public UserDetailDTO buscarPorId(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return mapToDTO(u);
    }
    public String createPasswordResetToken(User user) {

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken =
                new PasswordResetToken(
                        token,
                        user,
                        LocalDateTime.now().plusMinutes(30)
                );

        tokenRepository.save(resetToken);

        return token;
    }

    public boolean resetPassword(String token, String newPassword) {

        PasswordResetToken prt =
                tokenRepository.findByToken(token);

        if (prt == null) return false;

        if (prt.getExpiration().isBefore(LocalDateTime.now()))
            return false;

        User user = prt.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.delete(prt);

        return true;
    }

    public List<UserDetailDTO> findDoctorsNear(double lat, double lng) {

        List<UserDetailDTO> result = userRepository.findByUserType(UserType.DOCTOR)
                .stream()
                .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                .map(d -> {

                    double distance = GeoUtils.distanceKm(
                            lat, lng,
                            d.getLatitude(),
                            d.getLongitude()
                    );

                    UserDetailDTO dto = mapToDTOWithDistance(d, distance);

                    return dto;
                })
                .sorted((a, b) ->
                        Double.compare(a.distanceKm(), b.distanceKm()))
                .toList();

        return result;
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> eliminarUsuarioConDatos(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe usuario con ID " + id));

        // Borrar todas las citas donde es doctor o paciente
        appointmentRepository.deleteByDoctorOrPatient(u, u);

        // Borrar todas las reseñas creadas o recibidas
        reviewRepository.deleteByDoctorOrPatient(u, u);

        // Finalmente borrar el usuario
        userRepository.delete(u);

        return ResponseEntity.ok(Map.of("mensaje", "Usuario y datos asociados eliminados correctamente"));
    }


}
