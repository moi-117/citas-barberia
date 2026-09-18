package com.barberia.citas.repositorio;

import com.barberia.citas.dominio.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
    List<Servicio> findByActivoTrueOrderByNombreAsc();
    List<Servicio> findAllByOrderByNombreAsc();
}
