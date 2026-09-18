package com.barberia.citas.servicio;

import com.barberia.citas.dominio.Servicio;
import com.barberia.citas.dto.ServicioRequestDTO;
import com.barberia.citas.excepcion.OperacionNoPermitidaException;
import com.barberia.citas.excepcion.RecursoNoEncontradoException;
import com.barberia.citas.repositorio.ServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicioAdminService {

    private final ServicioRepository servicioRepository;

    @Transactional(readOnly = true)
    public List<Servicio> listar() {
        return servicioRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public Servicio crear(ServicioRequestDTO request) {
        String nombre = request.nombre().trim();
        if (servicioRepository.existsByNombreIgnoreCase(nombre)) {
            throw new OperacionNoPermitidaException("Ya existe un servicio con ese nombre.");
        }
        Servicio servicio = new Servicio();
        aplicar(servicio, request);
        servicio.setActivo(true);
        return servicioRepository.save(servicio);
    }

    @Transactional
    public Servicio editar(Long id, ServicioRequestDTO request) {
        Servicio servicio = obtener(id);
        String nombre = request.nombre().trim();
        if (servicioRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new OperacionNoPermitidaException("Ya existe otro servicio con ese nombre.");
        }
        aplicar(servicio, request);
        return servicioRepository.save(servicio);
    }

    @Transactional
    public Servicio cambiarEstado(Long id, boolean activo) {
        Servicio servicio = obtener(id);
        servicio.setActivo(activo);
        return servicioRepository.save(servicio);
    }

    private Servicio obtener(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));
    }

    private void aplicar(Servicio servicio, ServicioRequestDTO request) {
        servicio.setNombre(request.nombre().trim());
        servicio.setDescripcion(request.descripcion() == null || request.descripcion().isBlank()
                ? null : request.descripcion().trim());
        servicio.setPrecio(request.precio());
        servicio.setDuracionMinutos(request.duracionMinutos());
    }
}
