package io.github.pgatzka.gunsmith.observability;

import io.github.pgatzka.gunsmith.jooq.JooqQueryStatsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Endpoint(id = "jooqstats")
@RequiredArgsConstructor
public class JooqStatsEndpoint {

    private final JooqQueryStatsListener listener;

    @ReadOperation
    public Map<String, Object> stats() {
        List<Map<String, Object>> queries = listener.snapshot().entrySet().stream()
                .map(e -> {
                    var s = e.getValue();
                    return Map.<String, Object>of(
                            "sql", e.getKey(),
                            "count", s.getCount(),
                            "minMs", s.getMinNanos() / 1_000_000.0,
                            "avgMs", s.getAvgNanos() / 1_000_000.0,
                            "maxMs", s.getMaxNanos() / 1_000_000.0,
                            "totalMs", s.getTotalNanos() / 1_000_000.0
                    );
                })
                .sorted(Comparator.comparing(m -> -((Long) m.get("count"))))
                .toList();
        return Map.of("queries", queries);
    }

    @DeleteOperation
    public void reset() {
        listener.reset();
    }
}
