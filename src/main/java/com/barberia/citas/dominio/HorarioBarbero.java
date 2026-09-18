package com.barberia.citas.dominio;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "horarios_barbero", uniqueConstraints =
        @UniqueConstraint(name = "uk_horario_barbero_dia", columnNames = {"barbero_id", "dia_semana"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class HorarioBarbero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbero_id", nullable = false)
    private Barbero barbero;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 12)
    private DayOfWeek diaSemana;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(name = "descanso_inicio")
    private LocalTime descansoInicio;

    @Column(name = "descanso_fin")
    private LocalTime descansoFin;
}
