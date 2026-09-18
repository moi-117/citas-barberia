package com.barberia.citas.servicio;

import com.barberia.citas.dominio.Barbero;
import com.barberia.citas.dominio.Cita;
import com.barberia.citas.dominio.HorarioBarbero;
import com.barberia.citas.dominio.enums.EstadoCita;
import com.barberia.citas.dto.HorarioBarberoRequestDTO;
import com.barberia.citas.dto.HorarioBarberoResponseDTO;
import com.barberia.citas.dto.HorarioSemanalRequestDTO;
import com.barberia.citas.excepcion.HorarioNoDisponibleException;
import com.barberia.citas.excepcion.OperacionNoPermitidaException;
import com.barberia.citas.excepcion.RecursoNoEncontradoException;
import com.barberia.citas.repositorio.BarberoRepository;
import com.barberia.citas.repositorio.CitaRepository;
import com.barberia.citas.repositorio.HorarioBarberoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HorarioBarberoService {

    public static final LocalTime APERTURA_GENERAL = LocalTime.of(8, 0);
    public static final LocalTime CIERRE_GENERAL = LocalTime.of(20, 0);
    public static final int INTERVALO_MINUTOS = 15;

    private final HorarioBarberoRepository horarioRepository;
    private final BarberoRepository barberoRepository;
    private final CitaRepository citaRepository;
    private final Clock relojReservas;

    @Transactional(readOnly = true)
    public List<HorarioBarberoResponseDTO> listar(UUID barberoId) {
        if (!barberoRepository.existsById(barberoId)) {
            throw new RecursoNoEncontradoException("Barbero no encontrado");
        }
        Map<DayOfWeek, HorarioBarbero> guardados = horarioRepository.findByBarberoIdOrderByDiaSemana(barberoId)
                .stream().collect(Collectors.toMap(HorarioBarbero::getDiaSemana, Function.identity()));
        return Arrays.stream(DayOfWeek.values())
                .map(dia -> mapear(guardados.get(dia), dia))
                .toList();
    }

    @Transactional
    public List<HorarioBarberoResponseDTO> guardar(UUID barberoId, HorarioSemanalRequestDTO request) {
        Barbero barbero = barberoRepository.findByIdForUpdate(barberoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Barbero no encontrado"));

        Map<DayOfWeek, HorarioBarberoRequestDTO> porDia = new EnumMap<>(DayOfWeek.class);
        for (HorarioBarberoRequestDTO dia : request.dias()) {
            if (porDia.put(dia.diaSemana(), dia) != null) {
                throw new OperacionNoPermitidaException("Cada día debe aparecer una sola vez.");
            }
            validarConfiguracion(dia);
        }
        if (porDia.size() != DayOfWeek.values().length) {
            throw new OperacionNoPermitidaException("Debes configurar los siete días de la semana.");
        }

        List<Cita> futuras = citaRepository.findFuturasDeBarbero(
                barberoId, LocalDateTime.now(relojReservas),
                List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA));
        for (Cita cita : futuras) {
            if (!acepta(porDia.get(cita.getFechaHoraInicio().getDayOfWeek()),
                    cita.getFechaHoraInicio().toLocalTime(), cita.getFechaHoraFin().toLocalTime())) {
                throw new OperacionNoPermitidaException(
                        "El nuevo horario deja fuera una cita futura del " + cita.getFechaHoraInicio().toLocalDate()
                                + ". Cancela o reprograma esa cita antes de cambiar el horario.");
            }
        }

        Map<DayOfWeek, HorarioBarbero> existentes = horarioRepository.findByBarberoIdOrderByDiaSemana(barberoId)
                .stream().collect(Collectors.toMap(HorarioBarbero::getDiaSemana, Function.identity()));
        List<HorarioBarbero> guardar = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            HorarioBarberoRequestDTO datos = porDia.get(dia);
            HorarioBarbero horario = existentes.getOrDefault(dia, HorarioBarbero.builder()
                    .barbero(barbero).diaSemana(dia).build());
            horario.setActivo(datos.activo());
            horario.setHoraInicio(datos.activo() ? datos.horaInicio() : null);
            horario.setHoraFin(datos.activo() ? datos.horaFin() : null);
            horario.setDescansoInicio(datos.activo() ? datos.descansoInicio() : null);
            horario.setDescansoFin(datos.activo() ? datos.descansoFin() : null);
            guardar.add(horario);
        }
        horarioRepository.saveAll(guardar);
        return guardar.stream().map(h -> mapear(h, h.getDiaSemana())).toList();
    }

    @Transactional(readOnly = true)
    public HorarioBarberoResponseDTO obtener(UUID barberoId, DayOfWeek dia) {
        return horarioRepository.findByBarberoIdAndDiaSemana(barberoId, dia)
                .map(h -> mapear(h, dia))
                .orElseGet(() -> predeterminado(dia));
    }

    public void validarReserva(HorarioBarberoResponseDTO horario, LocalDateTime inicio, LocalDateTime fin) {
        if (!acepta(horario, inicio.toLocalTime(), fin.toLocalTime())
                || !inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new HorarioNoDisponibleException("El horario está fuera de la jornada o coincide con un descanso del barbero.");
        }
    }

    public boolean acepta(HorarioBarberoResponseDTO horario, LocalTime inicio, LocalTime fin) {
        if (!horario.activo() || horario.horaInicio() == null || horario.horaFin() == null
                || inicio.isBefore(horario.horaInicio()) || fin.isAfter(horario.horaFin())) {
            return false;
        }
        return horario.descansoInicio() == null
                || !inicio.isBefore(horario.descansoFin())
                || !fin.isAfter(horario.descansoInicio());
    }

    private boolean acepta(HorarioBarberoRequestDTO horario, LocalTime inicio, LocalTime fin) {
        return acepta(new HorarioBarberoResponseDTO(horario.diaSemana(), horario.activo(), horario.horaInicio(),
                horario.horaFin(), horario.descansoInicio(), horario.descansoFin()), inicio, fin);
    }

    private void validarConfiguracion(HorarioBarberoRequestDTO dia) {
        if (!dia.activo()) {
            return;
        }
        if (dia.diaSemana() == DayOfWeek.SUNDAY) {
            throw new OperacionNoPermitidaException("La jornada general no permite atención los domingos.");
        }
        if (dia.horaInicio() == null || dia.horaFin() == null || !dia.horaInicio().isBefore(dia.horaFin())) {
            throw new OperacionNoPermitidaException("Los días activos necesitan una hora de inicio y fin válidas.");
        }
        if (dia.horaInicio().isBefore(APERTURA_GENERAL) || dia.horaFin().isAfter(CIERRE_GENERAL)) {
            throw new OperacionNoPermitidaException("El horario individual debe estar dentro de la jornada general de 08:00 a 20:00.");
        }
        validarCuartoDeHora(dia.horaInicio());
        validarCuartoDeHora(dia.horaFin());
        if ((dia.descansoInicio() == null) != (dia.descansoFin() == null)) {
            throw new OperacionNoPermitidaException("El descanso necesita hora de inicio y fin.");
        }
        if (dia.descansoInicio() != null) {
            if (!dia.descansoInicio().isBefore(dia.descansoFin())
                    || dia.descansoInicio().isBefore(dia.horaInicio())
                    || dia.descansoFin().isAfter(dia.horaFin())) {
                throw new OperacionNoPermitidaException("El descanso debe estar dentro del horario del barbero.");
            }
            validarCuartoDeHora(dia.descansoInicio());
            validarCuartoDeHora(dia.descansoFin());
        }
    }

    private void validarCuartoDeHora(LocalTime hora) {
        if (hora.getSecond() != 0 || hora.getNano() != 0 || hora.getMinute() % INTERVALO_MINUTOS != 0) {
            throw new OperacionNoPermitidaException("Los horarios deben usar intervalos de 15 minutos.");
        }
    }

    private HorarioBarberoResponseDTO mapear(HorarioBarbero horario, DayOfWeek dia) {
        return horario == null ? predeterminado(dia) : new HorarioBarberoResponseDTO(
                dia, horario.isActivo(), horario.getHoraInicio(), horario.getHoraFin(),
                horario.getDescansoInicio(), horario.getDescansoFin());
    }

    private HorarioBarberoResponseDTO predeterminado(DayOfWeek dia) {
        boolean activo = dia != DayOfWeek.SUNDAY;
        return new HorarioBarberoResponseDTO(dia, activo,
                activo ? APERTURA_GENERAL : null, activo ? CIERRE_GENERAL : null, null, null);
    }
}
