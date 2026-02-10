package com.example.therapify.dtos.UserDTOs;
import com.example.therapify.enums.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public class UserRequestDTO {

    @NotBlank(message = "El nombre no puede estar vacío")
    @Size(min = 2, max = 30, message = "El nombre debe tener entre 2 y 30 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido no puede estar vacío")
    @Size(min = 2, max = 30, message = "El apellido debe tener entre 2 y 30 caracteres")
    private String lastName;

    @Email(message = "El email debe tener un formato válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 20, message = "La contraseña debe contener entre 6 y 20 caracteres")
    private String password;


    private String companyName;

    @NotNull(message = "El tipo de usuario es obligatorio")
    private UserType userType;

    private String gender;

    private String address;

    Map<String, Boolean> schedule;
    Map<String, List<String>> availability;

    @Size(max = 500, message = "La descripción no puede exceder los 500 caracteres")
    private String description;

    public UserRequestDTO() {}

    // ---------- GETTERS & SETTERS ----------


    public Map<String, Boolean> getSchedule() {
        return schedule;
    }

    public void setSchedule(Map<String, Boolean> schedule) {
        this.schedule = schedule;
    }

    public Map<String, List<String>> getAvailability() {
        return availability;
    }

    public void setAvailability(Map<String, List<String>> availability) {
        this.availability = availability;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
