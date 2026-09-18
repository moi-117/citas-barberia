package com.barberia.citas;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.dominio.enums.EstadoCita;
import com.barberia.citas.repositorio.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorreccionesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CitaRepository citas;
    @Autowired UsuarioRepository usuarios;
    @Autowired BarberoRepository barberos;
    @Autowired ServicioRepository servicios;
    UUID barberoId;
    LocalDate fecha;

    @BeforeEach
    void preparar() {
        citas.deleteAll();
        usuarios.deleteAll();
        barberos.deleteAll();
        barberoId = barberos.save(Barbero.builder().nombre("QA").especialidad("Corte").activo(true).build()).getId();
        fecha = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2);
        while (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) fecha = fecha.plusDays(1);
    }

    Map<String, Object> reserva(int hora) {
        return new HashMap<>(Map.of("clienteNombre", "Cliente Original", "clienteTelefono", "3105550101",
                "clienteEmail", "original@example.com", "servicioId", servicios.findAll().get(0).getId(),
                "barberoId", barberoId, "fechaHoraInicio", fecha.atTime(hora, 0).toString()));
    }

    ResultActions reservar(Map<String, Object> datos) throws Exception {
        return mvc.perform(post("/api/citas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"clienteNombre", "clienteTelefono", "clienteEmail"})
    void reservaPublicaNoModificaPerfilNiHistorial(String campo) throws Exception {
        reservar(reserva(10)).andExpect(status().isCreated());
        var cita = citas.findAll().get(0);
        cita.setEstado(EstadoCita.COMPLETADA);
        citas.saveAndFlush(cita);
        var otra = reserva(12);
        otra.put(campo, switch (campo) {
            case "clienteTelefono" -> "3105550102";
            case "clienteEmail" -> "otro@example.com";
            default -> "Otra Persona";
        });
        reservar(otra).andExpect(status().isConflict());
        var cliente = usuarios.findByEmailIgnoreCase("original@example.com").orElseThrow();
        assertThat(cliente.getNombre()).isEqualTo("Cliente Original");
        assertThat(cliente.getTelefono()).isEqualTo("3105550101");
        assertThat(citas.count()).isEqualTo(1);
        mvc.perform(get("/api/citas").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$[0].nombreUsuario").value("Cliente Original"))
                .andExpect(jsonPath("$[0].estado").value("COMPLETADA"));
    }

    @Test
    void clientePuedeRepetirReservaConDatosNormalizados() throws Exception {
        reservar(reserva(10)).andExpect(status().isCreated());
        var otra = reserva(12);
        otra.put("clienteTelefono", "+57 310 555 0101");
        otra.put("clienteEmail", "ORIGINAL@example.com");
        reservar(otra).andExpect(status().isCreated());
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(citas.count()).isEqualTo(2);
    }

    ResultActions registrar(String telefono, String password) throws Exception {
        return mvc.perform(post("/api/auth/registro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nombre", "Cuenta QA", "email", "cuenta@example.com",
                        "telefono", telefono, "password", password))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-------", "------1", "( )----"})
    void telefonoSinDigitosSuficientesSeRechazaEnRegistroYReserva(String telefono) throws Exception {
        registrar(telefono, "PruebaQA123!").andExpect(status().isBadRequest());
        var datos = reserva(10);
        datos.put("clienteTelefono", telefono);
        reservar(datos).andExpect(status().isBadRequest());
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void registroNoPuedeApropiarseDeUnInvitado() throws Exception {
        reservar(reserva(10)).andExpect(status().isCreated());
        registrar("3105550101", "PruebaQA123!").andExpect(status().isConflict());
        var original = usuarios.findByEmailIgnoreCase("original@example.com").orElseThrow();
        assertThat(original.getPassword()).isNull();
        assertThat(original.getNombre()).isEqualTo("Cliente Original");
        assertThat(usuarios.findByEmailIgnoreCase("cuenta@example.com")).isEmpty();
    }

    @Test
    void contrasenasUnicodeFueraDelLimiteNoSeTruncan() throws Exception {
        registrar("3105550111", "á".repeat(40)).andExpect(status().isBadRequest());
        assertThat(usuarios.count()).isZero();
        registrar("3105550111", "á".repeat(36)).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "cuenta@example.com", "password", "á".repeat(36)))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "cuenta@example.com", "password", "á".repeat(36) + "BBBB"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordAsciiDe72CaracteresSigueFuncionando() throws Exception {
        registrar("3105550111", "A".repeat(72)).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "cuenta@example.com", "password", "A".repeat(72)))))
                .andExpect(status().isOk());
    }

    @Test
    void precioFueraDeColumnaSeRechazaAntesDePersistir() throws Exception {
        long antes = servicios.count();
        mvc.perform(post("/api/citas/servicios").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Precio fuera\",\"precio\":100000000,\"duracionMinutos\":30}"))
                .andExpect(status().isBadRequest());
        assertThat(servicios.count()).isEqualTo(antes);
        mvc.perform(post("/api/citas/servicios").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Precio limite\",\"precio\":99999999,\"duracionMinutos\":30}"))
                .andExpect(status().isCreated());
    }

    @Test
    void metodoNoPermitidoDevuelve405YAllow() throws Exception {
        mvc.perform(put("/api/citas").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")));
    }

    @Test
    void telefonoCortoPermiteConfirmarYCancelarCita() throws Exception {
        var datos = reserva(10);
        datos.put("clienteTelefono", "5550199");
        reservar(datos).andExpect(status().isCreated());
        var id = citas.findAll().get(0).getId();
        mvc.perform(patch("/api/citas/{id}/confirmar", id).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mvc.perform(patch("/api/citas/{id}/cancelar", id).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
    }
}
