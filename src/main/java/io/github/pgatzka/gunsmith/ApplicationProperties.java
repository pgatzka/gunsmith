package io.github.pgatzka.gunsmith;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gunsmith")
public class ApplicationProperties {

    private Api api;

    private Job job;

    @Data
    public static class Api {

        private String url;

        private Double permits;

        private Integer retries;

        private Duration backoff;

    }

    @Data
    public static class Job {

        private Duration minDelay;

        private Duration maxDelay;

        private Duration startupDelay;

        private Double slowdownThreshold;

        private Long target;

        private List<String> ignoredWeapons;

    }

}