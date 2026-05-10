package io.github.pgatzka.gunsmith.batch;

import io.github.pgatzka.gunsmith.ApplicationProperties;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.WeaponIdsQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.pgatzka.gunsmith.jooq.Tables.BUILD;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuildJobScheduler implements SchedulingConfigurer {

    private final DSLContext dsl;

    private final ApolloService apolloService;

    private final ApplicationProperties properties;
    private final JobOperator jobOperator;
    private final Job generateJob;

    private volatile long jobCountTarget = 0;
    private volatile int targetWidth = 1;

    private final AtomicLong lastBuilt = new AtomicLong(0);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Initializing BuildJobScheduler...");

        List<String> ignoredWeapons = properties.getJob().getIgnoredWeapons();
        log.debug("Configured {} ignored weapons: {}", ignoredWeapons.size(), ignoredWeapons);

        long weaponCount = apolloService.query(new WeaponIdsQuery()).items.stream().filter(item -> {
            if (ignoredWeapons.contains(item.id)) {
                log.info("Ignoring weapon: {} (id={})", item.name, item.id);
                return false;
            }
            return true;
        }).count();

        if (weaponCount == 0) {
            log.error("No active weapons after applying ignore list — scheduler will remain idle");
            return;
        }

        long targetJobResults = properties.getJob().getTarget();
        long countPerWeapon = targetJobResults / weaponCount;
        long target = countPerWeapon * weaponCount;

        log.info("Running with {} / {} builds per job -> {} per weapon at {} weapons",
                target, targetJobResults, countPerWeapon, weaponCount);
        log.info("Scheduler config: minDelay={}, maxDelay={}, slowdownThreshold={}, startupDelay={}",
                formatDuration(properties.getJob().getMinDelay()),
                formatDuration(properties.getJob().getMaxDelay()),
                properties.getJob().getSlowdownThreshold(),
                formatDuration(properties.getJob().getStartupDelay()));

        jobCountTarget = target;
        targetWidth = String.valueOf(target).length();
        log.info("BuildJobScheduler initialized; jobCountTarget={}", jobCountTarget);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        log.info("Registering trigger task with BuildJobScheduler");
        registrar.addTriggerTask(this::runJob, this::nextExecution);
    }

    int limit = 500_000;

    private void runJob() {
        if (jobCountTarget == 0) {
            log.debug("Skipping run: jobCountTarget not yet initialized");
            return;
        }

        Instant start = Instant.now();
        log.debug("Job tick starting at {}", start);

        long count = dsl.fetchCount(BUILD);

        try {
            jobOperator.start(generateJob, new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis()).toJobParameters());
        } catch (Exception e) {
            log.error("Generate Job Failed", e);
            return; // do not slow down — skip metrics, keep current delay
        }

        long countAfter = dsl.fetchCount(BUILD);



        long built = countAfter - count;
        lastBuilt.set(built);

        Duration elapsed = Duration.between(start, Instant.now());
        double buildPercent = (built * 100.0) / jobCountTarget;
        Duration nextDelay = computeDelay(built);
        double delayPercent = computeDelayPercent(nextDelay);

        log.info("Built {} / {} ({}%) in {} | next job in {} | delay at {}%",
                String.format("%" + targetWidth + "d", built),
                jobCountTarget,
                String.format("%3.0f", buildPercent),
                formatDuration(elapsed),
                formatDuration(nextDelay),
                String.format("%3.0f", delayPercent));
    }

    private double computeDelayPercent(Duration delay) {
        long minMs = properties.getJob().getMinDelay().toMillis();
        long maxMs = properties.getJob().getMaxDelay().toMillis();
        long range = maxMs - minMs;
        if (range <= 0) return 0.0;
        return ((delay.toMillis() - minMs) * 100.0) / range;
    }

    private Instant nextExecution(TriggerContext ctx) {
        Instant base = ctx.lastCompletion();
        if (base == null) {
            base = Instant.now();
            log.debug("No previous completion; scheduling from now");
        }

        if (jobCountTarget == 0) {
            Duration startupDelay = properties.getJob().getStartupDelay();
            Instant next = base.plus(startupDelay);
            log.debug("Awaiting initialization; next tick in {} at {}", formatDuration(startupDelay), next);
            return next;
        }

        Duration delay = computeDelay(lastBuilt.get());
        Instant next = base.plus(delay);
        log.debug("Next tick in {} at {} (lastBuilt={})", formatDuration(delay), next, lastBuilt.get());
        return next;
    }

    private Duration computeDelay(long built) {
        Duration minDelay = properties.getJob().getMinDelay();
        Duration maxDelay = properties.getJob().getMaxDelay();
        long minMs = minDelay.toMillis();
        long maxMs = maxDelay.toMillis();

        if (built <= 0) {
            log.debug("computeDelay: built={} <= 0 -> maxDelay {}", built, formatDuration(maxDelay));
            return maxDelay;
        }

        double threshold = properties.getJob().getSlowdownThreshold();
        double thresholdCount = jobCountTarget * threshold;

        if (built >= thresholdCount) {
            log.debug("computeDelay: built={} >= threshold {} ({}% of target {}) -> minDelay {}",
                    built, thresholdCount, threshold * 100, jobCountTarget, formatDuration(minDelay));
            return minDelay;
        }

        double ratio = 1.0 - (built / thresholdCount);
        long delayMs = minMs + Math.round(ratio * (maxMs - minMs));
        Duration delay = Duration.ofMillis(delayMs);
        log.debug("computeDelay: built={} below threshold {} -> ratio={} -> {} (between min {} and max {})",
                built, thresholdCount, String.format("%.3f", ratio),
                formatDuration(delay), formatDuration(minDelay), formatDuration(maxDelay));
        return delay;
    }

    private static String formatDuration(Duration d) {
        long totalMs = d.toMillis();
        long minutes = totalMs / 60_000;
        long seconds = (totalMs % 60_000) / 1000;
        long millis = totalMs % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}