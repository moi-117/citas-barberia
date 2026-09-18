package com.barberia.citas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import com.barberia.citas.validacion.DatosCliente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Formato de correo inválido")
        @Size(max = 150, message = "El correo es demasiado largo")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
        String password
) {
    @AssertTrue(message = "La contraseña supera el límite de 72 bytes; contacta a la barbería si tu cuenta fue creada con una contraseña más larga")
    public boolean isPasswordDentroDelLimite() {
        return DatosCliente.passwordDentroDelLimite(password);
    }
}
