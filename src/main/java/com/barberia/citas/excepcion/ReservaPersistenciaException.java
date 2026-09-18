package com.barberia.citas.excepcion;

public class ReservaPersistenciaException extends RuntimeException {
    public ReservaPersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
