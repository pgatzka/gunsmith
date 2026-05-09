package io.github.pgatzka.gunsmith.apollo;

import com.apollographql.java.client.ApolloClient;
import com.google.common.util.concurrent.RateLimiter;
import io.github.pgatzka.gunsmith.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ApolloConfiguration {

    private final ApplicationProperties properties;

    @Bean(destroyMethod = "close")
    ApolloClient apolloClient() {
        return new ApolloClient.Builder()
                .serverUrl(properties.getApi().getUrl())
                .addHttpInterceptor(new FileCacheHttpInterceptor())
                .addHttpInterceptor(new RateLimitingHttpInterceptor(rateLimiter()))
                .build();
    }

    @Bean
    RateLimiter rateLimiter() {
        double permitsPerSecond = properties.getApi().getPermits() / 60d;
        log.info("Apollo running with {} permits per second", permitsPerSecond);
        return RateLimiter.create(permitsPerSecond);
    }

}