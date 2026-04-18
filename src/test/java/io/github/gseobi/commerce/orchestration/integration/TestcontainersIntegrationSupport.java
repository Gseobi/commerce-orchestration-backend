package io.github.gseobi.commerce.orchestration.integration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@Import(IntegrationFlywayDiagnosticsConfig.class)
public abstract class TestcontainersIntegrationSupport {

    private static final Duration INTEGRATION_AWAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final java.util.List<String> TOPIC_NAMES = java.util.List.of(
            "commerce.settlement.requested",
            "commerce.notification.requested"
    );

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.flyway.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
    }

    @BeforeAll
    static void createTopics() {
        Awaitility.await()
                .atMost(INTEGRATION_AWAIT_TIMEOUT)
                .untilAsserted(TestcontainersIntegrationSupport::ensureTopicsReady);
    }

    @BeforeEach
    void resetIntegrationState() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
                    orchestration_steps,
                    outbox_events,
                    notification_events,
                    settlements,
                    payments,
                    orders
                RESTART IDENTITY CASCADE
                """);
        purgeKafkaTopics();
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
        return INTEGRATION_AWAIT_TIMEOUT;
    }

    private static void ensureTopicsReady() throws Exception {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers()
        ))) {
            try {
                adminClient.createTopics(TOPIC_NAMES.stream()
                        .map(topic -> new NewTopic(topic, 1, (short) 1))
                        .toList()).all().get();
            } catch (java.util.concurrent.ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) {
                    throw exception;
                }
            }
            adminClient.describeTopics(TOPIC_NAMES).allTopicNames().get();
        }
    }

    private static void purgeKafkaTopics() {
        Awaitility.await()
                .atMost(INTEGRATION_AWAIT_TIMEOUT)
                .untilAsserted(() -> {
                    ensureTopicsReady();
                    try (AdminClient adminClient = AdminClient.create(Map.of(
                            "bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers()
                    ))) {
                        Map<String, org.apache.kafka.clients.admin.TopicDescription> descriptions =
                                adminClient.describeTopics(TOPIC_NAMES).allTopicNames().get();

                        Map<TopicPartition, OffsetSpec> latestOffsets = new java.util.HashMap<>();
                        for (org.apache.kafka.clients.admin.TopicDescription description : descriptions.values()) {
                            description.partitions().forEach(partition -> latestOffsets.put(
                                    new TopicPartition(description.name(), partition.partition()),
                                    OffsetSpec.latest()
                            ));
                        }

                        Map<TopicPartition, RecordsToDelete> recordsToDelete = new java.util.HashMap<>();
                        adminClient.listOffsets(latestOffsets).all().get().forEach((topicPartition, result) ->
                                recordsToDelete.put(topicPartition, RecordsToDelete.beforeOffset(result.offset()))
                        );

                        if (!recordsToDelete.isEmpty()) {
                            adminClient.deleteRecords(recordsToDelete).all().get();
                        }
                    }
                });
    }
}
