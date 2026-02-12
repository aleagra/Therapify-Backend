package com.example.therapify.dtos.ReviewDTOs;
import java.time.LocalDate;

public class ReviewDetailDTO {

    private Long id;
    private String comment;
    private Integer value;
    private LocalDate date;

    private Long patientId;
    private String patientName;
    private String patientLastName;
    private Long doctorId;
    private String doctorName;


    public ReviewDetailDTO() {}

    // Getters y setters...

    public String getPatientLastName() {
        return patientLastName;
    }

    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

}
