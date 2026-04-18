@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::api",
                "audit::api",
                "order::api",
                "notification::api",
                "outbox::api"
        }
)
package io.github.gseobi.commerce.orchestration.admin;
