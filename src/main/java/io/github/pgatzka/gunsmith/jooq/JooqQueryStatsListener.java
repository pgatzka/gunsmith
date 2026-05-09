package io.github.pgatzka.gunsmith.jooq;

import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class JooqQueryStatsListener implements ExecuteListener {

    private final Map<String, Stats> statsByQuery = new ConcurrentHashMap<>();

    @Override
    public void renderEnd(ExecuteContext ctx) {
        // Capture the rendered SQL once, before execution starts
        ctx.data("query.start", System.nanoTime());
    }

    @Override
    public void executeEnd(ExecuteContext ctx) {
        Long start = (Long) ctx.data("query.start");
        if (start == null) return;
        long elapsedNanos = System.nanoTime() - start;

        String sql = ctx.query() != null
                ? DSL.using(ctx.dialect()).render(ctx.query())
                : ctx.sql();
        if (sql == null) return;


        sql = clean(sql);

        if (sql.startsWith("insert into build")) {
            sql = "insert into build (weapon_id, ergonomics, recoil_horizontal, recoil_vertical, json, json_hash) values (?, ?, ?, ?, ?, ?)";
        }

        statsByQuery.computeIfAbsent(sql, k -> new Stats()).record(elapsedNanos);
    }

    private static String clean(String sql) {
        return sql.replace("\"public\".", "").replace("\"", "");
    }

    public Map<String, Stats> snapshot() {
        return Map.copyOf(statsByQuery);
    }

    public void reset() {
        statsByQuery.clear();
    }

    public static class Stats {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxNanos = new AtomicLong();

        void record(long nanos) {
            count.incrementAndGet();
            totalNanos.addAndGet(nanos);
            minNanos.accumulateAndGet(nanos, Math::min);
            maxNanos.accumulateAndGet(nanos, Math::max);
        }

        public long getCount() {
            return count.get();
        }

        public long getTotalNanos() {
            return totalNanos.get();
        }

        public long getMinNanos() {
            long m = minNanos.get();
            return m == Long.MAX_VALUE ? 0 : m;
        }

        public long getMaxNanos() {
            return maxNanos.get();
        }

        public long getAvgNanos() {
            long c = count.get();
            return c == 0 ? 0 : totalNanos.get() / c;
        }
    }
}