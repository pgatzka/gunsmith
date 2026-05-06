package io.github.pgatzka.gunsmith.batch.config;

import io.github.pgatzka.gunsmith.batch.GenerateBuildProcessor;
import io.github.pgatzka.gunsmith.batch.GenerateBuildReader;
import io.github.pgatzka.gunsmith.batch.GenerateBuildWriter;
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
    Step buildStep(JobRepository jobRepository, GenerateBuildReader generateBuildReader, GenerateBuildProcessor generateBuildProcessor, GenerateBuildWriter generateBuildWriter) {
        return new StepBuilder(jobRepository)
                .<BuildSettings, BuildResult>chunk(50)
                .reader(generateBuildReader)
                .processor(generateBuildProcessor)
                .writer(generateBuildWriter)
                .build();
    }

}
