package com.example.therapify.service;

import com.example.therapify.dtos.AppointmentDTOs.AppointmentDetailDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentListDTO;
import com.example.therapify.dtos.AppointmentDTOs.AppointmentRequestDTO;
import com.example.therapify.enums.Status;
import com.example.therapify.exception.AppointmentConflictException;
import com.example.therapify.model.Appointment;
import com.example.therapify.model.User;
import com.example.therapify.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserService userService;

    // -------------------------------------------------
    // CREATE APPOINTMENT
    // -------------------------------------------------
    public AppointmentDetailDTO createAppointment(AppointmentRequestDTO dto) {

        User patient = userService.getAuthenticatedUser();
        User doctor = userService.findEntityById(dto.getDoctorId());

        LocalDate date = LocalDate.parse(dto.getDate());
        LocalTime startTime = LocalTime.parse(dto.getStartTime());
        LocalTime endTime = LocalTime.parse(dto.getEndTime());

        boolean alreadyUsed = appointmentRepository
                .existsByDoctorIdAndDateAndStartTime(
                        doctor.getId(),
                        date,
                        startTime
                );

        if (alreadyUsed) {
            throw new AppointmentConflictException("Ese horario ya está reservado.");
        }

        Appointment ap = new Appointment();
        ap.setDoctor(doctor);
        ap.setPatient(patient);
        ap.setDate(date);
        ap.setStartTime(startTime);
        ap.setEndTime(endTime);
        ap.setStatus(Status.PENDING);

        appointmentRepository.save(ap);

        return toDetailDTO(ap);
    }

    // -------------------------------------------------
    // GET appointments by doctor + date
    // (para mostrar horarios ocupados)
    // -------------------------------------------------
    public List<AppointmentListDTO> getAppointmentsByDoctorAndDate(Long doctorId, String date) {

        LocalDate localDate = LocalDate.parse(date);

        return appointmentRepository
                .findByDoctorIdAndDate(doctorId, localDate)
                .stream()
                .map(this::toListDTO)
                .toList();
    }

    // -------------------------------------------------
    // DTO mapping
    // -------------------------------------------------
    private AppointmentDetailDTO toDetailDTO(Appointment ap) {
        AppointmentDetailDTO dto = new AppointmentDetailDTO();

        dto.setId(ap.getId());
        dto.setDoctorId(ap.getDoctor().getId());
        dto.setPatientId(ap.getPatient().getId());
        dto.setDate(ap.getDate().toString());
        dto.setStartTime(ap.getStartTime().toString());
        dto.setEndTime(ap.getEndTime().toString());
        dto.setStatus(ap.getStatus().name());
        dto.setCreatedAt(ap.getCreatedAt().toString());

        return dto;
    }

    private AppointmentListDTO toListDTO(Appointment ap) {
        AppointmentListDTO dto = new AppointmentListDTO();

        dto.setId(ap.getId());
        dto.setStartTime(ap.getStartTime().toString());
        dto.setEndTime(ap.getEndTime().toString());
        dto.setPatientName(ap.getPatient().getFirstName());

        return dto;
    }
}
