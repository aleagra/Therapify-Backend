package com.example.therapify.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppointmentConflictException.class)
    public ResponseEntity<Map<String, String>> handleAppointmentConflictException(AppointmentConflictException ex) {
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("error", "Turno no disponible");
        respuesta.put("mensaje", ex.getMessage());
        // Additive: "error"/"mensaje" keep the exact shape the frontend already handles on
        // POST /appointments, and "code" lets it tell the reschedule 409s apart.
        respuesta.put("code", ex.getCode().name());
        return new ResponseEntity<>(respuesta, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidAppointmentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidAppointmentException(InvalidAppointmentException ex) {
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("error", "Turno inválido");
        respuesta.put("mensaje", ex.getMessage());
        respuesta.put("code", ex.getCode().name());
        return new ResponseEntity<>(respuesta, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarResenaNoEncontrada(ReviewNotFoundException ex) {
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("error", "Review invalida");
        respuesta.put("mensaje", ex.getMessage());
        return new ResponseEntity<>(respuesta, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Acceso denegado");
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

}
