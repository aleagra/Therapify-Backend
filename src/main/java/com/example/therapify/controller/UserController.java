package com.example.therapify.controller;
import com.example.therapify.dtos.UserDTOs.UserDetailDTO;
import com.example.therapify.dtos.UserDTOs.UserRequestDTO;
import com.example.therapify.enums.UserType;
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
public class UserController {

    @Autowired
    UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UserDetailDTO> listar() {
        return userService.listarUsuarios();
    }

    @GetMapping("/{id}")
    public UserDetailDTO buscarPorId(@PathVariable Long id) {
        return userService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<UserDetailDTO> crear(
            @Valid @RequestBody UserRequestDTO req
    ) {
        UserDetailDTO creado = userService.crearUsuario(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> modificar(
            @Valid @RequestBody UserRequestDTO req
    ) {
        return userService.modificarMiUsuario(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable Long id) {
        return userService.eliminarUsuario(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/rol/{tipo}")
    public ResponseEntity<List<UserDetailDTO>> findByTipoDeUsuario(
            @PathVariable UserType tipo
    ) {
        List<UserDetailDTO> usuarios = userService.findByUserType(String.valueOf(tipo));
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<List<UserDetailDTO>> findByNombre(@PathVariable String nombre) {
        List<UserDetailDTO> usuarios = userService.findByFirstName(nombre);
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/apellido/{apellido}")
    public ResponseEntity<List<UserDetailDTO>> findByApellido(@PathVariable String apellido) {
        List<UserDetailDTO> usuarios = userService.findByLastName(apellido);
        return ResponseEntity.ok(usuarios);
    }

//    @GetMapping("/perfil")
//    public ResponseEntity<UsuarioCompletoDto> obtenerPerfilCompleto() {
//        return ResponseEntity.ok(userService.getUsuarioCompleto());
//    }
}