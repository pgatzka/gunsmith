package io.github.pgatzka.gunsmith;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gunsmith")
public class ApplicationProperties {

    private Long cacheTimeToLiveMinutes;

    private Integer maxQueryBatchSize;

    private Integer apolloMaxAttempts;

    private Long apolloInitialBackoffMilliseconds;

    private Double permitsPerMinute;

    private String apiUrl;

    private List<String> ignoredWeapons;

    private Integer batchSize;

    private Duration interval;

}
