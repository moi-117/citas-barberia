package com.barberia.citas.repositorio;

import com.barberia.citas.dominio.HorarioBarbero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HorarioBarberoRepository extends JpaRepository<HorarioBarbero, Long> {
    List<HorarioBarbero> findByBarberoIdOrderByDiaSemana(UUID barberoId);
    Optional<HorarioBarbero> findByBarberoIdAndDiaSemana(UUID barberoId, DayOfWeek diaSemana);
}
