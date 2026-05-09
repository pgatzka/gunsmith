package io.github.pgatzka.gunsmith.batch.config;

import io.github.pgatzka.gunsmith.batch.generate.GenerateProcessor;
import io.github.pgatzka.gunsmith.batch.generate.GenerateReader;
import io.github.pgatzka.gunsmith.batch.generate.GenerateWriter;
import io.github.pgatzka.gunsmith.batch.pojo.BuildResult;
import io.github.pgatzka.gunsmith.batch.pojo.BuildSettings;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StepConfiguration {

    @Bean
    public Step generateStep(JobRepository jobRepository, GenerateReader generateReader, GenerateProcessor generateProcessor, GenerateWriter generateWriter) {
        return new StepBuilder(jobRepository)
                .<BuildSettings, BuildResult>chunk(50)
                .reader(generateReader)
                .processor(generateProcessor)
                .writer(generateWriter)
                .build();
    }

}

