package com.example.therapify.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void appointmentConflictMapsToHttp409() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAppointmentConflictException(new AppointmentConflictException("Ese horario ya está reservado."));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Ese horario ya está reservado.", response.getBody().get("mensaje"));
    }
}
