package com.barberia.citas.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record HorarioBarberoResponseDTO(
        DayOfWeek diaSemana,
        boolean activo,
        LocalTime horaInicio,
        LocalTime horaFin,
        LocalTime descansoInicio,
        LocalTime descansoFin
) { }
