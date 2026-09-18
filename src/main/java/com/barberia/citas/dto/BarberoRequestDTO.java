package com.barberia.citas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BarberoRequestDTO(
        @NotBlank(message = "El nombre del barbero es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "La especialidad es obligatoria")
        @Size(max = 160, message = "La especialidad no puede superar 160 caracteres")
        String especialidad,

        @Size(max = 500, message = "La URL de la foto no puede superar 500 caracteres")
        @Pattern(regexp = "^(|https?://\\S+)$", message = "La foto debe ser una URL http o https válida")
        String fotoUrl
) {
}
