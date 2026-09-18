package com.barberia.citas.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelojReservasConfig {
    @Bean
    public Clock relojReservas() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
