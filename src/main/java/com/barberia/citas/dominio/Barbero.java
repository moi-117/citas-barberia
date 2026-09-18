package com.barberia.citas.dominio;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "barberos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "nombre", "activo"})
public class Barbero {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "especialidad", nullable = false, length = 160)
    private String especialidad;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
