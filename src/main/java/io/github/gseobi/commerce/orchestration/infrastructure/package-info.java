@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common",
                "config",
                "outbox::api"
        }
)
package io.github.gseobi.commerce.orchestration.infrastructure;
