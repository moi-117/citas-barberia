package com.barberia.citas.excepcion;

public class ConflictoDatosClienteException extends RuntimeException {
    public ConflictoDatosClienteException(String message) {
        super(message);
    }
}
