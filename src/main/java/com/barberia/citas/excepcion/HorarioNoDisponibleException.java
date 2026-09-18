// src/main/java/com/barberia/citas/excepcion/HorarioNoDisponibleException.java
package com.barberia.citas.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class HorarioNoDisponibleException extends RuntimeException {

    public HorarioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}