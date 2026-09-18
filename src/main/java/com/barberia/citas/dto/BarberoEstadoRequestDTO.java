package com.barberia.citas.dto;

import jakarta.validation.constraints.NotNull;

public record BarberoEstadoRequestDTO(
        @NotNull(message = "Debes indicar si el barbero está activo")
        Boolean activo
) {
}
