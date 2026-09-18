package com.barberia.citas.excepcion;

import com.barberia.citas.dto.ApiErrorDTO;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ReservaPersistenciaException.class)
    public ResponseEntity<ApiErrorDTO> manejarPersistenciaReserva(ReservaPersistenciaException ex) {
        log.warn("Conflicto de persistencia al guardar una reserva; transacción revertida", ex);
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ReservaInvalidaException.class)
    public ResponseEntity<ApiErrorDTO> manejarReservaInvalida(ReservaInvalidaException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorDTO> manejarAgendaOcupada(org.springframework.dao.PessimisticLockingFailureException ex) {
        log.warn("No se pudo obtener el bloqueo de la agenda", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "2")
                .body(respuesta(HttpStatus.SERVICE_UNAVAILABLE,
                        "La agenda está siendo actualizada. Reintenta la misma reserva en unos segundos.", Map.of()).getBody());
    }

    private static final ZoneId ZONA_BARBERIA = ZoneId.of("America/Bogota");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDTO> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errores.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return respuesta(HttpStatus.BAD_REQUEST, "Revisa los datos enviados", errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorDTO> manejarConstraint(ConstraintViolationException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Datos inválidos", Map.of());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorDTO> manejarPeticionInvalida(Exception ex) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "La solicitud contiene datos con un formato inválido.", Map.of());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiErrorDTO> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler({
            HorarioNoDisponibleException.class,
            OperacionNoPermitidaException.class,
            ConflictoDatosClienteException.class
    })
    public ResponseEntity<ApiErrorDTO> manejarConflicto(RuntimeException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorDTO> manejarBloqueoOptimista(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflicto de concurrencia al actualizar una entidad", ex);
        return respuesta(HttpStatus.CONFLICT,
                "La información cambió mientras realizabas la operación. Actualiza e intenta nuevamente.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDTO> manejarIntegridad(DataIntegrityViolationException ex) {
        // Un fallo de integridad no se disfraza como un conflicto de reserva.
        // Se registra completo en servidor y al navegador solo se envía un mensaje seguro.
        log.error("Violación de integridad de base de datos", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo completar la operación. El equipo de la barbería puede revisar el registro del servidor.",
                Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorDTO> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return new ResponseEntity<>(
                respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido para esta ruta.", Map.of()).getBody(),
                headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDTO> manejarGeneral(Exception ex) {
        log.error("Error no controlado", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Intenta nuevamente.", Map.of());
    }

    private ResponseEntity<ApiErrorDTO> respuesta(HttpStatus status, String mensaje, Map<String, String> errores) {
        return ResponseEntity.status(status).body(new ApiErrorDTO(
                LocalDateTime.now(ZONA_BARBERIA), status.value(), mensaje, errores
        ));
    }
}
