package com.barberia.citas.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record HorarioBarberoRequestDTO(
        @NotNull(message = "El día es obligatorio") DayOfWeek diaSemana,
        boolean activo,
        LocalTime horaInicio,
        LocalTime horaFin,
        LocalTime descansoInicio,
        LocalTime descansoFin
) { }
