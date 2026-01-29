package com.example.therapify.dtos.UserDTOs;
import com.example.therapify.enums.UserType;

public record UserDetailDTO(
        Long id,
        String nombre,
        String apellido,
        String email,
        UserType tipoDeUsuario
) {
}
