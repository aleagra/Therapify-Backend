package com.example.therapify.controller;

import com.example.therapify.dtos.AppointmentDTOs.AppointmentDetailDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentListDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentRequestDTO;
import com.example.therapify.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // ------------------------------
    // POST: crear turno (PACIENTE)
    // ------------------------------
    @PreAuthorize("hasAnyRole('PACIENTE','DOCTOR')")
    @PostMapping
    public ResponseEntity<AppointmentDetailDTO> createAppointment(
            @Valid @RequestBody AppointmentRequestDTO dto
    ) {
        AppointmentDetailDTO saved = appointmentService.createAppointment(dto);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('PACIENTE','DOCTOR','ADMIN')")
    @GetMapping("/mine")
    public ResponseEntity<List<AppointmentListDTO>> getMyAppointments() {
        return ResponseEntity.ok(
                appointmentService.getMyAppointments()
        );
    }

    @PreAuthorize("hasAnyRole('DOCTOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteAppointment(@PathVariable Long id) {
        boolean deleted = appointmentService.deleteAppointment(id);
        return ResponseEntity.ok(deleted);
    }


    // ------------------------------
    // GET: turnos de un doctor por fecha
    // (PACIENTE / DOCTOR / ADMIN)
    // ------------------------------
    @PreAuthorize("hasAnyRole('PACIENTE','DOCTOR','ADMIN')")
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<AppointmentListDTO>> getAppointmentsByDoctorAndDate(
            @PathVariable Long doctorId,
            @RequestParam String date
    ) {
        List<AppointmentListDTO> appointments =
                appointmentService.getAppointmentsByDoctorAndDate(doctorId, date);

        return ResponseEntity.ok(appointments);
    }

    @PreAuthorize("hasAnyRole('DOCTOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<AppointmentDetailDTO> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> updates
    ) {
        String status = updates.get("status");
        AppointmentDetailDTO updated = appointmentService.updateAppointmentStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}
