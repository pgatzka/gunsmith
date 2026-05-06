package io.github.pgatzka.gunsmith;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@EnableScheduling
@RequiredArgsConstructor
@EnableJpaAuditing
@SpringBootApplication
public class Application {

    private final JobOperator jobOperator;
    private final Job buildJob;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Value("${gunsmith.job.count}")
    private long count;

    @Scheduled(cron = "${gunsmith.job.cron}")
    public void scheduled() throws Exception {
        jobOperator.start(buildJob, new JobParametersBuilder().addLong("count", count).addLong("timestamp", System.currentTimeMillis()).toJobParameters());
    }
}
