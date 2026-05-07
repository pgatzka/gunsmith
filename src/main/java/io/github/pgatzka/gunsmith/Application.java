package io.github.pgatzka.gunsmith;

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
        jobOperator.start(buildJob, new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis(), true)
                .addLong("count", properties.getBatchSize().longValue())
                .toJobParameters()
        );
    }

}
