package com.example.therapify.model;
import com.example.therapify.enums.Status;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_appointments_doctor_date_start",
                        columnNames = {"doctor_id", "date", "start_time"}
                )
        },
        indexes = {
                @Index(name = "idx_appointments_doctor_id_date", columnList = "doctor_id, date")
        }
)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * How many times this appointment was moved via PATCH /appointments/{id}/reschedule.
     * Caps the reschedule limit (see AppointmentService.MAX_RESCHEDULES) and gives the doctor
     * context on how much the slot has already moved.
     *
     * columnDefinition carries the DB-side default on purpose: the project runs ddl-auto=update
     * with no Flyway, and Postgres refuses to add a NOT NULL column to a populated table unless
     * the ALTER also supplies a default (it backfills existing rows with it).
     */
    @Column(name = "reschedule_count", nullable = false, columnDefinition = "integer default 0")
    private int rescheduleCount = 0;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_id")
    @JsonBackReference
    private User doctor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    @JsonBackReference
    private User patient;

    public Appointment() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = Status.PENDING;
    }

    public Long getId() { return id; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        this.status = status;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public int getRescheduleCount() { return rescheduleCount; }
    public void setRescheduleCount(int rescheduleCount) { this.rescheduleCount = rescheduleCount; }

    /** Moves the appointment to a new slot in place — same row, same id — and counts the move. */
    public void applyReschedule(LocalDate newDate, LocalTime newStartTime, LocalTime newEndTime) {
        this.date = newDate;
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.rescheduleCount++;
    }

    /** Start of the appointment as a single instant-comparable value (used by the 24h rule). */
    public LocalDateTime startsAt() {
        return LocalDateTime.of(date, startTime);
    }

    /**
     * End of the appointment, as a single instant-comparable value. Same notion of "already
     * finished" the completion job uses, which spells it out as a split predicate because JPQL
     * cannot add a DATE and a TIME.
     */
    public LocalDateTime endsAt() {
        return LocalDateTime.of(date, endTime);
    }

    public User getDoctor() { return doctor; }
    public void setDoctor(User doctor) { this.doctor = doctor; }

    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }


    @Override
    public String toString() {
        return "Appointment{" +
                "id=" + id +
                ", date=" + date +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", status=" + status +
                ", doctorId=" + (doctor != null ? doctor.getId() : null) +
                ", patientId=" + (patient != null ? patient.getId() : null) +
                '}';
    }
}
