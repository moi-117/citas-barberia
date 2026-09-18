package com.barberia.citas.dto;

import com.barberia.citas.dominio.enums.RolUsuario;
import java.util.UUID;

public record AuthResponseDTO(
        UUID id,
        String nombre,
        String email,
        RolUsuario rol
) {}
