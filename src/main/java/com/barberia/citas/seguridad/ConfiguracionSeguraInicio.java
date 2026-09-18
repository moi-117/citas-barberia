package com.barberia.citas.seguridad;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfiguracionSeguraInicio implements ApplicationRunner {
    private final Environment environment;

    public ConfiguracionSeguraInicio(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validarOrígenes();
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            validarProduccion();
        }
    }

    private void validarProduccion() {
        String usuario = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        if (usuario.isBlank() || "root".equalsIgnoreCase(usuario)) {
            throw new IllegalStateException("En producción DB_USERNAME debe ser un usuario dedicado y no root");
        }
        if (password.isBlank() || "123456".equals(password) || "CAMBIA_ESTA_CLAVE".equals(password)) {
            throw new IllegalStateException("En producción DB_PASSWORD debe ser un secreto externo seguro");
        }
    }

    private void validarOrígenes() {
        String configurados = environment.getProperty("app.security.allowed-origins", "");
        Arrays.stream(configurados.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isBlank())
                .forEach(origen -> {
                    URI uri;
                    try {
                        uri = URI.create(origen);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalStateException("ALLOWED_ORIGINS contiene un origen inválido", ex);
                    }
                    if ("*".equals(origen) || uri.getHost() == null
                            || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                            || uri.getPath() != null && !uri.getPath().isEmpty()
                            || uri.getQuery() != null || uri.getFragment() != null) {
                        throw new IllegalStateException("ALLOWED_ORIGINS solo admite orígenes HTTP/HTTPS exactos, sin rutas ni comodines");
                    }
                    if (environment.acceptsProfiles(Profiles.of("prod")) && !"https".equals(uri.getScheme())) {
                        throw new IllegalStateException("En producción ALLOWED_ORIGINS debe usar HTTPS");
                    }
                });
    }
}
