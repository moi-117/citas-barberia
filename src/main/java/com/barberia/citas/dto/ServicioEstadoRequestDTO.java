package com.barberia.citas.dto;

import jakarta.validation.constraints.NotNull;

public record ServicioEstadoRequestDTO(
        @NotNull(message = "El estado es obligatorio") Boolean activo
) { }
