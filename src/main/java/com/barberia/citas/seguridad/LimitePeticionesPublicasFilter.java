package com.barberia.citas.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LimitePeticionesPublicasFilter extends OncePerRequestFilter {
    private final Clock reloj;
    private final int maximo;
    private final Duration ventana;
    private final Map<String, Consumo> consumos = new ConcurrentHashMap<>();

    public LimitePeticionesPublicasFilter(Clock reloj,
            @Value("${app.security.public-write.max-requests:30}") int maximo,
            @Value("${app.security.public-write.window-minutes:1}") long minutos) {
        this.reloj = reloj;
        this.maximo = maximo;
        this.ventana = Duration.ofMinutes(minutos);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return true;
        String ruta = request.getRequestURI();
        return !("/api/citas".equals(ruta) || "/api/auth/registro".equals(ruta));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Instant ahora = reloj.instant();
        String clave = request.getRemoteAddr() + '|' + request.getRequestURI();
        Consumo consumo = consumos.compute(clave, (ignorada, anterior) ->
                anterior == null || !ahora.isBefore(anterior.expira())
                        ? new Consumo(1, ahora.plus(ventana))
                        : new Consumo(anterior.cantidad() + 1, anterior.expira()));

        if (consumo.cantidad() > maximo) {
            long espera = Math.max(1, Duration.between(ahora, consumo.expira()).toSeconds());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(espera));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"mensaje\":\"Demasiadas solicitudes. Espera antes de intentar nuevamente.\",\"errores\":{}}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record Consumo(int cantidad, Instant expira) { }
}
