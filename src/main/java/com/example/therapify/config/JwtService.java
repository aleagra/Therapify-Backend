package com.example.therapify.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class JwtService {

    private static final String SECRET_KEY = "a-string-secret-at-least-256-bits-long";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);

    public String create(String username, String role) {

        // Limpia por si viene ROLE_DOCTOR
        String cleanRole = role.replace("ROLE_", "");

        return JWT.create()
                .withSubject(username)
                .withClaim("role", cleanRole)   // DOCTOR, PACIENTE, ADMIN
                .withIssuer("domestic-services")
                .withIssuedAt(new Date())
                .withExpiresAt(
                        new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(15))
                )
                .sign(ALGORITHM);
    }

    public boolean isValid(String jwt) {
        try {
            JWT.require(ALGORITHM)
                    .build()
                    .verify(jwt);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    public String getUsername(String jwt) {
        return JWT.require(ALGORITHM)
                .build()
                .verify(jwt)
                .getSubject();
    }

    public String getRole(String jwt) {
        return JWT.require(ALGORITHM)
                .build()
                .verify(jwt)
                .getClaim("role")
                .asString();
    }
}
