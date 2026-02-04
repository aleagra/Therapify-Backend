package com.example.therapify.service;

import com.example.therapify.config.JwtService;
import com.example.therapify.dtos.UserDTOs.UserDetailDTO;
import com.example.therapify.dtos.UserDTOs.UserRequestDTO;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.PasswordResetToken;
import com.example.therapify.model.User;
import com.example.therapify.repository.PasswordResetTokenRepository;
import com.example.therapify.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordResetTokenRepository tokenRepository;


    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,JwtService jwtService,PasswordResetTokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenRepository = tokenRepository;
    }

    // -------------------------
    // CREAR USUARIO
    // -------------------------
    public UserDetailDTO crearUsuario(UserRequestDTO req) {

        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        User u = new User();
        u.setFirstName(req.getFirstName());
        u.setLastName(req.getLastName());
        u.setEmail(req.getEmail());
        u.setCompanyName(req.getCompanyName());
        u.setUserType(req.getUserType());
        u.setGender(req.getGender());
        u.setAddress(req.getAddress());
        u.setDescription(req.getDescription());
        u.setPassword(passwordEncoder.encode(req.getPassword()));

        // Solo los doctores tienen schedule y availability
        if (req.getUserType() == UserType.DOCTOR) {
            u.setSchedule(req.getSchedule());
            u.setAvailability(req.getAvailability());
        }

        User saved = userRepository.save(u);

        return mapToDTO(saved);
    }

    // -------------------------
    // MAPEAR USUARIO A DTO
    // -------------------------
    private UserDetailDTO mapToDTO(User u) {
        // schedule solo si doctor
        Map<String, Boolean> scheduleMap = null;
        if (u.getUserType() == UserType.DOCTOR && u.getSchedule() != null && !u.getSchedule().isBlank()) {
            try {
                scheduleMap = new ObjectMapper().readValue(u.getSchedule(), new TypeReference<Map<String, Boolean>>() {});
            } catch (Exception e) {
                scheduleMap = null;
            }
        }

        // availability solo si doctor
        Map<String, List<String>> availabilityMap = u.getUserType() == UserType.DOCTOR ? u.getAvailabilityMap() : null;

        return new UserDetailDTO(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getUserType().name(),
                u.getCompanyName(),
                u.getGender(),
                u.getAddress(),
                u.getDescription(),
                null, // specialty: si la agregas en User lo tomás acá
                scheduleMap,
                availabilityMap
        );
    }

    // -------------------------
    // OBTENER USUARIO AUTENTICADO
    // -------------------------
    public User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
    }

    // -------------------------
    // LISTAR USUARIOS
    // -------------------------
    public List<UserDetailDTO> listarUsuarios() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    // BUSCAR USUARIO POR ID (entidad completa)
// -------------------------
    public User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
    }
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));
    }
    // -------------------------
    // MODIFICAR USUARIO
    // -------------------------
    // -------------------------
// MODIFICAR USUARIO
// -------------------------
    public ResponseEntity<Map<String, Object>> modificarMiUsuario(UserRequestDTO req) {
        User u = getAuthenticatedUser();

        if (req.getFirstName() != null) u.setFirstName(req.getFirstName());
        if (req.getLastName() != null) u.setLastName(req.getLastName());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getCompanyName() != null) u.setCompanyName(req.getCompanyName());
        if (req.getGender() != null) u.setGender(req.getGender());
        if (req.getAddress() != null) u.setAddress(req.getAddress());
        if (req.getDescription() != null) u.setDescription(req.getDescription());
        if (req.getPassword() != null && !req.getPassword().isBlank())
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        if (u.getUserType() == UserType.DOCTOR) {
            if (req.getSchedule() != null) u.setSchedule(req.getSchedule());
            if (req.getAvailability() != null) u.setAvailability(req.getAvailability());
        }

        // Guardamos cambios en la DB
        User updatedUser = userRepository.save(u);

        // Generamos un nuevo token JWT con el username/email y rol actualizado
        String newToken = jwtService.create(updatedUser.getEmail(), "ROLE_" + updatedUser.getUserType());

        // Mapear usuario a DTO para frontend
        UserDetailDTO dto = mapToDTO(updatedUser);

        Map<String, Object> res = new HashMap<>();
        res.put("mensaje", "Usuario actualizado correctamente");
        res.put("user", dto);
        res.put("token", newToken); // enviamos token actualizado

        return ResponseEntity.ok(res);
    }


    // -------------------------
    // ELIMINAR USUARIO
    // -------------------------
    public ResponseEntity<Map<String, String>> eliminarUsuario(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe usuario con ID " + id));
        userRepository.delete(u);

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Usuario eliminado correctamente");
        return ResponseEntity.ok(res);
    }

    // -------------------------
    // BUSQUEDAS PERSONALIZADAS
    // -------------------------
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

    // -------------------------
    // LOGIN → UserDetailsService
    // -------------------------
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + u.getUserType());

        return new org.springframework.security.core.userdetails.User(
                u.getEmail(),
                u.getPassword(),
                List.of(authority)
        );
    }

    // -------------------------
    // BUSCAR POR ID
    // -------------------------
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

}
