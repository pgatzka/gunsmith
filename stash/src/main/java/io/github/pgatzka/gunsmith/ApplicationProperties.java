package io.github.pgatzka.gunsmith;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gunsmith")
public class ApplicationProperties {



}
