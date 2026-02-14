package com.example.therapify.model;

import com.example.therapify.enums.UserType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "usuarios")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(unique = true, nullable = false)
    private String email;

    private String companyName;
    private String gender;
    private String address;

    @Column
    private Double longitude;

    private Double distanceKm;
    @Column(nullable = false)
    private String password;

    @Transient
    private String confirmPassword;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    @Column(columnDefinition = "TEXT")
    private String schedule;

    @Column(columnDefinition = "TEXT")
    private String availability;

    private String description;
    private boolean enabled;

    @OneToMany(mappedBy = "doctor")
    private List<Appointment> turnosComoDoctor;

    @OneToMany(mappedBy = "patient")
    private List<Appointment> turnosComoPaciente;

    @OneToMany(mappedBy = "doctor")
    private List<Review> resenasRecibidas;

    @OneToMany(mappedBy = "patient")
    private List<Review> resenasCreadas;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    @Column
    private Double latitude;

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

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UserType getUserType() { return userType; }
    public void setUserType(UserType userType) { this.userType = userType; }

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

    public Map<String, List<String>> getAvailabilityMap() {
        if (this.availability == null || this.availability.isBlank()) return null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(this.availability, new TypeReference<Map<String, List<String>>>() {});
        } catch (Exception e) {
            return null;
        }
    }



}
