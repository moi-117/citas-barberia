package com.barberia.citas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ServicioRequestDTO(
        @NotBlank(message = "El nombre del servicio es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @Size(max = 300, message = "La descripción no puede superar 300 caracteres")
        String descripcion,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "1", message = "El precio debe ser mayor que cero")
        @Digits(integer = 8, fraction = 0, message = "El precio debe ser un entero entre 1 y 99.999.999 COP")
        BigDecimal precio,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 5, message = "La duración mínima es 5 minutos")
        @Max(value = 240, message = "La duración máxima es 240 minutos")
        Integer duracionMinutos
) {
    @AssertTrue(message = "La duración debe estar en intervalos de 5 minutos")
    public boolean isDuracionEnIntervalosValidos() {
        return duracionMinutos == null || duracionMinutos % 5 == 0;
    }
}
