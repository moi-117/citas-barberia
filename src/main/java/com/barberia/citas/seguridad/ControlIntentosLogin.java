package com.barberia.citas.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ControlIntentosLogin {
    private final Clock reloj;
    private final int maximoIntentos;
    private final Duration ventana;
    private final Map<String, Intentos> intentos = new ConcurrentHashMap<>();

    public ControlIntentosLogin(Clock reloj,
            @Value("${app.security.login.max-attempts:5}") int maximoIntentos,
            @Value("${app.security.login.window-minutes:15}") long minutosVentana) {
        this.reloj = reloj;
        this.maximoIntentos = maximoIntentos;
        this.ventana = Duration.ofMinutes(minutosVentana);
    }

    public synchronized Duration bloqueoRestante(String clave) {
        Instant ahora = reloj.instant();
        Intentos estado = intentos.get(clave);
        if (estado == null) return Duration.ZERO;
        if (!ahora.isBefore(estado.expira())) {
            intentos.remove(clave);
            return Duration.ZERO;
        }
        return estado.cantidad() >= maximoIntentos
                ? Duration.between(ahora, estado.expira())
                : Duration.ZERO;
    }

    public synchronized Duration registrarFallo(String clave) {
        Instant ahora = reloj.instant();
        Intentos anterior = intentos.get(clave);
        Intentos siguiente = anterior == null || !ahora.isBefore(anterior.expira())
                ? new Intentos(1, ahora.plus(ventana))
                : new Intentos(anterior.cantidad() + 1, anterior.expira());
        intentos.put(clave, siguiente);
        return siguiente.cantidad() >= maximoIntentos
                ? Duration.between(ahora, siguiente.expira())
                : Duration.ZERO;
    }

    public void registrarExito(String clave) {
        intentos.remove(clave);
    }

    public void limpiar() {
        intentos.clear();
    }

    private record Intentos(int cantidad, Instant expira) { }
}
