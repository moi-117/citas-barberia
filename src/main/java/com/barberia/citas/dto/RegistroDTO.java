package com.barberia.citas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import com.barberia.citas.validacion.DatosCliente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Formato de correo inválido")
        @Size(max = 150, message = "El correo es demasiado largo")
        String email,

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "Formato de teléfono inválido")
        String telefono,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 12, max = 72, message = "La contraseña debe tener entre 12 y 72 caracteres")
        String password
) {
    @AssertTrue(message = "El teléfono debe contener al menos 7 dígitos")
    public boolean isTelefonoValido() {
        return DatosCliente.telefonoValido(telefono);
    }

    @AssertTrue(message = "La contraseña supera el límite de 72 bytes; usa menos caracteres acentuados o símbolos")
    public boolean isPasswordDentroDelLimite() {
        return DatosCliente.passwordDentroDelLimite(password);
    }
}
