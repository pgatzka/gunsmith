package io.github.pgatzka.gunsmith.apollo;

import com.apollographql.apollo.api.http.HttpRequest;
import com.apollographql.java.client.network.http.HttpCallback;
import com.apollographql.java.client.network.http.HttpInterceptor;
import com.apollographql.java.client.network.http.HttpInterceptorChain;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@RequiredArgsConstructor
public class RateLimitingHttpInterceptor implements HttpInterceptor {

    private final RateLimiter rateLimiter;

    @Override
    public void intercept(@NotNull HttpRequest request, @NotNull HttpInterceptorChain chain, @NotNull HttpCallback callback) {
        double waitTime = rateLimiter.acquire();
        if (waitTime > 0.1) {
            log.debug("Rate limiter throttled network call for {}s", waitTime);
        }
        chain.proceed(request, callback);
    }
}