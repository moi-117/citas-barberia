// src/main/java/com/barberia/citas/dominio/Usuario.java
package com.barberia.citas.dominio;

import com.barberia.citas.dominio.enums.RolUsuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuario_telefono", columnNames = "telefono"),
        @UniqueConstraint(name = "uk_usuario_email", columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "nombre", "rol"})
public class Usuario {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

    @Column(name = "email", length = 150)
    private String email;

    // Permitimos que sea nulo para los clientes que agendan sin crear cuenta
    @Column(name = "password", length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private RolUsuario rol;
}