package io.github.pgatzka.gunsmith.apollo;

import com.apollographql.java.client.ApolloClient;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApolloConfiguration {

    @Bean(destroyMethod = "close")
    ApolloClient apolloClient() {
        return new ApolloClient.Builder().serverUrl("https://api.tarkov.dev/graphql").build();
    }

    @Bean
    RateLimiter rateLimiter() {
        return RateLimiter.create(1);
    }

}
