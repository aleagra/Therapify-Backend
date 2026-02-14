package com.example.therapify.model;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 500)
    private String comment;

    @Column(nullable = false)
    private Integer value;

    @Column(nullable = false)
    private LocalDateTime date;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    @JsonBackReference
    private User patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_id")
    @JsonBackReference
    private User doctor;

    public Review() {}

    public void setId(Long id) {
        this.id = id;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public Long getId() {
        return id;
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

    public LocalDateTime getDate() {
        return date;
    }

    public User getPatient() {
        return patient;
    }

    public void setPatient(User patient) {
        this.patient = patient;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
        this.doctor = doctor;
    }

    @PrePersist
    protected void onCreate() {
        this.date = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Review{" +
                "id=" + id +
                ", comment='" + comment + '\'' +
                ", value=" + value +
                ", date=" + date +
                ", patientId=" + (patient != null ? patient.getId() : null) +
                ", doctorId=" + (doctor != null ? doctor.getId() : null) +
                '}';
    }
}
