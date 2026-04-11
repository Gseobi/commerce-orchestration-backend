package io.github.gseobi.commerce.orchestration.outbox.scheduler;

import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.outbox.service.OutboxPublisherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "scheduler-enabled", havingValue = "true")
public class OutboxPublishScheduler {

    private final OutboxPublisherService outboxPublisherService;
    private final OutboxProperties outboxProperties;

    public OutboxPublishScheduler(
            OutboxPublisherService outboxPublisherService,
            OutboxProperties outboxProperties
    ) {
        this.outboxPublisherService = outboxPublisherService;
        this.outboxProperties = outboxProperties;
    }

    @Scheduled(fixedDelayString = "#{@outboxProperties.publishFixedDelay()}")
    public void publishReadyEvents() {
        outboxPublisherService.publishReadyEvents(outboxProperties.batchSize());
    }
}
