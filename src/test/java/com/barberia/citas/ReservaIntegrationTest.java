package com.barberia.citas;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.dominio.Servicio;
import com.barberia.citas.dominio.Usuario;
import com.barberia.citas.dominio.enums.EstadoCita;
import com.barberia.citas.repositorio.BarberoRepository;
import com.barberia.citas.repositorio.CitaRepository;
import com.barberia.citas.repositorio.ServicioRepository;
import com.barberia.citas.repositorio.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservaIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ServicioRepository servicioRepository;
    @Autowired BarberoRepository barberoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CitaRepository citaRepository;

    @BeforeEach
    void limpiarReservasYClientes() {
        citaRepository.deleteAll();
        usuarioRepository.deleteAll();
        barberoRepository.deleteAll();
        barberoRepository.save(Barbero.builder()
                .nombre("Barbero Prueba")
                .especialidad("Corte clásico")
                .fotoUrl("https://example.com/barbero.jpg")
                .activo(true)
                .build());
    }

    @Test
    void clienteInvitadoPuedeReservarSinPasswordYNoSeDuplicaHorario() throws Exception {
        Servicio servicio = servicioRepository.findAll().get(0);
        Barbero barbero = barberoRepository.findAll().get(0);
        LocalDateTime inicio = proximoDiaLaboral().atTime(LocalTime.of(10, 0));

        Map<String, Object> reserva = Map.of(
                "clienteNombre", "Cliente Prueba",
                "clienteTelefono", "+57 310 555 0101",
                "clienteEmail", "cliente.prueba@example.com",
                "servicioId", servicio.getId(),
                "barberoId", barbero.getId().toString(),
                "fechaHoraInicio", inicio.toString()
        );

        String respuestaReserva = mockMvc.perform(post("/api/citas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserva)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.consultaToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String consultaToken = objectMapper.readTree(respuestaReserva).get("consultaToken").asText();
        assertThat(consultaToken).isNotBlank();

        mockMvc.perform(get("/api/citas/mis/{token}", consultaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.nombreServicio").value(servicio.getNombre()))
                .andExpect(jsonPath("$.telefonoUsuario").doesNotExist())
                .andExpect(jsonPath("$.emailUsuario").doesNotExist());

        Usuario invitado = usuarioRepository.findByTelefono("3105550101").orElseThrow();
        assertThat(invitado.getPassword()).isNull();
        assertThat(invitado.getEmail()).isEqualTo("cliente.prueba@example.com");

        Map<String, Object> segundaReserva = Map.of(
                "clienteNombre", "Otro Cliente",
                "clienteTelefono", "3105550102",
                "clienteEmail", "otro@example.com",
                "servicioId", servicio.getId(),
                "barberoId", barbero.getId().toString(),
                "fechaHoraInicio", inicio.toString()
        );

        mockMvc.perform(post("/api/citas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(segundaReserva)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("horario")));

        assertThat(citaRepository.count()).isEqualTo(1);

        var citaGuardada = citaRepository.findAll().get(0);
        citaGuardada.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.saveAndFlush(citaGuardada);

        mockMvc.perform(get("/api/citas/mis/{token}", consultaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    void rechazaHorarioManipuladoFueraDeLaMallaDeQuinceMinutos() throws Exception {
        Servicio servicio = servicioRepository.findAll().get(0);
        Barbero barbero = barberoRepository.findAll().get(0);
        LocalDateTime inicio = proximoDiaLaboral().atTime(10, 7);

        Map<String, Object> reserva = Map.of(
                "clienteNombre", "Cliente Prueba",
                "clienteTelefono", "3105550199",
                "clienteEmail", "malla@example.com",
                "servicioId", servicio.getId(),
                "barberoId", barbero.getId().toString(),
                "fechaHoraInicio", inicio.toString()
        );

        mockMvc.perform(post("/api/citas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserva)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("horarios disponibles")));
    }

    private LocalDate proximoDiaLaboral() {
        LocalDate fecha = LocalDate.now().plusDays(2);
        while (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            fecha = fecha.plusDays(1);
        }
        return fecha;
    }
}
