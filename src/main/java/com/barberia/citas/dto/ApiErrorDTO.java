package com.barberia.citas.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiErrorDTO(
        LocalDateTime timestamp,
        int status,
        String mensaje,
        Map<String, String> errores
) {}
