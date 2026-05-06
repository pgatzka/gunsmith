package io.github.pgatzka.gunsmith.apollo;

import com.apollographql.apollo.api.Query;
import com.apollographql.java.client.ApolloClient;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApolloService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1_000L;

    private final RateLimiter rateLimiter;
    private final ApolloClient apolloClient;

    public <D extends Query.Data> D query(Query<D> query) {
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return executeQuery(query);
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("Query attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());

                if (attempt < MAX_ATTEMPTS) {
                    sleep(INITIAL_BACKOFF_MS * (1L << (attempt - 1))); // 1s, 2s, 4s...
                }
            }
        }

        throw new RuntimeException("Query failed after " + MAX_ATTEMPTS + " attempts", lastException);
    }

    private <D extends Query.Data> D executeQuery(Query<D> query) {
        rateLimiter.acquire();
        CompletableFuture<D> future = new CompletableFuture<>();
        log.debug("Sending query: {}", query);
        apolloClient.query(query).enqueue(response -> {
            if (response.exception != null) {
                future.completeExceptionally(response.exception);
            } else if (response.errors != null && !response.errors.isEmpty()) {
                future.completeExceptionally(new RuntimeException("Response has errors: " + response.errors));
            } else if (response.data == null) {
                future.completeExceptionally(new RuntimeException("No data returned"));
            } else {
                future.complete(response.data);
            }
        });
        return future.join();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry", e);
        }
    }
}