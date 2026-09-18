// src/main/java/com/barberia/citas/dominio/Cita.java
package com.barberia.citas.dominio;

import com.barberia.citas.dominio.enums.EstadoCita;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "citas", indexes = {
        @Index(name = "idx_cita_rango_horario", columnList = "fecha_hora_inicio, fecha_hora_fin"),
        @Index(name = "idx_cita_barbero_horario", columnList = "barbero_id, fecha_hora_inicio, fecha_hora_fin")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "fechaHoraInicio", "fechaHoraFin", "estado"})
public class Cita {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private LocalDateTime fechaHoraInicio;

    @Column(name = "fecha_hora_fin", nullable = false)
    private LocalDateTime fechaHoraFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCita estado;

    /**
     * Token privado que permite al navegador del cliente consultar el estado
     * de esta cita sin obligarlo a crear una cuenta ni recordar un código.
     */
    @Column(name = "consulta_token", nullable = false, unique = true, length = 36, updatable = false)
    private String consultaToken;

    @Column(name = "clave_idempotencia", unique = true, length = 36, updatable = false)
    private String claveIdempotencia;

    @Column(name = "huella_reserva", length = 64, updatable = false)
    private String huellaReserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbero_id", nullable = false)
    private Barbero barbero;

    /**
     * Bloqueo optimista: evita que dos transacciones concurrentes
     * confirmen/actualicen la misma cita sobre una versión obsoleta.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
