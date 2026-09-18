package com.barberia.citas.repositorio;

import com.barberia.citas.dominio.Barbero;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BarberoRepository extends JpaRepository<Barbero, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Barbero b WHERE b.id = :id")
    Optional<Barbero> findByIdForUpdate(@Param("id") UUID id);

    List<Barbero> findByActivoTrueOrderByNombreAsc();

    List<Barbero> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);
}
