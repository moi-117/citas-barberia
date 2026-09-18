package com.barberia.citas;

import com.barberia.citas.seguridad.ConfiguracionSeguraInicio;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracionSeguraInicioTest {
    @Test
    void produccionRechazaRootYPasswordDeEjemplo() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("spring.datasource.username", "root")
                .withProperty("spring.datasource.password", "123456");
        entorno.setActiveProfiles("prod");
        assertThatThrownBy(() -> ejecutar(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_USERNAME");
    }

    @Test
    void produccionAceptaUsuarioDedicadoSecretoExternoYOrigenHttps() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("spring.datasource.username", "rand_app")
                .withProperty("spring.datasource.password", "secreto-externo-distinto")
                .withProperty("app.security.allowed-origins", "https://barberia.example");
        entorno.setActiveProfiles("prod");
        assertThatCode(() -> ejecutar(entorno)).doesNotThrowAnyException();
    }

    @Test
    void corsRechazaComodinesRutasYHttpEnProduccion() {
        for (String origen : new String[]{"*", "https://barberia.example/ruta", "http://barberia.example"}) {
            MockEnvironment entorno = new MockEnvironment()
                    .withProperty("spring.datasource.username", "rand_app")
                    .withProperty("spring.datasource.password", "secreto-externo-distinto")
                    .withProperty("app.security.allowed-origins", origen);
            entorno.setActiveProfiles("prod");
            assertThatThrownBy(() -> ejecutar(entorno)).isInstanceOf(IllegalStateException.class);
        }
    }

    private void ejecutar(MockEnvironment entorno) throws Exception {
        new ConfiguracionSeguraInicio(entorno)
                .run(new DefaultApplicationArguments(new String[0]));
    }
}
