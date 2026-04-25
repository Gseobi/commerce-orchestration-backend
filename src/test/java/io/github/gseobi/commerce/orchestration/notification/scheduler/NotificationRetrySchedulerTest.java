package io.github.gseobi.commerce.orchestration.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gseobi.commerce.orchestration.config.SchedulingConfig;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryProcessingResult;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetrySchedulerTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class NotificationRetrySchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void doesNotRegisterSchedulerBeanWhenPropertyIsMissing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(NotificationRetryScheduler.class));
    }

    @Test
    void doesNotRegisterSchedulerBeanWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("notification.retry.scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NotificationRetryScheduler.class));
    }

    @Test
    void registersSchedulerBeanWhenPropertyIsTrue() {
        contextRunner
                .withPropertyValues("notification.retry.scheduler.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(NotificationRetryScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SchedulingConfig.class, NotificationRetryScheduler.class})
    static class TestConfiguration {

        @Bean
        NotificationRetrySchedulerTrigger notificationRetrySchedulerTrigger() {
            return new NoOpNotificationRetryProcessor();
        }
    }

    static class NoOpNotificationRetryProcessor implements NotificationRetrySchedulerTrigger {

        private static final NotificationRetryProcessingResult EMPTY_RESULT =
                NotificationRetryProcessingResult.completed(0, 0, 0, 0, java.util.List.of());

        @Override
        public NotificationRetryProcessingResult processDueRetryEvents() {
            return EMPTY_RESULT;
        }
    }
}
