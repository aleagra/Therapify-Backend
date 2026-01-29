package com.example.therapify.service;
import com.example.therapify.dtos.UserDTOs.UserDetailDTO;
import com.example.therapify.dtos.UserDTOs.UserRequestDTO;
import com.example.therapify.enums.UserType;
import com.example.therapify.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.therapify.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ----------------------------------------------------
    // CREACIÓN DE USUARIO
    // ----------------------------------------------------
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

        u.setSchedule(req.getSchedule());
        u.setAvailability(req.getAvailability());

        User saved = userRepository.save(u);

        return new UserDetailDTO(
                saved.getId(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getEmail(),
                saved.getUserType()
        );
    }

    // ----------------------------------------------------
    // OBTENER USUARIO AUTENTICADO
    // ----------------------------------------------------
    public User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
    }

    // ----------------------------------------------------
    // LISTAR TODOS LOS USUARIOS
    // ----------------------------------------------------
    public List<UserDetailDTO> listarUsuarios() {
        return userRepository.findAll().stream()
                .map(u -> new UserDetailDTO(
                        u.getId(), u.getFirstName(), u.getLastName(),
                        u.getEmail(), u.getUserType()
                ))
                .toList();
    }

    // ----------------------------------------------------
    // MODIFICAR USUARIO ACTUAL
    // ----------------------------------------------------
    public ResponseEntity<Map<String, String>> modificarMiUsuario(UserRequestDTO req) {

        User u = getAuthenticatedUser();

        u.setFirstName(req.getFirstName());
        u.setLastName(req.getLastName());
        u.setEmail(req.getEmail());
        u.setCompanyName(req.getCompanyName());
        u.setGender(req.getGender());
        u.setAddress(req.getAddress());
        u.setDescription(req.getDescription());

        if (req.getPassword() != null && !req.getPassword().isEmpty()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        u.setSchedule(req.getSchedule());
        u.setAvailability(req.getAvailability());

        userRepository.save(u);

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Usuario actualizado correctamente");

        return ResponseEntity.ok(res);
    }

    // ----------------------------------------------------
    // ELIMINAR USUARIO POR ID (solo admin)
    // ----------------------------------------------------
    public ResponseEntity<Map<String, String>> eliminarUsuario(Long id) {

        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe usuario con ID " + id));

        userRepository.delete(u);

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Usuario eliminado correctamente");

        return ResponseEntity.ok(res);
    }

    // ----------------------------------------------------
    // ELIMINAR MI PROPIA CUENTA
    // ----------------------------------------------------
    public ResponseEntity<Map<String, String>> eliminarMiCuenta() {
        User u = getAuthenticatedUser();

        userRepository.delete(u);

        SecurityContextHolder.clearContext();

        Map<String, String> res = new HashMap<>();
        res.put("mensaje", "Cuenta eliminada correctamente");

        return ResponseEntity.ok(res);
    }

    // ----------------------------------------------------
    // BUSCAR POR ID
    // ----------------------------------------------------
    public UserDetailDTO buscarPorId(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return new UserDetailDTO(
                u.getId(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.getUserType()
        );
    }

    public User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
    }
    // ----------------------------------------------------
    // BUSQUEDAS PERSONALIZADAS
    // ----------------------------------------------------
    public List<UserDetailDTO> findByUserType(String type) {
        return userRepository.findByUserType(UserType.valueOf(type)).stream()
                .map(u -> new UserDetailDTO(
                        u.getId(), u.getFirstName(), u.getLastName(),
                        u.getEmail(), u.getUserType()
                )).toList();
    }

    public List<UserDetailDTO> findByFirstName(String firstName) {
        return userRepository.findByFirstName(firstName).stream()
                .map(u -> new UserDetailDTO(
                        u.getId(), u.getFirstName(), u.getLastName(),
                        u.getEmail(), u.getUserType()
                )).toList();
    }

    public List<UserDetailDTO> findByLastName(String lastName) {
        return userRepository.findByLastName(lastName).stream()
                .map(u -> new UserDetailDTO(
                        u.getId(), u.getFirstName(), u.getLastName(),
                        u.getEmail(), u.getUserType()
                )).toList();
    }

    // ----------------------------------------------------
    // LOGIN → UserDetailsService (Spring Security)
    // ----------------------------------------------------
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        GrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + u.getUserType());

        return new org.springframework.security.core.userdetails.User(
                u.getEmail(),
                u.getPassword(),
                List.of(authority)
        );
    }

}
