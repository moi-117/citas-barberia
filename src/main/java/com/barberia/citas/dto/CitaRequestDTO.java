package com.barberia.citas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import com.barberia.citas.validacion.DatosCliente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CitaRequestDTO(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String clienteNombre,

        @NotBlank(message = "El teléfono del cliente es obligatorio")
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "Formato de teléfono inválido")
        String clienteTelefono,

        @NotBlank(message = "El correo del cliente es obligatorio")
        @Email(message = "Formato de correo inválido")
        @Size(max = 150, message = "El correo es demasiado largo")
        String clienteEmail,

        @NotNull(message = "El id del servicio es obligatorio")
        Long servicioId,

        @NotNull(message = "El id del barbero es obligatorio")
        UUID barberoId,

        @NotNull(message = "La fecha y hora de inicio es obligatoria")
        LocalDateTime fechaHoraInicio
) {
    @AssertTrue(message = "El teléfono debe contener al menos 7 dígitos")
    public boolean isTelefonoValido() {
        return DatosCliente.telefonoValido(clienteTelefono);
    }
}
