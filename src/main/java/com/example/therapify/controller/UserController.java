package com.example.therapify.controller;

import com.example.therapify.dtos.UserDTOs.UserDetailDTO;
import com.example.therapify.dtos.UserDTOs.UserRequestDTO;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.User;
import com.example.therapify.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    @Autowired
    private UserService userService;

    // ==========================
    // SOLO ADMIN
    // ==========================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UserDetailDTO> listar() {
        return userService.listarUsuarios();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable Long id) {
        return userService.eliminarUsuario(id);
    }

    // ==========================
    // PUBLICO / AUTENTICADO
    // ==========================

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody UserRequestDTO req) {
        userService.crearUsuario(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message",
                        "Te enviamos un email para confirmar tu cuenta"
                ));
    }

    @GetMapping("/{id}")
    public UserDetailDTO buscarPorId(@PathVariable Long id) {
        return userService.buscarPorId(id);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> modificarMiPerfil(
            @RequestBody UserRequestDTO req
    ) {
        return userService.modificarMiUsuario(req);
    }

    // Endpoint opcional: obtener perfil propio
    @GetMapping("/mi-perfil")
    public UserDetailDTO miPerfil() {
        User u = userService.getAuthenticatedUser();
        return userService.buscarPorId(u.getId());
    }

    // ==========================
    // BUSCAR POR ROL
    // ==========================

    @GetMapping("/rol/{tipo}")
    public ResponseEntity<List<UserDetailDTO>> findByTipoDeUsuario(
            @PathVariable UserType tipo
    ) {
        return ResponseEntity.ok(
                userService.findByUserType(tipo.name())
        );
    }

    // ==========================
    // BUSQUEDAS POR NOMBRE / APELLIDO
    // ==========================

    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<List<UserDetailDTO>> findByNombre(
            @PathVariable String nombre
    ) {
        return ResponseEntity.ok(
                userService.findByFirstName(nombre)
        );
    }

    @GetMapping("/apellido/{apellido}")
    public ResponseEntity<List<UserDetailDTO>> findByApellido(
            @PathVariable String apellido
    ) {
        return ResponseEntity.ok(
                userService.findByLastName(apellido)
        );
    }

    @GetMapping("/doctors/near")
    public ResponseEntity<List<UserDetailDTO>> doctorsNear(
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        return ResponseEntity.ok(
                userService.findDoctorsNear(lat, lng)
        );
    }



}
