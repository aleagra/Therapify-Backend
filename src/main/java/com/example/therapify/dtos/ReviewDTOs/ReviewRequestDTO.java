package com.example.therapify.dtos.ReviewDTOs;

import jakarta.validation.constraints.*;

public class ReviewRequestDTO {
    @NotNull(message = "El ID del paciente es obligatorio")
    private Long patientId;

    @NotNull(message = "El ID del doctor es obligatorio")
    private Long doctorId;

    @NotBlank(message = "El comentario no puede estar vacío")
    @Size(max = 255, message = "El comentario no puede superar los 255 caracteres")
    private String comment;

    @NotNull(message = "La valoración es obligatoria")
    @Min(value = 1, message = "La valoración debe ser al menos 1")
    @Max(value = 5, message = "La valoración no puede superar 5")
    private Integer value;

    // No incluimos date porque lo genera el backend automáticamente (recomendado)
    // pero si querés que venga del front, avisame y te lo agrego.


    // -------- Constructor vacío --------
    public ReviewRequestDTO() {
    }

    // -------- Getters & Setters --------

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
