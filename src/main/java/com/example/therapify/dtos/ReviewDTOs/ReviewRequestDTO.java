package com.example.therapify.dtos.ReviewDTOs;

import jakarta.validation.constraints.*;

public class ReviewRequestDTO {

    @NotNull(message = "El ID del doctor es obligatorio")
    private Long doctorId;

    @NotBlank(message = "El comentario no puede estar vacío")
    @Size(max = 255, message = "El comentario no puede superar los 255 caracteres")
    private String comment;

    @Override
    public String toString() {
        return "ReviewRequestDTO{" +
                "doctorId=" + doctorId +
                ", comment='" + comment + '\'' +
                ", value=" + value +
                '}';
    }

    @NotNull(message = "La valoración es obligatoria")
    @Min(value = 1, message = "La valoración debe ser al menos 1")
    @Max(value = 5, message = "La valoración no puede superar 5")
    private Integer value;

    public ReviewRequestDTO() {}

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
