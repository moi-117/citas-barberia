package com.barberia.citas;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.dominio.Servicio;
import com.barberia.citas.repositorio.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservaIntegridadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CitaRepository citas;
    @Autowired UsuarioRepository usuarios;
    @Autowired BarberoRepository barberos;
    @Autowired ServicioRepository servicios;
    @MockBean Clock relojReservas;
    UUID barberoId;
    Long servicioId;

    @BeforeEach
    void preparar() {
        when(relojReservas.getZone()).thenReturn(ZoneId.of("America/Bogota"));
        when(relojReservas.instant()).thenReturn(Instant.parse("2030-01-07T12:00:00Z")); // lunes 07:00 Bogotá
        citas.deleteAll();
        usuarios.deleteAll();
        barberos.deleteAll();
        barberoId = barberos.save(Barbero.builder().nombre("Agenda QA").especialidad("Corte").activo(true).build()).getId();
        servicioId = servicios.save(Servicio.builder().nombre("Servicio QA")
                .precio(BigDecimal.valueOf(30000)).duracionMinutos(60).build()).getId();
    }

    Map<String, Object> datos(String inicio, int cliente) {
        return new HashMap<>(Map.of("clienteNombre", "Cliente " + cliente,
                "clienteTelefono", "310555" + String.format("%04d", cliente),
                "clienteEmail", "reserva" + cliente + "@example.com", "servicioId", servicioId,
                "barberoId", barberoId, "fechaHoraInicio", inicio));
    }

    MvcResult reservar(Map<String, Object> datos, String clave) throws Exception {
        var peticion = post("/api/citas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos));
        if (clave != null) peticion.header("Idempotency-Key", clave);
        return mvc.perform(peticion).andReturn();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-07T06:00:00", "2030-01-07T07:45:00", "2030-01-07T19:15:00",
            "2030-01-07T20:00:00", "2030-01-13T10:00:00", "2030-01-07T10:07:00",
            "2030-01-07T10:00:01", "2030-01-07T10:00:00.001"})
    void rechazaPasadoFueraDeJornadaDomingoYMallaManipulada(String inicio) throws Exception {
        assertThat(reservar(datos(inicio, 1), null).getResponse().getStatus()).isEqualTo(409);
        assertThat(citas.count()).isZero();
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void validaHoraBogotaInclusoSiElServidorEstaEnOtraZona() throws Exception {
        Instant ahora = Instant.parse("2030-01-07T22:30:00Z");
        when(relojReservas.instant()).thenReturn(ahora);
        try (var factory = jakarta.validation.Validation.byDefaultProvider().configure()
                .clockProvider(() -> Clock.fixed(ahora, ZoneId.of("Pacific/Auckland"))).buildValidatorFactory()) {
            var dto = new com.barberia.citas.dto.CitaRequestDTO("Cliente 1", "3105550001", "reserva1@example.com",
                    servicioId, barberoId, LocalDateTime.parse("2030-01-07T18:00:00"));
            assertThat(factory.getValidator().validate(dto)).isEmpty();
        }
        assertThat(reservar(datos("2030-01-07T17:15:00", 1), null).getResponse().getStatus()).isEqualTo(409);
        assertThat(reservar(datos("2030-01-07T18:00:00", 1), null).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void disponibilidadIncluyeBloqueosImportadosQueComienzanElDiaAnterior() throws Exception {
        reservar(datos("2030-01-07T19:00:00", 1), null);
        var cita = citas.findAll().get(0);
        cita.setFechaHoraInicio(LocalDateTime.parse("2030-01-07T23:30:00"));
        cita.setFechaHoraFin(LocalDateTime.parse("2030-01-08T08:30:00"));
        citas.saveAndFlush(cita);
        mvc.perform(get("/api/citas/disponibilidad").param("barberoId", barberoId.toString())
                .param("servicioId", servicioId.toString()).param("fecha", "2030-01-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("08:00"))))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("08:30")));
        assertThat(reservar(datos("2030-01-08T08:00:00", 2), null).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void respetaDuracionSolapamientosYPermiteCitasAdyacentes() throws Exception {
        assertThat(reservar(datos("2030-01-07T10:00:00", 1), null).getResponse().getStatus()).isEqualTo(201);
        assertThat(citas.findAll().get(0).getFechaHoraFin()).isEqualTo(LocalDateTime.parse("2030-01-07T11:00:00"));
        for (String hora : List.of("09:15", "10:00", "10:30", "10:45")) {
            assertThat(reservar(datos("2030-01-07T" + hora + ":00", 2), null).getResponse().getStatus()).isEqualTo(409);
        }
        assertThat(reservar(datos("2030-01-07T09:00:00", 2), null).getResponse().getStatus()).isEqualTo(201);
        assertThat(reservar(datos("2030-01-07T11:00:00", 3), null).getResponse().getStatus()).isEqualTo(201);
        assertThat(reservar(datos("2030-01-07T19:00:00", 4), null).getResponse().getStatus()).isEqualTo(201);
        mvc.perform(get("/api/citas/disponibilidad").param("barberoId", barberoId.toString())
                .param("servicioId", servicioId.toString()).param("fecha", "2030-01-07"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("10:30"))))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("12:00")));
    }

    @Test
    void cancelaYLiberaHorarioSinResucitarReservaAlReintentar() throws Exception {
        String clave = UUID.randomUUID().toString();
        var peticion = datos("2030-01-07T10:00:00", 1);
        var original = reservar(peticion, clave);
        assertThat(original.getResponse().getStatus()).isEqualTo(201);
        var id = citas.findAll().get(0).getId();
        mvc.perform(patch("/api/citas/{id}/cancelar", id).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(reservar(datos("2030-01-07T10:00:00", 2), null).getResponse().getStatus()).isEqualTo(201);
        var repetida = reservar(peticion, clave);
        assertThat(json.readTree(repetida.getResponse().getContentAsString()).get("estado").asText()).isEqualTo("CANCELADA");
        assertThat(citas.count()).isEqualTo(2);
    }

    @Test
    void rechazaBarberoInactivoYServicioConDuracionCorrupta() throws Exception {
        var barbero = barberos.findById(barberoId).orElseThrow();
        barbero.setActivo(false);
        barberos.saveAndFlush(barbero);
        assertThat(reservar(datos("2030-01-07T10:00:00", 1), null).getResponse().getStatus()).isEqualTo(409);
        mvc.perform(get("/api/citas/disponibilidad").param("barberoId", barberoId.toString())
                .param("servicioId", servicioId.toString()).param("fecha", "2030-01-07"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        barbero.setActivo(true);
        barberos.saveAndFlush(barbero);
        var servicio = servicios.findById(servicioId).orElseThrow();
        servicio.setDuracionMinutos(0);
        servicios.saveAndFlush(servicio);
        assertThat(reservar(datos("2030-01-07T10:00:00", 1), null).getResponse().getStatus()).isEqualTo(409);
        assertThat(citas.count()).isZero();
    }

    @Test
    void validaClaveYFormatoDeFechaSinErroresInternos() throws Exception {
        assertThat(reservar(datos("2030-01-07T10:00:00", 1), "invalida").getResponse().getStatus()).isEqualTo(400);
        assertThat(reservar(datos("+10000-01-07T10:00:00", 1), null).getResponse().getStatus()).isEqualTo(400);
        assertThat(reservar(datos("2030-02-30T10:00:00", 1), null).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void mismaClaveRecuperaReservaPeroNoAceptaDatosDiferentes() throws Exception {
        String clave = UUID.randomUUID().toString();
        var datos = datos("2030-01-07T10:00:00", 1);
        String original = reservar(datos, clave).getResponse().getContentAsString();
        assertThat(reservar(datos, clave).getResponse().getContentAsString()).isEqualTo(original);
        datos.put("fechaHoraInicio", "2030-01-07T12:00:00");
        assertThat(reservar(datos, clave).getResponse().getStatus()).isEqualTo(409);
        assertThat(citas.count()).isEqualTo(1);
    }

    @Test
    void ochoClientesConcurrentesSoloObtienenUnaCita() throws Exception {
        var resultados = concurrentes(false);
        assertThat(resultados.stream().filter(r -> r.getResponse().getStatus() == 201)).hasSize(1);
        assertThat(resultados.stream().filter(r -> r.getResponse().getStatus() == 409)).hasSize(7);
        assertThat(citas.count()).isEqualTo(1);
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void ochoReintentosConcurrentesRecibenLaMismaCita() throws Exception {
        var resultados = concurrentes(true);
        Set<String> tokens = new HashSet<>();
        for (var resultado : resultados) {
            assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
            tokens.add(json.readTree(resultado.getResponse().getContentAsString()).get("consultaToken").asText());
        }
        assertThat(tokens).hasSize(1);
        assertThat(citas.count()).isEqualTo(1);
        assertThat(usuarios.count()).isEqualTo(1);
    }

    List<MvcResult> concurrentes(boolean mismaClave) throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        var preparados = new CountDownLatch(8);
        var salida = new CountDownLatch(1);
        String clave = UUID.randomUUID().toString();
        try {
            List<Future<MvcResult>> tareas = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                int cliente = mismaClave ? 1 : i;
                tareas.add(executor.submit(() -> {
                    preparados.countDown();
                    if (!salida.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Tiempo agotado");
                    return reservar(datos("2030-01-07T10:00:00", cliente), mismaClave ? clave : UUID.randomUUID().toString());
                }));
            }
            assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            List<MvcResult> resultados = new ArrayList<>();
            for (var tarea : tareas) resultados.add(tarea.get(20, TimeUnit.SECONDS));
            return resultados;
        } finally {
            salida.countDown();
            executor.shutdownNow();
        }
    }
}
