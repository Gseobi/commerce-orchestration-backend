@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common",
                "config",
                "outbox::api",
                "outbox::entity"
        }
)
package io.github.gseobi.commerce.orchestration.infrastructure;
