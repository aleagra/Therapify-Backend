package com.example.therapify.dtos.AppointmentDTOs;

/**
 * Body of PATCH /appointments/{id}/reschedule.
 *
 * Deliberately separate from {@link AppointmentRequestDTO}: a reschedule never chooses a
 * doctor (the slot moves inside the same professional's calendar) and every field is
 * mandatory, whereas the generic PATCH is a partial update where anything may be absent.
 *
 * doctorId is optional and exists only so the frontend can send what it has on screen; if it
 * arrives and doesn't match the appointment's doctor the request is rejected with 400 rather
 * than silently ignored — moving a turn to another professional is a cancel + re-book.
 *
 * No Bean Validation annotations on purpose: presence and format are checked in
 * AppointmentService so that every failure of this endpoint — malformed or semantic — comes
 * back in the one {error, mensaje, code} envelope, instead of half of them arriving as
 * Spring's ProblemDetail for @Valid.
 */
public class AppointmentRescheduleRequestDTO {

    private String date;
    private String startTime;
    private String endTime;
    private Long doctorId;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }
}
