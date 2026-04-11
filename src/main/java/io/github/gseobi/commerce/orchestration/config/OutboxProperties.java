package io.github.gseobi.commerce.orchestration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        boolean schedulerEnabled,
        Duration publishFixedDelay,
        int batchSize,
        int maxRetryCount,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration maxBackoff,
        Duration publishTimeout
) {
}
