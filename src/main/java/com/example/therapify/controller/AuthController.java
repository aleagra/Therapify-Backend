package com.example.therapify.controller;
import com.example.therapify.dtos.UserDTOs.AuthRequest;
import com.example.therapify.model.EmailVerificationToken;
import com.example.therapify.model.User;
import com.example.therapify.config.JwtService;
import com.example.therapify.repository.EmailVerificationTokenRepository;
import com.example.therapify.service.EmailService;
import com.example.therapify.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository emailTokenRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserService userService,
            EmailService emailService, EmailVerificationTokenRepository emailTokenRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.emailService = emailService;
        this.emailTokenRepository = emailTokenRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequest.getEmail(),
                        authRequest.getPassword()
                )
        );


        User user = userService.findByEmail(authRequest.getEmail());

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Debes verificar tu email"
            );
        }
        String token = jwtService.create(
                user.getEmail(),
                user.getUserType().name()
        );

        return ResponseEntity.ok(
                Map.of(
                        "token", token,
                        "id", user.getId(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "email", user.getEmail(),
                        "userType", user.getUserType()
                )
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody Map<String, String> body
    ) {

        String email = body.get("email");

        User user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.ok().build();
        }

        String token = userService.createPasswordResetToken(user);

        String link =
                "http://localhost:4200/reset-password?token=" + token;

        emailService.sendResetPassword(
                user.getEmail(),
                link
        );

        return ResponseEntity.ok(
                Map.of("message", "Mail enviado")
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody Map<String, String> body
    ) {

        String token = body.get("token");
        String newPassword = body.get("password");

        boolean success =
                userService.resetPassword(token, newPassword);

        if (!success) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Token inválido o expirado")
            );
        }

        return ResponseEntity.ok(
                Map.of("message", "Contraseña actualizada")
        );
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {

        EmailVerificationToken evt =
                emailTokenRepository.findByToken(token);

        if (evt == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Token inválido")
            );
        }

        if (evt.getExpiration().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Token expirado")
            );
        }

        User user = evt.getUser();
        user.setEnabled(true);
        userService.save(user);

        emailTokenRepository.delete(evt);

        return ResponseEntity.ok(
                Map.of("message", "Cuenta verificada correctamente")
        );
    }


}
