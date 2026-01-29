package com.example.therapify.model;

import com.example.therapify.enums.UserType;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "usuarios")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // firstName
    @Column(nullable = false)
    private String nombre;

    // lastName
    @Column(nullable = false)
    private String apellido;

    @Column(unique = true, nullable = false)
    private String email;

    private String companyName;
    private String gender;
    private String address;

    @Column(nullable = false)
    private String password;

    // No se guarda en la BD, solo para validación
    @Transient
    private String confirmPassword;

    // userType (enum) — CAMBIADO
    @Enumerated(EnumType.STRING)
    private UserType userType;

    // schedule {monday: true, ...}
    @Column(columnDefinition = "TEXT")
    private String schedule;

    // availability {monday:["8:00", ...], ...}
    @Column(columnDefinition = "TEXT")
    private String availability;

    private String description;

    // -------- Relaciones --------

    @OneToMany(mappedBy = "doctor")
    private List<Appointment> turnosComoDoctor;

    @OneToMany(mappedBy = "patient")
    private List<Appointment> turnosComoPaciente;

    @OneToMany(mappedBy = "doctor")
    private List<Review> resenasRecibidas;

    @OneToMany(mappedBy = "patient")
    private List<Review> resenasCreadas;

    // -------- Getters & Setters --------


    public Long getId() { return id; }

    public String getFirstName() { return nombre; }
    public void setFirstName(String nombre) { this.nombre = nombre; }

    public String getLastName() { return apellido; }
    public void setLastName(String apellido) { this.apellido = apellido; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UserType getUserType() { return userType; }
    public void setUserType(UserType userType) { this.userType = userType; }

    public List<Appointment> getTurnosComoDoctor() { return turnosComoDoctor; }
    public void setTurnosComoDoctor(List<Appointment> turnosComoDoctor) { this.turnosComoDoctor = turnosComoDoctor; }

    public List<Appointment> getTurnosComoPaciente() { return turnosComoPaciente; }
    public void setTurnosComoPaciente(List<Appointment> turnosComoPaciente) { this.turnosComoPaciente = turnosComoPaciente; }

    public List<Review> getResenasRecibidas() { return resenasRecibidas; }
    public void setResenasRecibidas(List<Review> resenasRecibidas) { this.resenasRecibidas = resenasRecibidas; }

    public List<Review> getResenasCreadas() { return resenasCreadas; }
    public void setResenasCreadas(List<Review> resenasCreadas) { this.resenasCreadas = resenasCreadas; }

    // -------- UserDetails --------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(userType.name()));
    }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
