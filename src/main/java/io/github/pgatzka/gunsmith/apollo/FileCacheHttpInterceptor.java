package io.github.pgatzka.gunsmith.apollo;

import com.apollographql.apollo.api.http.HttpRequest;
import com.apollographql.apollo.api.http.HttpResponse;
import com.apollographql.apollo.exception.ApolloNetworkException;
import com.apollographql.java.client.network.http.HttpCallback;
import com.apollographql.java.client.network.http.HttpInterceptor;
import com.apollographql.java.client.network.http.HttpInterceptorChain;
import lombok.extern.slf4j.Slf4j;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
public class FileCacheHttpInterceptor implements HttpInterceptor {

    private static final Path CACHE_DIR = Path.of("./apollo-cache");
    private static final Duration TTL = Duration.ofHours(12);

    public FileCacheHttpInterceptor() {
        try {
            Files.createDirectories(CACHE_DIR);
            log.info("Apollo file cache initialized at {}", CACHE_DIR.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Apollo cache directory", e);
        }
    }

    @Override
    public void intercept(@NotNull HttpRequest request, @NotNull HttpInterceptorChain chain, @NotNull HttpCallback callback) {
        String cacheKey;
        try {
            cacheKey = computeCacheKey(request);
        } catch (Exception e) {
            log.warn("Could not compute cache key, bypassing cache: {}", e.getMessage());
            chain.proceed(request, callback);
            return;
        }

        Path cacheFile = CACHE_DIR.resolve(cacheKey + ".json");

        if (isCacheHit(cacheFile)) {
            try {
                HttpResponse cachedResponse = buildResponseFromCache(cacheFile);
                log.debug("Cache hit for key {}", cacheKey);
                callback.onResponse(cachedResponse);
                return;
            } catch (IOException e) {
                log.warn("Failed to read cache file {}, falling through to network: {}", cacheFile.getFileName(), e.getMessage());
            }
        } else {
            log.debug("Cache miss for key {}", cacheKey);
        }

        chain.proceed(request, new HttpCallback() {
            @Override
            public void onResponse(@NotNull HttpResponse response) {
                if (response.getStatusCode() == 200) {
                    HttpResponse cacheable = writeCacheAndRebuild(cacheFile, response);
                    callback.onResponse(cacheable);
                } else {
                    callback.onResponse(response);
                }
            }

            @Override
            public void onFailure(@NotNull ApolloNetworkException exception) {
                callback.onFailure(exception);
            }
        });
    }

    private boolean isCacheHit(Path cacheFile) {
        try {
            if (!Files.exists(cacheFile)) {
                return false;
            }
            Instant lastModified = Files.getLastModifiedTime(cacheFile).toInstant();
            if (Duration.between(lastModified, Instant.now()).compareTo(TTL) > 0) {
                log.debug("Cache entry {} expired, deleting", cacheFile.getFileName());
                Files.deleteIfExists(cacheFile);
                return false;
            }
            return true;
        } catch (IOException e) {
            log.warn("Could not check cache file {}: {}", cacheFile.getFileName(), e.getMessage());
            return false;
        }
    }

    private HttpResponse buildResponseFromCache(Path cacheFile) throws IOException {
        byte[] body = Files.readAllBytes(cacheFile);
        return new HttpResponse.Builder(200)
                .body(new Buffer().write(body))
                .build();
    }

    private HttpResponse writeCacheAndRebuild(Path cacheFile, HttpResponse response) {
        try {
            Buffer captured = new Buffer();
            response.getBody().readAll(captured);
            byte[] bytes = captured.readByteArray();
            Files.write(cacheFile, bytes);
            log.debug("Wrote cache entry {} ({} bytes)", cacheFile.getFileName(), bytes.length);

            return new HttpResponse.Builder(response.getStatusCode())
                    .body(new Buffer().write(bytes))
                    .addHeaders(response.getHeaders())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to write cache file {}: {}", cacheFile.getFileName(), e.getMessage());
            try {
                Files.deleteIfExists(cacheFile);
            } catch (IOException ignored) {
            }
            return response;
        }
    }

    private String computeCacheKey(HttpRequest request) throws IOException {
        Buffer buffer = new Buffer();
        request.getBody().writeTo(buffer);
        String body = buffer.readUtf8();
        return sha256(body);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}