package com.barberia.citas;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class ReservaMigracionTest {
    @Test
    void v7ConservaCitasPreviasYExigeClavesUnicas() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:migracion_reservas;MODE=MySQL", "sa", "")) {
            var sql = conexion.createStatement();
            sql.execute("CREATE TABLE citas (id INT PRIMARY KEY, consulta_token VARCHAR(36))");
            sql.execute("INSERT INTO citas VALUES (1, 'token-previo'), (2, 'otro-token')");
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V7__idempotencia_reservas.sql"));
            var anteriores = sql.executeQuery("SELECT consulta_token, clave_idempotencia FROM citas WHERE id=1");
            assertThat(anteriores.next()).isTrue();
            assertThat(anteriores.getString(1)).isEqualTo("token-previo");
            assertThat(anteriores.getString(2)).isNull();
            sql.execute("UPDATE citas SET clave_idempotencia='12345678-1234-4234-8234-123456789012' WHERE id=1");
            assertThatThrownBy(() -> sql.execute("UPDATE citas SET clave_idempotencia='12345678-1234-4234-8234-123456789012' WHERE id=2"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
