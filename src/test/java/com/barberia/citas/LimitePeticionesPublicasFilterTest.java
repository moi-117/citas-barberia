package com.barberia.citas;

import com.barberia.citas.seguridad.LimitePeticionesPublicasFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimitePeticionesPublicasFilterTest {
    @Test
    void limitaEscriturasPublicasPorIpSinAfectarLecturas() throws Exception {
        var filtro = new LimitePeticionesPublicasFilter(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC), 2, 1);

        assertThat(ejecutar(filtro, "POST", "/api/citas").getStatus()).isEqualTo(200);
        assertThat(ejecutar(filtro, "POST", "/api/citas").getStatus()).isEqualTo(200);
        var bloqueada = ejecutar(filtro, "POST", "/api/citas");
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("60");
        assertThat(bloqueada.getContentAsString()).doesNotContain("java", "Exception");

        assertThat(ejecutar(filtro, "GET", "/api/citas/disponibilidad").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse ejecutar(LimitePeticionesPublicasFilter filtro,
                                              String metodo, String ruta) throws Exception {
        var request = new MockHttpServletRequest(metodo, ruta);
        request.setRemoteAddr("192.0.2.10");
        var response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
