package io.github.gseobi.commerce.orchestration.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("integration-test")
@Tag("integration")
class IntegrationSchemaValidationSmokeTest extends TestcontainersIntegrationSupport {

    @Test
    void contextLoadsWithFlywayAndValidate() {
    }
}
