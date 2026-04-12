package io.github.gseobi.commerce.orchestration.integration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
public abstract class TestcontainersIntegrationSupport {

    protected static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("commerce_orchestration_test")
                    .withUsername("commerce")
                    .withPassword("commerce");

    protected static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    static {
        POSTGRESQL_CONTAINER.start();
        KAFKA_CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
    }

    @BeforeAll
    static void createTopics() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers()
        ))) {
            adminClient.createTopics(java.util.List.of(
                    new NewTopic("commerce.settlement.requested", 1, (short) 1),
                    new NewTopic("commerce.notification.requested", 1, (short) 1)
            )).all().get();
        } catch (Exception ignored) {
            // Topic creation is best-effort because brokers may auto-create them.
        }
    }

    protected KafkaConsumer<String, String> createConsumer(String groupId) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId + "-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true"
        ));
    }

    protected Duration awaitTimeout() {
        return Duration.ofSeconds(10);
    }
}
