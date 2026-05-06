package io.github.pgatzka.gunsmith.batch.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobConfiguration {

    @Bean
    Job buildJob(JobRepository jobRepository, Step buildStep) {
        return new JobBuilder(jobRepository)
                .start(buildStep)
                .build();
    }

}
