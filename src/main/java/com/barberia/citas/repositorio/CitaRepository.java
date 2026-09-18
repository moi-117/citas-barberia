package com.barberia.citas.repositorio;

import com.barberia.citas.dominio.Cita;
import com.barberia.citas.dominio.enums.EstadoCita;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CitaRepository extends JpaRepository<Cita, UUID> {

    boolean existsByBarberoId(UUID barberoId);

    Optional<Cita> findByConsultaToken(String consultaToken);

    Optional<Cita> findByClaveIdempotencia(String claveIdempotencia);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Cita c " +
            "WHERE c.barbero.id = :barberoId " +
            "AND c.estado != :estadoCancelada " +
            "AND (c.fechaHoraInicio < :fin AND c.fechaHoraFin > :inicio)")
    boolean existeSolapamiento(@Param("barberoId") UUID barberoId,
                               @Param("inicio") LocalDateTime inicio,
                               @Param("fin") LocalDateTime fin,
                               @Param("estadoCancelada") EstadoCita estadoCancelada);

    @Query("SELECT c FROM Cita c " +
            "WHERE c.barbero.id = :barberoId " +
            "AND c.estado != :estadoCancelada " +
            "AND c.fechaHoraInicio < :finDia AND c.fechaHoraFin > :inicioDia")
    List<Cita> findCitasDelDia(@Param("barberoId") UUID barberoId,
                               @Param("inicioDia") LocalDateTime inicioDia,
                               @Param("finDia") LocalDateTime finDia,
                               @Param("estadoCancelada") EstadoCita estadoCancelada);

    @Query("SELECT c FROM Cita c JOIN FETCH c.usuario JOIN FETCH c.servicio JOIN FETCH c.barbero " +
            "WHERE c.fechaHoraInicio >= :inicio AND c.fechaHoraInicio < :fin ORDER BY c.fechaHoraInicio")
    List<Cita> findAllConRelacionesEntre(@Param("inicio") LocalDateTime inicio,
                                         @Param("fin") LocalDateTime fin);

    @Query("SELECT c FROM Cita c JOIN FETCH c.usuario JOIN FETCH c.servicio JOIN FETCH c.barbero " +
            "WHERE c.barbero.id = :barberoId AND c.estado IN :estadosActivos " +
            "AND c.fechaHoraInicio >= :desde ORDER BY c.fechaHoraInicio")
    List<Cita> findFuturasDeBarbero(@Param("barberoId") UUID barberoId,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("estadosActivos") List<EstadoCita> estadosActivos);

    @Query("SELECT c.servicio, COUNT(c) as totalReservas " +
            "FROM Cita c " +
            "WHERE c.estado != :estadoCancelada " +
            "GROUP BY c.servicio " +
            "ORDER BY totalReservas DESC")
    List<Object[]> findServiciosMasPedidos(@Param("estadoCancelada") EstadoCita estadoCancelada,
                                           Pageable pageable);

    @Query("SELECT c FROM Cita c JOIN FETCH c.usuario JOIN FETCH c.servicio JOIN FETCH c.barbero")
    List<Cita> findAllConRelaciones();
}
