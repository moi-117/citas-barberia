package com.barberia.citas.servicio;

import com.barberia.citas.dto.CitaRequestDTO;
import com.barberia.citas.validacion.DatosCliente;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class IdempotenciaReserva {
    private IdempotenciaReserva() { }

    static String normalizarClave(String clave) {
        if (clave == null) return null; // Compatibilidad con clientes anteriores.
        if (!clave.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw new com.barberia.citas.excepcion.ReservaInvalidaException(
                    "La clave de la reserva debe ser un UUID v4 válido.");
        }
        return clave.toLowerCase(Locale.ROOT);
    }

    static String huella(CitaRequestDTO request) {
        var campos = List.of(request.clienteNombre().trim().toLowerCase(Locale.ROOT),
                DatosCliente.normalizarTelefono(request.clienteTelefono()),
                request.clienteEmail().trim().toLowerCase(Locale.ROOT), request.servicioId().toString(),
                request.barberoId().toString(), request.fechaHoraInicio().toString());
        StringBuilder contenido = new StringBuilder();
        campos.forEach(c -> contenido.append(c.length()).append(':').append(c));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contenido.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
