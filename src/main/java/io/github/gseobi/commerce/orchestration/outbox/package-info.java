@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common",
                "common::error",
                "common::metrics",
                "config"
        }
)
package io.github.gseobi.commerce.orchestration.outbox;
