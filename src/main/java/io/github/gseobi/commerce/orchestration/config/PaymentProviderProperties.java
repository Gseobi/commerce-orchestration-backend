package io.github.gseobi.commerce.orchestration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.provider")
public record PaymentProviderProperties(
        String mode,
        String baseUrl,
        String apiKey,
        String approvePath,
        String cancelPath,
        Duration connectTimeout,
        Duration readTimeout,
        String mockFailureToken
) {
}
