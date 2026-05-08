package io.github.pgatzka.gunsmith;

import io.github.pgatzka.gunsmith.data.repository.BuildRepository;
import io.github.pgatzka.gunsmith.service.AttachmentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

@Slf4j
@EnableScheduling
@RequiredArgsConstructor
@EnableJpaAuditing
@SpringBootApplication
@EnableConfigurationProperties({ApplicationProperties.class})
public class Application {

    private final JobOperator jobOperator;

    private final Job buildJob;

    private final ApplicationProperties properties;

    private final BuildRepository buildRepository;
    private final AttachmentService attachmentService;

    /**
     * Build-table size sampled just before the most recent job started, or -1 when none.
     */
    private long countBeforeLastJob = -1;

    /**
     * True between starting a job and evaluating its outcome on the next tick.
     */
    private boolean awaitingEvaluation = false;

    /**
     * Cycles to skip before running again, set after observing low productivity.
     */
    private int skipsRemaining = 0;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @PostConstruct
    public void logProjection() {
        Integer batchSize = properties.getBatchSize();
        Duration interval = properties.getInterval();

        double batchesPerHour = 3600.0 / interval.toMillis() * 1000.0;
        long buildsPerHour = Math.round(batchSize * batchesPerHour);
        log.info("Generating {} builds per hour", String.format("%,d", buildsPerHour));
    }

    @Scheduled(fixedRateString = "${gunsmith.interval}")
    public void generateBuilds() throws Exception {
        // Phase 1: still serving a backoff initiated by a prior low-productivity cycle.
        if (skipsRemaining > 0) {
            skipsRemaining--;
            log.debug("Skipping cycle ({} skips remaining)", skipsRemaining);
            return;
        }

        // Phase 2: a job ran in a previous cycle and we haven't yet measured how many builds it added.
        // Use that delta to decide whether the next cycle(s) should be skipped.
        if (awaitingEvaluation) {
            long currentCount = buildRepository.count();
            long delta = currentCount - countBeforeLastJob;
            int skips = computeSkipsForDelta(delta);
            awaitingEvaluation = false;
            log.info("Last job added {} builds (target {}); backing off {} cycle(s)",
                    delta, properties.getBatchSize(), skips);
            if (skips > 0) {
                skipsRemaining = skips;
                return;
            }
            // High productivity — run the next job immediately, falling through to phase 3.
        }

        // Phase 3: run a job and remember the pre-run row count so the next tick can measure delta.
        countBeforeLastJob = buildRepository.count();
        awaitingEvaluation = true;
        jobOperator.start(buildJob, new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis(), true)
                .addString("id", "5447a9cd4bdc2dbd208b4567")
                .addLong("count", properties.getBatchSize().longValue())
                .toJobParameters()
        );
    }

    private int computeSkipsForDelta(long delta) {
        long target = properties.getBatchSize();
        if (target <= 0) return 0;
        double rate = (double) delta / target;
        if (rate >= 0.20) return 0;   // healthy: at least 20% landed, no slowdown
        if (rate >= 0.10) return 3;   // moderate
        if (rate >= 0.01) return 10;  // saturating
        return 30;                    // saturated
    }


}
