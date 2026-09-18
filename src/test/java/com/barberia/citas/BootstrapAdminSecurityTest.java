package com.barberia.citas;

import com.barberia.citas.dominio.Usuario;
import com.barberia.citas.dominio.enums.RolUsuario;
import com.barberia.citas.repositorio.ServicioRepository;
import com.barberia.citas.repositorio.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BootstrapAdminSecurityTest {
    @Test
    void bootstrapExplicitoPuedeRotarPasswordSinCambiarIdentidadNiRol() throws Exception {
        ServicioRepository servicios = mock(ServicioRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Environment entorno = mock(Environment.class);
        Usuario admin = Usuario.builder().nombre("Admin").email("admin@example.com")
                .telefono("3000000000").password("hash-anterior").rol(RolUsuario.ADMIN).build();

        when(servicios.count()).thenReturn(1L);
        when(entorno.getProperty("app.bootstrap-admin.enabled", "false")).thenReturn("true");
        when(entorno.getProperty("app.bootstrap-admin.email", "")).thenReturn("admin@example.com");
        when(entorno.getProperty("app.bootstrap-admin.password", "")).thenReturn("NuevaClaveSegura2026!");
        when(entorno.getProperty("app.bootstrap-admin.telefono", "")).thenReturn("3000000000");
        when(usuarios.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(usuarios.findByTelefono("3000000000")).thenReturn(Optional.of(admin));
        when(encoder.matches("NuevaClaveSegura2026!", "hash-anterior")).thenReturn(false);
        when(encoder.encode("NuevaClaveSegura2026!")).thenReturn("hash-nuevo");

        new CitasApplication().initData(servicios, usuarios, encoder, entorno).run();

        assertThat(admin.getPassword()).isEqualTo("hash-nuevo");
        assertThat(admin.getRol()).isEqualTo(RolUsuario.ADMIN);
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        verify(usuarios).saveAndFlush(admin);
    }
}
