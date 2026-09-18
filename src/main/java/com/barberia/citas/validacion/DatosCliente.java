package com.barberia.citas.validacion;

import java.nio.charset.StandardCharsets;

public final class DatosCliente {
    private DatosCliente() {}

    public static String normalizarTelefono(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("\\D", "");
        if (digitos.length() == 12 && digitos.startsWith("57")) {
            return digitos.substring(2);
        }
        return digitos;
    }

    public static boolean telefonoValido(String telefono) {
        int longitud = normalizarTelefono(telefono).length();
        return longitud >= 7 && longitud <= 20;
    }

    public static boolean passwordDentroDelLimite(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
