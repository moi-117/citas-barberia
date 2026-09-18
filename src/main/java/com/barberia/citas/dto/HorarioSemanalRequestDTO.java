package com.barberia.citas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HorarioSemanalRequestDTO(
        @NotNull(message = "El horario semanal es obligatorio")
        @Size(min = 7, max = 7, message = "Debes enviar los siete días de la semana")
        List<@Valid HorarioBarberoRequestDTO> dias
) { }
