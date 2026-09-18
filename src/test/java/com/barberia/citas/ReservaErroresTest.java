package com.barberia.citas;

import com.barberia.citas.excepcion.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import static org.assertj.core.api.Assertions.assertThat;

class ReservaErroresTest {
    @Test
    void bloqueoTemporalIndicaQuePuedeReintentarseSinRevelarDetallesInternos() {
        var respuesta = new GlobalExceptionHandler().manejarAgendaOcupada(new CannotAcquireLockException("detalle SQL interno"));
        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(respuesta.getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(respuesta.getBody().toString()).contains("Reintenta").doesNotContain("detalle SQL interno");
    }
}
