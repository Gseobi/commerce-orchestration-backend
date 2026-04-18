@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common",
                "common::error",
                "infrastructure",
                "order::api",
                "payment::api",
                "settlement::api",
                "notification::api",
                "audit::api",
                "outbox::api"
        }
)
package io.github.gseobi.commerce.orchestration.orchestration;
