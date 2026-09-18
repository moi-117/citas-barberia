package com.barberia.citas.controlador;

import com.barberia.citas.dominio.Servicio;
import com.barberia.citas.dto.ServicioEstadoRequestDTO;
import com.barberia.citas.dto.ServicioRequestDTO;
import com.barberia.citas.servicio.ServicioAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/servicios")
@RequiredArgsConstructor
public class ServicioAdminController {

    private final ServicioAdminService servicioAdminService;

    @GetMapping
    public ResponseEntity<List<Servicio>> listar() {
        return ResponseEntity.ok(servicioAdminService.listar());
    }

    @PostMapping
    public ResponseEntity<Servicio> crear(@Valid @RequestBody ServicioRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioAdminService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Servicio> editar(@PathVariable Long id,
                                            @Valid @RequestBody ServicioRequestDTO request) {
        return ResponseEntity.ok(servicioAdminService.editar(id, request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Servicio> cambiarEstado(@PathVariable Long id,
                                                   @Valid @RequestBody ServicioEstadoRequestDTO request) {
        return ResponseEntity.ok(servicioAdminService.cambiarEstado(id, request.activo()));
    }
}
