package io.github.gseobi.commerce.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        boolean schedulerEnabled,
        long publishFixedDelay,
        int batchSize
) {
}
