package com.barberia.citas;

import com.barberia.citas.dominio.Servicio;
import com.barberia.citas.dominio.Usuario;
import com.barberia.citas.dominio.enums.RolUsuario;
import com.barberia.citas.repositorio.ServicioRepository;
import com.barberia.citas.repositorio.UsuarioRepository;
import com.barberia.citas.validacion.DatosCliente;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.TimeZone;

@Slf4j
@SpringBootApplication
public class CitasApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
        SpringApplication.run(CitasApplication.class, args);
    }

    @Bean
    CommandLineRunner initData(ServicioRepository servicioRepository,
                               UsuarioRepository usuarioRepository,
                               PasswordEncoder passwordEncoder,
                               Environment environment) {
        return args -> {
            if (servicioRepository.count() == 0) {
                servicioRepository.save(Servicio.builder()
                        .nombre("Corte Clásico")
                        .descripcion("Corte tradicional y perfilado")
                        .precio(new BigDecimal("25000.00"))
                        .duracionMinutos(30)
                        .build());
                servicioRepository.save(Servicio.builder()
                        .nombre("Fade Premium")
                        .descripcion("Degradado, contornos y acabado premium")
                        .precio(new BigDecimal("35000.00"))
                        .duracionMinutos(45)
                        .build());
                servicioRepository.save(Servicio.builder()
                        .nombre("Corte + Barba")
                        .descripcion("Corte completo y perfilado de barba")
                        .precio(new BigDecimal("50000.00"))
                        .duracionMinutos(60)
                        .build());
            }

            boolean adminHabilitado = Boolean.parseBoolean(
                    environment.getProperty("app.bootstrap-admin.enabled", "false")
            );
            if (adminHabilitado) {
                String adminEmail = environment.getProperty("app.bootstrap-admin.email", "")
                        .trim().toLowerCase(Locale.ROOT);
                String adminPassword = environment.getProperty("app.bootstrap-admin.password", "");
                String adminTelefono = DatosCliente.normalizarTelefono(
                        environment.getProperty("app.bootstrap-admin.telefono", "")
                );
                validarAdminInicial(adminEmail, adminPassword, adminTelefono);

                var adminPorEmail = usuarioRepository.findByEmailIgnoreCase(adminEmail);
                var usuarioPorTelefono = usuarioRepository.findByTelefono(adminTelefono);

                if (adminPorEmail.isEmpty() && usuarioPorTelefono.isEmpty()) {
                    usuarioRepository.save(Usuario.builder()
                            .nombre("Administrador RAND")
                            .telefono(adminTelefono)
                            .email(adminEmail)
                            .password(passwordEncoder.encode(adminPassword))
                            .rol(RolUsuario.ADMIN)
                            .build());
                    log.info("Administrador inicial creado para {}", adminEmail);
                } else if (adminPorEmail.isPresent() && adminPorEmail.get().getRol() != RolUsuario.ADMIN) {
                    log.warn("El correo configurado como administrador ya pertenece a un usuario CLIENTE. No se modificó su rol automáticamente.");
                } else if (adminPorEmail.isEmpty() && usuarioPorTelefono.isPresent()) {
                    log.warn("El teléfono configurado para el administrador ya está en uso. No se creó un administrador automático.");
                } else if (adminPorEmail.isPresent()) {
                    Usuario admin = adminPorEmail.get();
                    if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
                        admin.setPassword(passwordEncoder.encode(adminPassword));
                        usuarioRepository.saveAndFlush(admin);
                        log.info("Contraseña del administrador {} rotada mediante la configuración de arranque", adminEmail);
                    }
                }
            }
        };
    }

    private void validarAdminInicial(String email, String password, String telefono) {
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalStateException("APP_ADMIN_EMAIL es obligatorio y debe ser un correo válido cuando BOOTSTRAP_ADMIN=true");
        }
        if (password.length() < 12 || !DatosCliente.passwordDentroDelLimite(password)
                || "RandAdmin2026*".equals(password) || "CAMBIA_ESTA_CLAVE".equals(password)) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD debe tener entre 12 y 72 bytes y no puede ser una clave de ejemplo");
        }
        if (!DatosCliente.telefonoValido(telefono)) {
            throw new IllegalStateException("APP_ADMIN_TELEFONO es obligatorio y debe contener entre 7 y 20 dígitos");
        }
    }
}
