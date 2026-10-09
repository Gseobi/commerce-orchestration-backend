package io.github.gseobi.commerce.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.security")
public record SecurityModeProperties(@DefaultValue("DISABLED") Mode mode) {
    public enum Mode {
        DISABLED, DEMO, OIDC
    }
}
