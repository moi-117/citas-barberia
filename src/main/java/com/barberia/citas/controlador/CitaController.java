package com.barberia.citas.controlador;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.dominio.Servicio;
import com.barberia.citas.dto.CitaClienteDTO;
import com.barberia.citas.dto.CitaRequestDTO;
import com.barberia.citas.dto.CitaResponseDTO;
import com.barberia.citas.dto.ServicioPopularDTO;
import com.barberia.citas.dto.ServicioRequestDTO;
import com.barberia.citas.repositorio.BarberoRepository;
import com.barberia.citas.repositorio.ServicioRepository;
import com.barberia.citas.servicio.CitaService;
import com.barberia.citas.servicio.ServicioAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;
    private final BarberoRepository barberoRepository;
    private final ServicioRepository servicioRepository;
    private final ServicioAdminService servicioAdminService;

    @PostMapping
    public ResponseEntity<CitaClienteDTO> agendarCita(@Valid @RequestBody CitaRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia) {
        return ResponseEntity.status(HttpStatus.CREATED).body(citaService.agendarCita(request, claveIdempotencia));
    }

    @GetMapping("/mis/{token}")
    public ResponseEntity<CitaClienteDTO> consultarMiCita(@PathVariable String token) {
        return ResponseEntity.ok(citaService.consultarCitaPorToken(token));
    }

    @GetMapping("/servicios")
    public ResponseEntity<List<Servicio>> listarServicios() {
        return ResponseEntity.ok(servicioRepository.findByActivoTrueOrderByNombreAsc());
    }

    @GetMapping("/barberos")
    public ResponseEntity<List<Barbero>> obtenerBarberos() {
        return ResponseEntity.ok(barberoRepository.findByActivoTrueOrderByNombreAsc());
    }

    @GetMapping
    public ResponseEntity<List<CitaResponseDTO>> listarCitas(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(fecha == null ? citaService.listarCitas() : citaService.listarCitas(fecha));
    }

    @GetMapping("/disponibilidad")
    public ResponseEntity<List<String>> obtenerDisponibilidad(
            @RequestParam UUID barberoId,
            @RequestParam Long servicioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(citaService.obtenerDisponibilidad(barberoId, fecha, servicioId));
    }

    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<CitaResponseDTO> confirmarCita(@PathVariable UUID id) {
        return ResponseEntity.ok(citaService.confirmarCita(id));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<CitaResponseDTO> cancelarCita(@PathVariable UUID id) {
        return ResponseEntity.ok(citaService.cancelarCita(id));
    }

    @PatchMapping("/{id}/completar")
    public ResponseEntity<CitaResponseDTO> completarCita(@PathVariable UUID id) {
        return ResponseEntity.ok(citaService.completarCita(id));
    }

    /** Compatibilidad con el panel anterior; continúa protegido como ADMIN. */
    @PostMapping("/servicios")
    public ResponseEntity<Servicio> crearServicio(@Valid @RequestBody ServicioRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioAdminService.crear(request));
    }

    @GetMapping("/servicios/populares")
    public ResponseEntity<List<ServicioPopularDTO>> obtenerServiciosPopulares() {
        return ResponseEntity.ok(citaService.obtenerServiciosPopulares());
    }
}
