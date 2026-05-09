package io.github.pgatzka.gunsmith.apollo;

import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.exception.ApolloHttpException;
import com.apollographql.apollo.exception.ApolloNetworkException;
import com.apollographql.java.client.ApolloClient;
import com.google.common.util.concurrent.RateLimiter;
import io.github.pgatzka.gunsmith.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApolloService {

    private final ApplicationProperties properties;
    private final ApolloClient apolloClient;

    public <D extends Query.Data> D query(Query<D> query) {
        String queryName = query.name();

        int maxAttempts = properties.getApi().getRetries();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug("Executing query {} (attempt {}/{})", queryName, attempt, maxAttempts);
                long start = System.currentTimeMillis();
                D data = executeQuery(query);
                log.debug("Query {} succeeded in {}ms on attempt {}", queryName, System.currentTimeMillis() - start, attempt);
                return data;
            } catch (NonRetryableQueryException e) {
                log.error("Query {} failed with non-retryable error: {}", queryName, e.getMessage());
                throw e;
            } catch (RetryableQueryException e) {
                if (attempt >= maxAttempts) {
                    log.error("Query {} failed after {} attempts, giving up", queryName, maxAttempts, e);
                    throw new RuntimeException("Query " + queryName + " failed after " + maxAttempts + " attempts", e);
                }
                long backoff = computeBackoff(attempt, e.getRetryAfterMillis());
                log.warn("Query {} failed on attempt {}/{}: {} — retrying in {}ms",
                        queryName, attempt, maxAttempts, e.getMessage(), backoff);
                sleep(backoff);
            }
        }

        throw new IllegalStateException("Unreachable: retry loop exited without returning or throwing");
    }

    private <D extends Query.Data> D executeQuery(Query<D> query) {
        CompletableFuture<D> future = new CompletableFuture<>();
        apolloClient.query(query).enqueue(response -> {
            if (response.exception != null) {
                future.completeExceptionally(response.exception);
            } else if (response.errors != null && !response.errors.isEmpty()) {
                future.completeExceptionally(new GraphQLErrorsException(response.errors.toString()));
            } else if (response.data == null) {
                future.completeExceptionally(new EmptyResponseException("No data returned"));
            } else {
                future.complete(response.data);
            }
        });

        try {
            return future.join();
        } catch (CompletionException e) {
            throw classify(e.getCause() != null ? e.getCause() : e);
        }
    }

    private RuntimeException classify(Throwable cause) {
        if (cause instanceof ApolloHttpException http) {
            int status = http.getStatusCode();
            if (status == 429) {
                Long retryAfter = parseRetryAfter(http);
                log.warn("Received HTTP 429 (rate limited){}", retryAfter != null ? ", server requests " + retryAfter + "ms wait" : "");
                return new RetryableQueryException("HTTP 429 (rate limited)", cause, retryAfter);
            }
            if (status >= 500) {
                return new RetryableQueryException("HTTP " + status + " (server error)", cause, null);
            }
            return new NonRetryableQueryException("HTTP " + status + " (client error)", cause);
        }
        if (cause instanceof ApolloNetworkException) {
            return new RetryableQueryException("Network error: " + cause.getMessage(), cause, null);
        }
        if (cause instanceof GraphQLErrorsException || cause instanceof EmptyResponseException) {
            return new NonRetryableQueryException(cause.getMessage(), cause);
        }
        log.warn("Unrecognized exception type {}, treating as non-retryable", cause.getClass().getName());
        return new NonRetryableQueryException("Unknown error: " + cause.getMessage(), cause);
    }

    private Long parseRetryAfter(ApolloHttpException http) {
        // Retry-After can be in seconds or an HTTP-date; we only handle seconds here
        // The exact API for reading headers depends on the Apollo Java client version — verify against your version
        try {
            return http.getHeaders().stream()
                    .filter(h -> "Retry-After".equalsIgnoreCase(h.getName()))
                    .map(h -> Long.parseLong(h.getValue()) * 1000L)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not parse Retry-After header: {}", e.getMessage());
            return null;
        }
    }

    private long computeBackoff(int attempt, Long serverRequestedMillis) {
        if (serverRequestedMillis != null) {
            return serverRequestedMillis;
        }
        long base = properties.getApi().getBackoff().toMillis() * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(base / 2 + 1);
        return base + jitter;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry", e);
        }
    }

    private static class RetryableQueryException extends RuntimeException {
        private final Long retryAfterMillis;

        RetryableQueryException(String message, Throwable cause, Long retryAfterMillis) {
            super(message, cause);
            this.retryAfterMillis = retryAfterMillis;
        }

        Long getRetryAfterMillis() {
            return retryAfterMillis;
        }
    }

    private static class NonRetryableQueryException extends RuntimeException {
        NonRetryableQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class GraphQLErrorsException extends RuntimeException {
        GraphQLErrorsException(String message) {
            super(message);
        }
    }

    private static class EmptyResponseException extends RuntimeException {
        EmptyResponseException(String message) {
            super(message);
        }
    }
}