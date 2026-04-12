package io.github.gseobi.commerce.orchestration.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:integration-schema-smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.url=jdbc:h2:mem:integration-schema-smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.user=sa",
        "spring.flyway.password=",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "app.outbox.scheduler-enabled=false"
})
class IntegrationSchemaValidationSmokeTest {

    @Test
    void contextLoadsWithFlywayAndValidate() {
    }
}
