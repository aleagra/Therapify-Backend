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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserService userService;
    @Autowired
    private EmailService emailService;

    public enum AppointmentTimeFilter { UPCOMING, PAST }

    @Transactional
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

        try {
            // The exists-check above is not race-proof under concurrent requests for the
            // same slot; the DB unique constraint on (doctor_id, date, start_time) is the
            // real guard, and this catch turns that violation into a 409 instead of a 500.
            appointmentRepository.save(ap);
        } catch (DataIntegrityViolationException e) {
            throw new AppointmentConflictException("Ese horario ya está reservado.");
        }

        userService.evictDoctorCaches(doctor.getId());

        emailService.sendAppointmentConfirmation(
                patient.getEmail(),
                doctor.getEmail(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                date.toString(),
                startTime.toString(),
                endTime.toString()
        );

        return toDetailDTO(ap);
    }

    public Page<AppointmentListDTO> getMyAppointments(
            AppointmentTimeFilter timeFilter,
            Status status,
            Pageable pageable
    ) {
        User user = userService.getAuthenticatedUser();
        boolean isAdmin = user.getUserType().name().equals("ADMIN");

        LocalDate today = LocalDate.now();
        LocalDate fromDate = timeFilter == AppointmentTimeFilter.UPCOMING ? today : null;
        LocalDate toDate = timeFilter == AppointmentTimeFilter.PAST ? today : null;

        return appointmentRepository
                .findMyAppointments(
                        isAdmin ? null : user.getId(),
                        fromDate,
                        toDate,
                        status,
                        pageable
                )
                .map(this::toListDTO);
    }

    public AppointmentDetailDTO updateAppointmentStatus(Long id, String statusStr) {
        Appointment ap = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        Status status = Status.valueOf(statusStr);
        ap.setStatus(status);
        appointmentRepository.save(ap);
        userService.evictDoctorCaches(ap.getDoctor().getId());

        return toDetailDTO(ap);
    }

    public boolean deleteAppointment(Long id) {
        Appointment ap = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        Long doctorId = ap.getDoctor().getId();
        appointmentRepository.delete(ap);
        userService.evictDoctorCaches(doctorId);
        return true;
    }

    public List<AppointmentListDTO> getAppointmentsByDoctorAndDate(Long doctorId, String date) {

        LocalDate localDate = LocalDate.parse(date);

        return appointmentRepository
                .findByDoctorIdAndDate(doctorId, localDate)
                .stream()
                .map(this::toListDTO)
                .toList();
    }

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
        dto.setDate(ap.getDate().toString());
        dto.setStartTime(ap.getStartTime().toString());
        dto.setEndTime(ap.getEndTime().toString());
        dto.setStatus(ap.getStatus().name());
        dto.setDoctorId(ap.getDoctor().getId());
        dto.setPatientId(ap.getPatient().getId());
        dto.setCreatedAt(ap.getCreatedAt().toString());

        // ap.getPatient()/ap.getDoctor() are already loaded associations — no need for
        // extra findEntityById lookups (that was a per-row N+1 on every appointment list).
        User patient = ap.getPatient();
        dto.setPatientName(patient.getFirstName() + " " + patient.getLastName());

        User doctor = ap.getDoctor();
        dto.setDoctorName(doctor.getFirstName() + " " + doctor.getLastName());
        dto.setDoctorSpecialty(doctor.getSpecialty() != null ? doctor.getSpecialty().name() : null);

        return dto;
    }
}
