package com.barberia.citas.servicio;

import com.barberia.citas.dto.CitaClienteDTO;
import com.barberia.citas.dto.CitaRequestDTO;
import com.barberia.citas.dto.CitaResponseDTO;
import com.barberia.citas.dto.ServicioPopularDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CitaService {
    CitaClienteDTO agendarCita(CitaRequestDTO request);
    CitaClienteDTO agendarCita(CitaRequestDTO request, String claveIdempotencia);
    CitaClienteDTO consultarCitaPorToken(String token);
    List<CitaResponseDTO> listarCitas();
    List<CitaResponseDTO> listarCitas(LocalDate fecha);
    List<String> obtenerDisponibilidad(UUID barberoId, LocalDate fecha, Long servicioId);
    CitaResponseDTO confirmarCita(UUID id);
    CitaResponseDTO cancelarCita(UUID id);
    CitaResponseDTO completarCita(UUID id);
    List<ServicioPopularDTO> obtenerServiciosPopulares();
}
