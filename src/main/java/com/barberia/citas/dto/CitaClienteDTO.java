package com.barberia.citas.dto;

import com.barberia.citas.dominio.enums.EstadoCita;

import java.time.LocalDateTime;

/**
 * Vista mínima y segura de una cita para el propio cliente.
 * No expone teléfono, correo ni identificadores internos.
 */
public record CitaClienteDTO(
        String consultaToken,
        String nombreServicio,
        String nombreBarbero,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        EstadoCita estado
) {}
