package com.barberia.citas;

import com.barberia.citas.dominio.*;
import com.barberia.citas.dominio.enums.EstadoCita;
import com.barberia.citas.dominio.enums.RolUsuario;
import com.barberia.citas.repositorio.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministracionOperativaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ServicioRepository servicios;
    @Autowired BarberoRepository barberos;
    @Autowired HorarioBarberoRepository horarios;
    @Autowired UsuarioRepository usuarios;
    @Autowired CitaRepository citas;

    @BeforeEach
    void limpiar() {
        citas.deleteAll();
        horarios.deleteAll();
        usuarios.deleteAll();
        barberos.deleteAll();
    }

    @AfterEach
    void limpiarDespues() {
        citas.deleteAll();
        horarios.deleteAll();
        usuarios.deleteAll();
        barberos.deleteAll();
        servicios.findAll().stream()
                .filter(s -> s.getNombre().startsWith("Servicio Admin Temporal"))
                .forEach(servicios::delete);
    }

    @Test
    void adminGestionaServiciosYLosInactivosNoSeOfrecen() throws Exception {
        Map<String, Object> datos = Map.of(
                "nombre", "Servicio Admin Temporal",
                "descripcion", "Creado durante una prueba",
                "precio", 42000,
                "duracionMinutos", 45);

        String creado = mvc.perform(post("/api/admin/servicios")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(creado).get("id").asLong();

        Map<String, Object> editado = Map.of(
                "nombre", "Servicio Admin Temporal Editado",
                "descripcion", "Actualizado",
                "precio", 45000,
                "duracionMinutos", 60);
        mvc.perform(put("/api/admin/servicios/{id}", id)
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(editado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMinutos").value(60));

        mvc.perform(patch("/api/admin/servicios/{id}/estado", id)
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));

        mvc.perform(get("/api/citas/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

        Barbero barbero = crearBarbero();
        Servicio inactivo = servicios.findById(id).orElseThrow();
        mvc.perform(post("/api/citas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reserva(
                                inactivo, barbero, proximoDiaLaboral().atTime(10, 0)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("servicio")));
    }

    @Test
    void clienteNoPuedeGestionarServiciosNiHorarios() throws Exception {
        Barbero barbero = crearBarbero();
        mvc.perform(get("/api/admin/servicios").with(user("cliente@example.com").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/barberos/{id}/horarios", barbero.getId())
                        .with(user("cliente@example.com").roles("CLIENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("dias", semanaConDescanso()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void horarioIndividualYDescansoGobiernanDisponibilidadYReserva() throws Exception {
        Barbero barbero = crearBarbero();
        Servicio servicio = servicios.findByActivoTrueOrderByNombreAsc().get(0);

        mvc.perform(put("/api/admin/barberos/{id}/horarios", barbero.getId())
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("dias", semanaConDescanso()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));

        LocalDate fecha = proximoDiaLaboral();
        mvc.perform(get("/api/citas/disponibilidad")
                        .param("barberoId", barbero.getId().toString())
                        .param("servicioId", servicio.getId().toString())
                        .param("fecha", fecha.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@ == '10:00')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@ == '12:00')]").isEmpty());

        Map<String, Object> reservaEnDescanso = reserva(servicio, barbero, fecha.atTime(12, 0));
        mvc.perform(post("/api/citas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reservaEnDescanso)))
                .andExpect(status().isConflict());

        Map<String, Object> reservaValida = reserva(servicio, barbero, fecha.atTime(10, 0));
        mvc.perform(post("/api/citas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reservaValida)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void adminFiltraCitasPorDiaYCambiaEstado() throws Exception {
        Barbero barbero = crearBarbero();
        Servicio servicio = servicios.findByActivoTrueOrderByNombreAsc().get(0);
        Usuario cliente = usuarios.save(Usuario.builder().nombre("Cliente Agenda").telefono("3151234567")
                .email("agenda@example.com").rol(RolUsuario.CLIENTE).build());
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
        Cita citaHoy = guardarCita(cliente, servicio, barbero, hoy.atTime(10, 0));
        guardarCita(cliente, servicio, barbero, hoy.plusDays(1).atTime(10, 0));

        mvc.perform(get("/api/citas").param("fecha", hoy.toString())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(citaHoy.getId().toString()));

        mvc.perform(patch("/api/citas/{id}/confirmar", citaHoy.getId())
                        .with(user("cliente@example.com").roles("CLIENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/citas/{id}/confirmar", citaHoy.getId())
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    private Barbero crearBarbero() {
        return barberos.save(Barbero.builder().nombre("Barbero Administración")
                .especialidad("Corte").activo(true).build());
    }

    private List<Map<String, Object>> semanaConDescanso() {
        List<Map<String, Object>> dias = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("diaSemana", dia.name());
            fila.put("activo", dia != DayOfWeek.SUNDAY);
            fila.put("horaInicio", dia == DayOfWeek.SUNDAY ? null : "09:00");
            fila.put("horaFin", dia == DayOfWeek.SUNDAY ? null : "18:00");
            fila.put("descansoInicio", dia == DayOfWeek.SUNDAY ? null : "12:00");
            fila.put("descansoFin", dia == DayOfWeek.SUNDAY ? null : "13:00");
            dias.add(fila);
        }
        return dias;
    }

    private Map<String, Object> reserva(Servicio servicio, Barbero barbero, LocalDateTime inicio) {
        return Map.of("clienteNombre", "Cliente Horario", "clienteTelefono", "3161234567",
                "clienteEmail", "horario@example.com", "servicioId", servicio.getId(),
                "barberoId", barbero.getId().toString(), "fechaHoraInicio", inicio.toString());
    }

    private Cita guardarCita(Usuario cliente, Servicio servicio, Barbero barbero, LocalDateTime inicio) {
        return citas.save(Cita.builder().usuario(cliente).servicio(servicio).barbero(barbero)
                .fechaHoraInicio(inicio).fechaHoraFin(inicio.plusMinutes(servicio.getDuracionMinutos()))
                .estado(EstadoCita.PENDIENTE).consultaToken(UUID.randomUUID().toString()).build());
    }

    private LocalDate proximoDiaLaboral() {
        LocalDate fecha = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2);
        while (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) fecha = fecha.plusDays(1);
        return fecha;
    }
}
