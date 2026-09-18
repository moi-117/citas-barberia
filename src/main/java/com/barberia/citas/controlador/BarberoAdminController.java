package com.barberia.citas.controlador;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.dto.BarberoEstadoRequestDTO;
import com.barberia.citas.dto.BarberoRequestDTO;
import com.barberia.citas.excepcion.OperacionNoPermitidaException;
import com.barberia.citas.excepcion.RecursoNoEncontradoException;
import com.barberia.citas.repositorio.BarberoRepository;
import com.barberia.citas.repositorio.CitaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/barberos")
@RequiredArgsConstructor
public class BarberoAdminController {

    private final BarberoRepository barberoRepository;
    private final CitaRepository citaRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Barbero>> listarTodos() {
        return ResponseEntity.ok(barberoRepository.findAllByOrderByNombreAsc());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Barbero> crear(@Valid @RequestBody BarberoRequestDTO request) {
        String nombre = request.nombre().trim();
        if (barberoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new OperacionNoPermitidaException("Ya existe un barbero con ese nombre.");
        }

        Barbero barbero = Barbero.builder()
                .nombre(nombre)
                .especialidad(request.especialidad().trim())
                .fotoUrl(normalizarFoto(request.fotoUrl()))
                .activo(true)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(barberoRepository.save(barbero));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Barbero> editar(@PathVariable UUID id,
                                           @Valid @RequestBody BarberoRequestDTO request) {
        Barbero barbero = obtener(id);
        String nombre = request.nombre().trim();
        if (barberoRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new OperacionNoPermitidaException("Ya existe otro barbero con ese nombre.");
        }

        barbero.setNombre(nombre);
        barbero.setEspecialidad(request.especialidad().trim());
        barbero.setFotoUrl(normalizarFoto(request.fotoUrl()));
        return ResponseEntity.ok(barberoRepository.save(barbero));
    }

    @PatchMapping("/{id}/estado")
    @Transactional
    public ResponseEntity<Barbero> cambiarEstado(@PathVariable UUID id,
                                                  @Valid @RequestBody BarberoEstadoRequestDTO request) {
        Barbero barbero = obtener(id);
        barbero.setActivo(request.activo());
        return ResponseEntity.ok(barberoRepository.save(barbero));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        Barbero barbero = obtener(id);
        if (citaRepository.existsByBarberoId(id)) {
            throw new OperacionNoPermitidaException(
                    "Este barbero tiene citas asociadas. Desactívalo para conservar el historial."
            );
        }
        barberoRepository.delete(barbero);
        return ResponseEntity.noContent().build();
    }

    private Barbero obtener(UUID id) {
        return barberoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Barbero no encontrado"));
    }

    private String normalizarFoto(String fotoUrl) {
        if (fotoUrl == null || fotoUrl.isBlank()) {
            return null;
        }
        return fotoUrl.trim();
    }
}
