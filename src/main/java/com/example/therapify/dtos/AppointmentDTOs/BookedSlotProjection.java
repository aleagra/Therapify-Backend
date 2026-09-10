package com.example.therapify.dtos.AppointmentDTOs;

import java.time.LocalDate;
import java.time.LocalTime;

public interface BookedSlotProjection {
    Long getDoctorId();
    LocalDate getDate();
    LocalTime getStartTime();
}
