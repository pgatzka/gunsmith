package io.github.pgatzka.gunsmith.config;

import io.github.pgatzka.gunsmith.jooq.JooqQueryStatsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JooqStatsConfiguration {

    private final JooqQueryStatsListener listener;

    @Bean
    DefaultConfigurationCustomizer jooqStatsCustomizer() {
        return customizer -> customizer.set(listener);
    }

}