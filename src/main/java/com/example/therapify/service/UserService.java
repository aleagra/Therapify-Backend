package com.example.therapify.service;

import com.example.therapify.config.DemoGuard;
import com.example.therapify.config.GeoUtils;
import com.example.therapify.config.JwtService;
import com.example.therapify.exception.AccessDeniedException;
import com.example.therapify.dtos.ReviewDTOs.DoctorRatingStats;
import com.example.therapify.dtos.UserDTOs.UserDetailDTO;
import com.example.therapify.dtos.UserDTOs.UserRequestDTO;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.EmailVerificationToken;
import com.example.therapify.model.PasswordResetToken;
import com.example.therapify.model.User;
import com.example.therapify.repository.*;
import com.example.therapify.util.AvailabilityCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

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
    private final CacheManager cacheManager;
    private final DemoGuard demoGuard;


    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, PasswordResetTokenRepository tokenRepository, GeocodingService geocodingService, EmailService emailService, EmailVerificationTokenRepository emailTokenRepository, AppointmentRepository appointmentRepository, ReviewRepository reviewRepository, CacheManager cacheManager, DemoGuard demoGuard) {
        this.demoGuard = demoGuard;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenRepository = tokenRepository;
        this.geocodingService = geocodingService;
        this.emailService = emailService;
        this.cacheManager = cacheManager;
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
                "https://therapifyy.vercel.app/verify-email?token=" + token;

        try {
            emailService.sendEmailVerification(saved.getEmail(), link);
        } catch (Exception e) {
            System.err.println("❌ Error enviando email de verificación");
            e.printStackTrace();
        }

        return mapToDTO(saved);
    }

    private static final int AVAILABILITY_WINDOW_DAYS = 30;
    private static final int MAX_NEXT_AVAILABLE_DATES = 5;

    private record DoctorStats(
            double averageRating,
            int totalReviews,
            int availableSlotsCount,
            List<LocalDate> nextAvailableDates
    ) {
        static DoctorStats empty() {
            return new DoctorStats(0.0, 0, 0, List.of());
        }
    }

    /**
     * Batch-computes rating average/count and available-slots stats for every DOCTOR in
     * {@code users}, using at most 2 extra queries total (not per-doctor), to avoid N+1.
     */
    private Map<Long, DoctorStats> computeDoctorStats(List<User> users) {

        Map<Long, User> doctorsById = users.stream()
                .filter(u -> u.getUserType() == UserType.DOCTOR)
                .collect(Collectors.toMap(User::getId, Function.identity()));

        if (doctorsById.isEmpty()) return Map.of();

        List<Long> doctorIds = new ArrayList<>(doctorsById.keySet());

        Map<Long, DoctorRatingStats> ratingByDoctor = reviewRepository
                .findRatingStatsByDoctorIds(doctorIds)
                .stream()
                .collect(Collectors.toMap(DoctorRatingStats::doctorId, Function.identity()));

        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(AVAILABILITY_WINDOW_DAYS);

        Map<Long, Set<String>> bookedSlotsByDoctor = appointmentRepository
                .findBookedSlots(doctorIds, today, windowEnd)
                .stream()
                .collect(Collectors.groupingBy(
                        b -> b.getDoctorId(),
                        Collectors.mapping(
                                b -> AvailabilityCalculator.slotKey(b.getDate(), b.getStartTime()),
                                Collectors.toSet()
                        )
                ));

        Map<Long, DoctorStats> result = new HashMap<>();

        for (Long doctorId : doctorIds) {
            DoctorRatingStats rating = ratingByDoctor.get(doctorId);

            double averageRating = (rating != null && rating.averageRating() != null)
                    ? Math.round(rating.averageRating() * 10) / 10.0
                    : 0.0;
            int totalReviews = rating != null ? rating.totalReviews().intValue() : 0;

            AvailabilityCalculator.Result availability = AvailabilityCalculator.compute(
                    doctorsById.get(doctorId).getAvailabilityMap(),
                    bookedSlotsByDoctor.getOrDefault(doctorId, Set.of()),
                    today,
                    windowEnd,
                    MAX_NEXT_AVAILABLE_DATES
            );

            result.put(doctorId, new DoctorStats(
                    averageRating,
                    totalReviews,
                    availability.availableSlotsCount(),
                    availability.nextAvailableDates()
            ));
        }

        return result;
    }

    private UserDetailDTO mapToDTO(User u, Map<Long, DoctorStats> statsByDoctorId) {

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

        DoctorStats stats = u.getUserType() == UserType.DOCTOR
                ? statsByDoctorId.getOrDefault(u.getId(), DoctorStats.empty())
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
                null,
                u.getDescription(),
                u.getSpecialty() != null ? u.getSpecialty().name() : null,
                scheduleMap,
                availabilityMap,
                u.getConsultationPrice(),
                stats != null ? stats.averageRating() : null,
                stats != null ? stats.totalReviews() : null,
                stats != null ? stats.availableSlotsCount() : null,
                stats != null ? stats.nextAvailableDates() : null,
                demoGuard.isDemoAccount(u)
        );
    }

    private UserDetailDTO mapToDTO(User u) {
        return mapToDTO(u, computeDoctorStats(List.of(u)));
    }


    private UserDetailDTO mapToDTOWithDistance(User u, double distance, Map<Long, DoctorStats> statsByDoctorId) {

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

        DoctorStats stats = u.getUserType() == UserType.DOCTOR
                ? statsByDoctorId.getOrDefault(u.getId(), DoctorStats.empty())
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
                u.getConsultationPrice(),
                stats != null ? stats.averageRating() : null,
                stats != null ? stats.totalReviews() : null,
                stats != null ? stats.availableSlotsCount() : null,
                stats != null ? stats.nextAvailableDates() : null,
                demoGuard.isDemoAccount(u)
        );
    }

    /**
     * Invalidates the cached doctor detail/listing entries affected by a profile,
     * appointment, or review change for this user. Programmatic (not @CacheEvict)
     * because the affected id/doctor isn't always a direct method parameter here.
     */
    public void evictDoctorCaches(Long userId) {
        Cache userDetailCache = cacheManager.getCache("userDetail");
        if (userDetailCache != null) userDetailCache.evict(userId);

        Cache doctorListingsCache = cacheManager.getCache("doctorListings");
        if (doctorListingsCache != null) doctorListingsCache.clear();
    }

    public User getAuthenticatedUser() {
        String email = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
    }

    public List<UserDetailDTO> listarUsuarios() {
        List<User> users = userRepository.findAll();
        Map<Long, DoctorStats> stats = computeDoctorStats(users);
        return users.stream()
                .map(u -> mapToDTO(u, stats))
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
    /**
     * Identity fields that a demo account may never change: rewriting any of them would lock
     * every later evaluator out of the shared demo login until it's fixed by hand in the DB.
     * Everything else (address, gender, specialty, price, schedule, availability, description)
     * stays editable on purpose — that's what we want evaluators to try.
     */
    private static final List<String> DEMO_PROTECTED_FIELDS =
            List.of("password", "email", "firstName", "lastName");

    public ResponseEntity<Map<String, Object>> modificarMiUsuario(UserRequestDTO req) {

        User u = getAuthenticatedUser();

        // Ignore silently rather than reject: the UI already disables these inputs, so a 403
        // would only be noise, and anyone bypassing the UI gets no signal that they hit a guard.
        boolean isDemo = demoGuard.isDemoAccount(u);

        if (isDemo) {
            List<String> attempted = new ArrayList<>();
            if (req.getPassword() != null && !req.getPassword().isBlank()) attempted.add("password");
            if (req.getEmail() != null) attempted.add("email");
            if (req.getFirstName() != null) attempted.add("firstName");
            if (req.getLastName() != null) attempted.add("lastName");

            if (!attempted.isEmpty()) {
                log.warn("Intento de modificar campos protegidos {} en la cuenta demo {}; ignorado. Campos protegidos: {}",
                        attempted, u.getEmail(), DEMO_PROTECTED_FIELDS);
            }
        }

        if (!isDemo && req.getFirstName() != null)
            u.setFirstName(req.getFirstName());

        if (!isDemo && req.getLastName() != null)
            u.setLastName(req.getLastName());

        if (!isDemo && req.getEmail() != null)
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

        if (!isDemo && req.getPassword() != null && !req.getPassword().isBlank()) {
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
        evictDoctorCaches(updatedUser.getId());
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
        rechazarSiEsCuentaDemo(u);
        userRepository.delete(u);
        evictDoctorCaches(id);

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Usuario eliminado correctamente");
        return ResponseEntity.ok(res);
    }

    @Cacheable(value = "doctorListings", key = "'byType:' + #type")
    public List<UserDetailDTO> findByUserType(String type) {
        List<User> users = userRepository.findByUserType(UserType.valueOf(type));
        Map<Long, DoctorStats> stats = computeDoctorStats(users);
        return users.stream()
                .map(u -> mapToDTO(u, stats))
                .toList();
    }

    @Cacheable(value = "doctorListings", key = "'byFirstName:' + #firstName")
    public List<UserDetailDTO> findByFirstName(String firstName) {
        List<User> users = userRepository.findByFirstName(firstName);
        Map<Long, DoctorStats> stats = computeDoctorStats(users);
        return users.stream()
                .map(u -> mapToDTO(u, stats))
                .toList();
    }

    @Cacheable(value = "doctorListings", key = "'byLastName:' + #lastName")
    public List<UserDetailDTO> findByLastName(String lastName) {
        List<User> users = userRepository.findByLastName(lastName);
        Map<Long, DoctorStats> stats = computeDoctorStats(users);
        return users.stream()
                .map(u -> mapToDTO(u, stats))
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

    @Cacheable(value = "userDetail", key = "#id")
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

    @Cacheable(value = "doctorListings", key = "'near:' + #lat + ':' + #lng")
    public List<UserDetailDTO> findDoctorsNear(double lat, double lng) {

        List<User> doctors = userRepository.findByUserType(UserType.DOCTOR)
                .stream()
                .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                .toList();

        Map<Long, DoctorStats> stats = computeDoctorStats(doctors);

        List<UserDetailDTO> result = doctors
                .stream()
                .map(d -> {

                    double distance = GeoUtils.distanceKm(
                            lat, lng,
                            d.getLatitude(),
                            d.getLongitude()
                    );

                    return mapToDTOWithDistance(d, distance, stats);
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
        rechazarSiEsCuentaDemo(u);

        // Borrar todas las citas donde es doctor o paciente
        appointmentRepository.deleteByDoctorOrPatient(u, u);

        // Borrar todas las reseñas creadas o recibidas
        reviewRepository.deleteByDoctorOrPatient(u, u);

        // Finalmente borrar el usuario
        userRepository.delete(u);
        evictDoctorCaches(id);

        return ResponseEntity.ok(Map.of("mensaje", "Usuario y datos asociados eliminados correctamente"));
    }

    /**
     * Unlike the silent field filtering in {@link #modificarMiUsuario}, deleting a demo account
     * is irreversible and must never fail quietly: surface it as a 403 (see GlobalExceptionHandler).
     */
    private void rechazarSiEsCuentaDemo(User u) {
        if (demoGuard.isDemoAccount(u)) {
            log.warn("Intento de eliminar la cuenta demo {}; rechazado.", u.getEmail());
            throw new AccessDeniedException(
                    "Las cuentas de demostración no pueden eliminarse."
            );
        }
    }

}
