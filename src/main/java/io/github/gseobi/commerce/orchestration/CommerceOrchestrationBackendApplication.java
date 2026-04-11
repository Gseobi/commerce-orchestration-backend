package io.github.gseobi.commerce.orchestration;

import io.github.gseobi.commerce.orchestration.config.AppSecurityProperties;
import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.config.PaymentProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppSecurityProperties.class, OutboxProperties.class, PaymentProviderProperties.class})
public class CommerceOrchestrationBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceOrchestrationBackendApplication.class, args);
    }

}
