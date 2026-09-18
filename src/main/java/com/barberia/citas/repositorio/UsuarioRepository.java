package com.barberia.citas.repositorio;

import com.barberia.citas.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    Optional<Usuario> findByTelefono(String telefono);
}
