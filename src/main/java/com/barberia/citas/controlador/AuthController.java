package com.barberia.citas.controlador;

import com.barberia.citas.dominio.Usuario;
import com.barberia.citas.dominio.enums.RolUsuario;
import com.barberia.citas.dto.AuthResponseDTO;
import com.barberia.citas.dto.LoginDTO;
import com.barberia.citas.dto.RegistroDTO;
import com.barberia.citas.repositorio.UsuarioRepository;
import com.barberia.citas.seguridad.ControlIntentosLogin;
import com.barberia.citas.validacion.DatosCliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ControlIntentosLogin controlIntentosLogin;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken()
        );
    }

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroDTO registroDTO) {
        String email = normalizarEmail(registroDTO.email());
        String telefono = DatosCliente.normalizarTelefono(registroDTO.telefono());

        Optional<Usuario> porEmail = usuarioRepository.findByEmailIgnoreCase(email);
        Optional<Usuario> porTelefono = usuarioRepository.findByTelefono(telefono);

        if (porEmail.isPresent() && porEmail.get().getPassword() != null) {
            return conflictoRegistro();
        }

        if (porTelefono.isPresent() && porTelefono.get().getPassword() != null) {
            return conflictoRegistro();
        }

        if (porEmail.isPresent() || porTelefono.isPresent()) {
            return conflictoRegistro();
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(registroDTO.nombre().trim());
        usuario.setEmail(email);
        usuario.setTelefono(telefono);
        usuario.setPassword(passwordEncoder.encode(registroDTO.password()));
        usuario.setRol(RolUsuario.CLIENTE);

        try {
            Usuario guardado = usuarioRepository.saveAndFlush(usuario);
            return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponseDTO(
                    guardado.getId(), guardado.getNombre(), guardado.getEmail(), guardado.getRol()
            ));
        } catch (DataIntegrityViolationException ex) {
            // Cubre el caso raro de dos registros simultáneos con el mismo correo/teléfono.
            return conflictoRegistro();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO loginDTO,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        String email = normalizarEmail(loginDTO.email());
        String claveIntentos = request.getRemoteAddr() + "|" + email;
        Duration bloqueo = controlIntentosLogin.bloqueoRestante(claveIntentos);
        if (!bloqueo.isZero()) {
            return demasiadosIntentos(bloqueo);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, loginDTO.password())
            );
        } catch (AuthenticationException ex) {
            bloqueo = controlIntentosLogin.registrarFallo(claveIntentos);
            return bloqueo.isZero() ? credencialesInvalidas() : demasiadosIntentos(bloqueo);
        }
        controlIntentosLogin.registrarExito(claveIntentos);

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado ya no existe"));

        HttpSession session = request.getSession(true);
        if (!session.isNew()) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(new AuthResponseDTO(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", "No autenticado"));
        }

        return usuarioRepository.findByEmailIgnoreCase(authentication.getName())
                .<ResponseEntity<?>>map(usuario -> ResponseEntity.ok(new AuthResponseDTO(
                        usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("mensaje", "La sesión ya no corresponde a un usuario válido")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada"));
    }

    private ResponseEntity<?> credencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("mensaje", "Credenciales incorrectas"));
    }

    private ResponseEntity<?> demasiadosIntentos(Duration bloqueo) {
        long segundos = Math.max(1, bloqueo.toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(segundos))
                .body(Map.of("mensaje", "Demasiados intentos. Espera antes de volver a iniciar sesión."));
    }

    private ResponseEntity<?> conflictoRegistro() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", "No se pudo crear la cuenta con los datos indicados."));
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
