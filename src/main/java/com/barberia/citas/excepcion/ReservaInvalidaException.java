package com.barberia.citas.excepcion;

public class ReservaInvalidaException extends RuntimeException {
    public ReservaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
