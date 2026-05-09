package io.github.pgatzka.gunsmith.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobConfiguration {

    @Bean
    Job generateJob(JobRepository jobRepository, Step generateStep) {
        return new JobBuilder(jobRepository)
                .start(generateStep)
                .build();
    }


}
