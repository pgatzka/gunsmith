package io.github.pgatzka.gunsmith.web;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only stats queries for the throughput page. The single query here uses Postgres'
 * {@code generate_series} left-joined with the per-minute count so empty minutes come back as
 * explicit zero rows — the chart never has to invent missing buckets.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    public record ThroughputBucket(OffsetDateTime bucket, long count) {}

    public record ThroughputReport(List<ThroughputBucket> buckets,
                                   long total, long peak, double mean, long lastMinute,
                                   OffsetDateTime generatedAt) {}

    private final DSLContext dsl;

    public ThroughputReport last4Hours() {
        String sql = """
                with series as (
                    select generate_series(
                        date_trunc('minute', now() - interval '4 hours'),
                        date_trunc('minute', now()),
                        interval '1 minute'
                    ) as bucket
                ),
                counts as (
                    select date_trunc('minute', created_at) as bucket, count(*)::bigint as cnt
                    from build
                    where created_at >= now() - interval '4 hours'
                    group by 1
                )
                select s.bucket, coalesce(c.cnt, 0) as cnt
                from series s
                left join counts c on c.bucket = s.bucket
                order by s.bucket
                """;

        var rows = dsl.fetch(sql);
        List<ThroughputBucket> buckets = new ArrayList<>(rows.size());
        long total = 0, peak = 0, lastMinute = 0;
        for (var r : rows) {
            OffsetDateTime ts = r.get("bucket", OffsetDateTime.class);
            long n = r.get("cnt", Long.class);
            buckets.add(new ThroughputBucket(ts, n));
            total += n;
            if (n > peak) peak = n;
        }
        if (!buckets.isEmpty()) {
            lastMinute = buckets.get(buckets.size() - 1).count();
        }
        double mean = buckets.isEmpty() ? 0.0 : (double) total / buckets.size();

        return new ThroughputReport(buckets, total, peak, mean, lastMinute, OffsetDateTime.now());
    }
}
