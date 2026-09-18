package com.barberia.citas;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.repositorio.BarberoRepository;
import com.barberia.citas.repositorio.CitaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BarberoAdminIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BarberoRepository barberoRepository;
    @Autowired CitaRepository citaRepository;

    @BeforeEach
    void preparar() {
        citaRepository.deleteAll();
        barberoRepository.deleteAll();
    }

    @Test
    void sitioPublicoSoloMuestraBarberosActivos() throws Exception {
        barberoRepository.save(Barbero.builder()
                .nombre("Activo")
                .especialidad("Fade")
                .activo(true)
                .build());
        barberoRepository.save(Barbero.builder()
                .nombre("Inactivo")
                .especialidad("Barba")
                .activo(false)
                .build());

        mockMvc.perform(get("/api/citas/barberos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Activo"));
    }

    @Test
    void clienteNoPuedeAdministrarBarberos() throws Exception {
        mockMvc.perform(get("/api/admin/barberos")
                        .with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPuedeCrearEditarYDesactivarBarbero() throws Exception {
        Map<String, Object> crear = Map.of(
                "nombre", "Mateo",
                "especialidad", "Fade y textura",
                "fotoUrl", "https://example.com/mateo.jpg"
        );

        String body = mockMvc.perform(post("/api/admin/barberos")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crear)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();

        Barbero creado = objectMapper.readValue(body, Barbero.class);

        Map<String, Object> editar = Map.of(
                "nombre", "Mateo RAND",
                "especialidad", "Fade premium",
                "fotoUrl", "https://example.com/mateo2.jpg"
        );

        mockMvc.perform(put("/api/admin/barberos/{id}", creado.getId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Mateo RAND"));

        mockMvc.perform(patch("/api/admin/barberos/{id}/estado", creado.getId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        assertThat(barberoRepository.findById(creado.getId()).orElseThrow().isActivo()).isFalse();
    }
}
