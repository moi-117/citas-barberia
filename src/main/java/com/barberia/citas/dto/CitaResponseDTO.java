package com.barberia.citas.dto;

import com.barberia.citas.dominio.enums.EstadoCita;

import java.time.LocalDateTime;
import java.util.UUID;

public record CitaResponseDTO(
        UUID id,
        String nombreUsuario,
        String telefonoUsuario,
        String emailUsuario,
        String nombreServicio,
        String nombreBarbero,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        EstadoCita estado
) {}
