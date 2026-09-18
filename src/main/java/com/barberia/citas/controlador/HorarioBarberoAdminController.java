package com.barberia.citas.controlador;

import com.barberia.citas.dto.HorarioBarberoResponseDTO;
import com.barberia.citas.dto.HorarioSemanalRequestDTO;
import com.barberia.citas.servicio.HorarioBarberoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/barberos/{barberoId}/horarios")
@RequiredArgsConstructor
public class HorarioBarberoAdminController {

    private final HorarioBarberoService horarioBarberoService;

    @GetMapping
    public ResponseEntity<List<HorarioBarberoResponseDTO>> listar(@PathVariable UUID barberoId) {
        return ResponseEntity.ok(horarioBarberoService.listar(barberoId));
    }

    @PutMapping
    public ResponseEntity<List<HorarioBarberoResponseDTO>> guardar(
            @PathVariable UUID barberoId,
            @Valid @RequestBody HorarioSemanalRequestDTO request) {
        return ResponseEntity.ok(horarioBarberoService.guardar(barberoId, request));
    }
}
