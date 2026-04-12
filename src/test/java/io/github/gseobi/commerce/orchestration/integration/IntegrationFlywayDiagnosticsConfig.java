package io.github.gseobi.commerce.orchestration.integration;

import java.util.List;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@TestConfiguration(proxyBeanMethods = false)
class IntegrationFlywayDiagnosticsConfig {

    @Bean
    FlywayMigrationStrategy integrationFlywayMigrationStrategy(
            DataSource dataSource
    ) {
        return flyway -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            logClasspathMigrations(flyway);
            logDatabaseState(jdbcTemplate, "before");

            var result = flyway.migrate();
            log.info(
                    "integration-test flyway migrate completed: initialSchemaVersion={}, targetSchemaVersion={}, migrationsExecuted={}",
                    result.initialSchemaVersion,
                    result.targetSchemaVersion,
                    result.migrationsExecuted
            );
            logDatabaseState(jdbcTemplate, "after");
        };
    }

    private static void logClasspathMigrations(Flyway flyway) {
        List<String> migrations = java.util.Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getScript)
                .filter(java.util.Objects::nonNull)
                .toList();

        log.info("integration-test flyway classpath migrations: {}", migrations);
    }

    private static void logDatabaseState(JdbcTemplate jdbcTemplate, String phase) {
        boolean historyExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select exists (
                    select 1
                    from information_schema.tables
                    where table_schema = 'public'
                      and table_name = 'flyway_schema_history'
                )
                """, Boolean.class));

        log.info("integration-test database tables {} flyway migrate: {}", phase, jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                order by table_name
                """, String.class));

        if (!historyExists) {
            log.info("integration-test flyway_schema_history {} flyway migrate: <missing>", phase);
            return;
        }

        log.info("integration-test flyway_schema_history {} flyway migrate: {}", phase, jdbcTemplate.queryForList("""
                select version || ':' || script || ':' || success
                from flyway_schema_history
                order by installed_rank
                """, String.class));
    }
}
