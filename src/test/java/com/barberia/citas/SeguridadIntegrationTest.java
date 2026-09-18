package com.barberia.citas;

import com.barberia.citas.dominio.Usuario;
import com.barberia.citas.dominio.enums.RolUsuario;
import com.barberia.citas.repositorio.UsuarioRepository;
import com.barberia.citas.seguridad.ControlIntentosLogin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.allowed-origins=https://frontend.example")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper json;
    @Autowired ControlIntentosLogin controlIntentosLogin;

    @BeforeEach
    void prepararSeguridad() {
        usuarios.findByEmailIgnoreCase("seguridad-admin@example.com").ifPresent(usuarios::delete);
        usuarios.findByEmailIgnoreCase("seguridad-cliente@example.com").ifPresent(usuarios::delete);
        usuarios.findByEmailIgnoreCase("registro-seguro@example.com").ifPresent(usuarios::delete);
        controlIntentosLogin.limpiar();
    }

    @Test
    void serviciosYBarberosSonPublicos() throws Exception {
        mockMvc.perform(get("/api/citas/servicios"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/citas/barberos"))
                .andExpect(status().isOk());
    }

    @Test
    void recursosAudiovisualesPublicosSeSirvenSinAutenticacion() throws Exception {
        mockMvc.perform(get("/media/rand-hero.mp4"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("video/mp4"));
    }

    @Test
    void consultaDeUnaCitaPorTokenEsPublicaSinExponerAgenda() throws Exception {
        mockMvc.perform(get("/api/citas/mis/token-inexistente"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listadoAdministrativoNoEsPublico() throws Exception {
        mockMvc.perform(get("/api/citas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNoPuedeEntrarAlApiAdministrativo() throws Exception {
        mockMvc.perform(get("/api/citas").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPuedeEntrarAlApiAdministrativo() throws Exception {
        mockMvc.perform(get("/api/citas").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }
    @Test
    void administracionDeBarberosNoEsPublica() throws Exception {
        mockMvc.perform(get("/api/admin/barberos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRealCargaRolDesdeBaseYProtegeLaSesion() throws Exception {
        guardarUsuario("seguridad-admin@example.com", "3110000001", "ClaveAdministrativa2026!", RolUsuario.ADMIN);
        var login = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "SEGURIDAD-ADMIN@example.com",
                                "password", "ClaveAdministrativa2026!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/citas").session(sesion))
                .andExpect(status().isOk());
    }

    @Test
    void clienteAutenticadoNoAdquierePermisosAdministrativos() throws Exception {
        guardarUsuario("seguridad-cliente@example.com", "3110000002", "FraseSeguraCliente2026", RolUsuario.CLIENTE);
        var login = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "seguridad-cliente@example.com", "password", "FraseSeguraCliente2026"))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/citas").session(sesion))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje").value("Acceso denegado"));
    }

    @Test
    void loginSeBloqueaTemporalmenteSinRevelarSiLaCuentaExiste() throws Exception {
        guardarUsuario("seguridad-admin@example.com", "3110000001", "ClaveAdministrativa2026!", RolUsuario.ADMIN);
        for (int intento = 1; intento <= 4; intento++) {
            login("seguridad-admin@example.com", "incorrecta")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value("Credenciales incorrectas"));
        }
        login("seguridad-admin@example.com", "incorrecta")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        login("seguridad-admin@example.com", "ClaveAdministrativa2026!")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void csrfCorsYCabecerasSeAplicanSinAbrirElApi() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"UnaClaveSegura123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://frontend.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://atacante.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")));
    }

    @Test
    void registroExigePasswordLargoFuerzaRolClienteYNoEnumeraIdentidades() throws Exception {
        Map<String, Object> corto = Map.of("nombre", "Registro Seguro", "email", "registro-seguro@example.com",
                "telefono", "3110000003", "password", "Corta123");
        mockMvc.perform(post("/api/auth/registro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(corto)))
                .andExpect(status().isBadRequest());

        Map<String, Object> valido = Map.of("nombre", "Registro Seguro", "email", "registro-seguro@example.com",
                "telefono", "3110000003", "password", "FraseSeguraRegistro2026", "rol", "ADMIN");
        mockMvc.perform(post("/api/auth/registro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(valido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/auth/registro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(valido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("No se pudo crear la cuenta con los datos indicados."))
                .andExpect(content().string(not(containsString("correo ya"))));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private void guardarUsuario(String email, String telefono, String password, RolUsuario rol) {
        usuarios.saveAndFlush(Usuario.builder()
                .nombre("Usuario Seguridad")
                .email(email)
                .telefono(telefono)
                .password(passwordEncoder.encode(password))
                .rol(rol)
                .build());
    }

}
