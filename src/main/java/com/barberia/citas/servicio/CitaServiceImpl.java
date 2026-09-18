package com.barberia.citas.servicio;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.dominio.Cita;
import com.barberia.citas.dominio.Servicio;
import com.barberia.citas.dominio.Usuario;
import com.barberia.citas.dominio.enums.EstadoCita;
import com.barberia.citas.dominio.enums.RolUsuario;
import com.barberia.citas.dto.CitaClienteDTO;
import com.barberia.citas.dto.CitaRequestDTO;
import com.barberia.citas.dto.CitaResponseDTO;
import com.barberia.citas.dto.ServicioPopularDTO;
import com.barberia.citas.excepcion.ConflictoDatosClienteException;
import com.barberia.citas.excepcion.HorarioNoDisponibleException;
import com.barberia.citas.excepcion.OperacionNoPermitidaException;
import com.barberia.citas.excepcion.RecursoNoEncontradoException;
import com.barberia.citas.repositorio.BarberoRepository;
import com.barberia.citas.repositorio.CitaRepository;
import com.barberia.citas.repositorio.ServicioRepository;
import com.barberia.citas.repositorio.UsuarioRepository;
import com.barberia.citas.validacion.DatosCliente;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Clock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CitaServiceImpl implements CitaService {

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final BarberoRepository barberoRepository;
    private final HorarioBarberoService horarioBarberoService;
    private final Clock relojReservas;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public CitaClienteDTO agendarCita(CitaRequestDTO request) {
        return agendarCita(request, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public CitaClienteDTO agendarCita(CitaRequestDTO request, String claveIdempotencia) {
        String clave = IdempotenciaReserva.normalizarClave(claveIdempotencia);
        String huella = clave == null ? null : IdempotenciaReserva.huella(request);

        // Bloqueamos el registro del barbero durante la validación + inserción.
        // Así dos peticiones concurrentes no pueden apropiarse del mismo horario.
        Barbero barbero = barberoRepository.findByIdForUpdate(request.barberoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Barbero no encontrado"));

        // READ_COMMITTED permite ver la reserva del anterior poseedor del bloqueo.
        // También recupera una respuesta perdida incluso si la cita ya pasó o se canceló.
        if (clave != null) {
            var anterior = citaRepository.findByClaveIdempotencia(clave);
            if (anterior.isPresent()) {
                if (!huella.equals(anterior.get().getHuellaReserva())) {
                    throw new OperacionNoPermitidaException("Esta clave ya corresponde a otra reserva. No cambies los datos de un reintento.");
                }
                return mapearCliente(anterior.get());
            }
        }

        Servicio servicio = servicioRepository.findById(request.servicioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));

        if (!barbero.isActivo()) {
            throw new OperacionNoPermitidaException("Este barbero no está disponible para nuevas reservas.");
        }
        if (!servicio.isActivo()) {
            throw new OperacionNoPermitidaException("Este servicio no está disponible para nuevas reservas.");
        }

        LocalDateTime inicio = request.fechaHoraInicio();
        validarFechaSoportada(inicio.toLocalDate());
        LocalDateTime fin = inicio.plusMinutes(validarDuracion(servicio));
        validarMomentoReserva(inicio);
        horarioBarberoService.validarReserva(
                horarioBarberoService.obtener(barbero.getId(), inicio.getDayOfWeek()), inicio, fin);

        boolean hayCruce = citaRepository.existeSolapamiento(
                barbero.getId(), inicio, fin, EstadoCita.CANCELADA
        );
        if (hayCruce) {
            throw new HorarioNoDisponibleException("Ese horario acaba de ser reservado. Elige otro horario disponible.");
        }

        try {
            Usuario cliente = obtenerOCrearCliente(request);
            Cita cita = Cita.builder()
                .usuario(cliente)
                .servicio(servicio)
                .barbero(barbero)
                .fechaHoraInicio(inicio)
                .fechaHoraFin(fin)
                .estado(EstadoCita.PENDIENTE)
                .consultaToken(UUID.randomUUID().toString())
                .claveIdempotencia(clave)
                .huellaReserva(huella)
                .build();

        Cita citaGuardada = citaRepository.saveAndFlush(cita);
        return mapearCliente(citaGuardada);
        } catch (DataIntegrityViolationException ex) {
            // La transacción completa se revierte; no quedan clientes ni citas parciales.
            throw new com.barberia.citas.excepcion.ReservaPersistenciaException(
                    "No se pudo guardar la reserva porque sus datos cambiaron o ya se registraron. Reintenta con los mismos datos; si persiste, contacta a la barbería.", ex);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public CitaClienteDTO consultarCitaPorToken(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            throw new RecursoNoEncontradoException("Cita no encontrada");
        }
        Cita cita = citaRepository.findByConsultaToken(token.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));
        return mapearCliente(cita);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CitaResponseDTO> listarCitas() {
        return citaRepository.findAllConRelaciones().stream()
                .map(this::mapear)
                .sorted(Comparator.comparing(CitaResponseDTO::fechaHoraInicio))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CitaResponseDTO> listarCitas(LocalDate fecha) {
        validarFechaSoportada(fecha);
        return citaRepository.findAllConRelacionesEntre(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                .stream().map(this::mapear).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> obtenerDisponibilidad(UUID barberoId, LocalDate fecha, Long servicioId) {
        validarFechaSoportada(fecha);
        Barbero barbero = barberoRepository.findById(barberoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Barbero no encontrado"));
        if (!barbero.isActivo()) {
            return List.of();
        }

        Servicio servicio = servicioRepository.findById(servicioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));
        if (!servicio.isActivo()) {
            return List.of();
        }

        var horario = horarioBarberoService.obtener(barberoId, fecha.getDayOfWeek());
        if (!horario.activo()) {
            return List.of();
        }

        LocalDate hoy = LocalDate.now(relojReservas);
        if (fecha.isBefore(hoy)) {
            return List.of();
        }

        int duracion = validarDuracion(servicio);
        LocalDateTime inicioDia = fecha.atStartOfDay();
        LocalDateTime finDia = fecha.plusDays(1).atStartOfDay();
        List<Cita> citasDelDia = citaRepository.findCitasDelDia(
                barberoId, inicioDia, finDia, EstadoCita.CANCELADA
        );

        List<String> horasDisponibles = new ArrayList<>();
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("HH:mm");
        LocalDateTime cursor = fecha.atTime(horario.horaInicio());
        LocalDateTime ultimoInicioPosible = fecha.atTime(horario.horaFin()).minusMinutes(duracion);
        LocalDateTime ahora = LocalDateTime.now(relojReservas);

        while (!cursor.isAfter(ultimoInicioPosible)) {
            LocalDateTime finSlot = cursor.plusMinutes(duracion);
            LocalDateTime cursorActual = cursor;
            boolean esPasado = !cursor.isAfter(ahora);
            boolean seSolapa = citasDelDia.stream().anyMatch(c ->
                    cursorActual.isBefore(c.getFechaHoraFin()) && finSlot.isAfter(c.getFechaHoraInicio())
            );
            boolean permitidoPorHorario = horarioBarberoService.acepta(
                    horario, cursor.toLocalTime(), finSlot.toLocalTime());

            if (!esPasado && !seSolapa && permitidoPorHorario) {
                horasDisponibles.add(cursor.toLocalTime().format(formato));
            }
            cursor = cursor.plusMinutes(HorarioBarberoService.INTERVALO_MINUTOS);
        }

        return horasDisponibles;
    }

    @Transactional
    @Override
    public CitaResponseDTO confirmarCita(UUID id) {
        Cita cita = obtenerCita(id);
        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            throw new OperacionNoPermitidaException("Solo se pueden confirmar citas pendientes.");
        }
        cita.setEstado(EstadoCita.CONFIRMADA);
        return mapear(citaRepository.save(cita));
    }

    @Transactional
    @Override
    public CitaResponseDTO cancelarCita(UUID id) {
        Cita cita = obtenerCita(id);
        if (cita.getEstado() == EstadoCita.COMPLETADA) {
            throw new OperacionNoPermitidaException("Una cita completada no se puede cancelar.");
        }
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            return mapear(cita);
        }
        cita.setEstado(EstadoCita.CANCELADA);
        return mapear(citaRepository.save(cita));
    }

    @Transactional
    @Override
    public CitaResponseDTO completarCita(UUID id) {
        Cita cita = obtenerCita(id);
        if (cita.getEstado() != EstadoCita.CONFIRMADA) {
            throw new OperacionNoPermitidaException("Solo una cita confirmada puede marcarse como completada.");
        }
        cita.setEstado(EstadoCita.COMPLETADA);
        return mapear(citaRepository.save(cita));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ServicioPopularDTO> obtenerServiciosPopulares() {
        Pageable top5 = PageRequest.of(0, 5);
        return citaRepository.findServiciosMasPedidos(EstadoCita.CANCELADA, top5).stream()
                .map(fila -> new ServicioPopularDTO((Servicio) fila[0], (Long) fila[1]))
                .toList();
    }

    private Usuario obtenerOCrearCliente(CitaRequestDTO request) {
        String nombre = request.clienteNombre().trim();
        String telefono = DatosCliente.normalizarTelefono(request.clienteTelefono());
        String email = request.clienteEmail().trim().toLowerCase(Locale.ROOT);

        var porTelefono = usuarioRepository.findByTelefono(telefono);
        var porEmail = usuarioRepository.findByEmailIgnoreCase(email);

        if (porTelefono.isPresent() && porEmail.isPresent()
                && !porTelefono.get().getId().equals(porEmail.get().getId())) {
            throw new ConflictoDatosClienteException(
                    "El correo y el teléfono ya están asociados a clientes distintos. Verifica tus datos."
            );
        }

        if (porTelefono.isPresent() || porEmail.isPresent()) {
            Usuario existente = porTelefono.orElseGet(porEmail::orElseThrow);
            // Una reserva pública nunca autoriza cambios al perfil ni a su historial.
            if (!telefono.equals(existente.getTelefono())
                    || existente.getEmail() == null || !email.equalsIgnoreCase(existente.getEmail())
                    || !nombre.equalsIgnoreCase(existente.getNombre().trim())) {
                throw new ConflictoDatosClienteException(
                        "Los datos no coinciden con el cliente existente. Verifícalos o contacta a la barbería para actualizarlos."
                );
            }
            return existente;
        }

        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .telefono(telefono)
                .email(email)
                .password(null)
                .rol(RolUsuario.CLIENTE)
                .build());
    }

    private void validarMomentoReserva(LocalDateTime inicio) {
        LocalDateTime ahora = LocalDateTime.now(relojReservas);
        if (!inicio.isAfter(ahora)) {
            throw new HorarioNoDisponibleException("La cita debe programarse para una fecha y hora futura.");
        }
        if (inicio.getSecond() != 0 || inicio.getNano() != 0
                || inicio.getMinute() % HorarioBarberoService.INTERVALO_MINUTOS != 0) {
            throw new HorarioNoDisponibleException("Selecciona uno de los horarios disponibles mostrados por el sistema.");
        }
    }

    private void validarFechaSoportada(LocalDate fecha) {
        if (fecha.getYear() < 1000 || fecha.getYear() > 9999) {
            throw new com.barberia.citas.excepcion.ReservaInvalidaException("La fecha indicada está fuera del rango permitido.");
        }
    }

    private int validarDuracion(Servicio servicio) {
        Integer duracion = servicio.getDuracionMinutos();
        if (duracion == null || duracion < 5 || duracion > 240 || duracion % 5 != 0) {
            throw new OperacionNoPermitidaException("El servicio tiene una duración inválida. Contacta a la barbería.");
        }
        return duracion;
    }

    private Cita obtenerCita(UUID id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));
    }

    private CitaResponseDTO mapear(Cita cita) {
        return new CitaResponseDTO(
                cita.getId(),
                cita.getUsuario().getNombre(),
                cita.getUsuario().getTelefono(),
                cita.getUsuario().getEmail(),
                cita.getServicio().getNombre(),
                cita.getBarbero().getNombre(),
                cita.getFechaHoraInicio(),
                cita.getFechaHoraFin(),
                cita.getEstado()
        );
    }

    private CitaClienteDTO mapearCliente(Cita cita) {
        return new CitaClienteDTO(
                cita.getConsultaToken(),
                cita.getServicio().getNombre(),
                cita.getBarbero().getNombre(),
                cita.getFechaHoraInicio(),
                cita.getFechaHoraFin(),
                cita.getEstado()
        );
    }
}
