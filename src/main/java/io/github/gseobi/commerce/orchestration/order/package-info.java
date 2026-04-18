@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common",
                "common::api",
                "common::error",
                "payment::api",
                "settlement::api",
                "notification::api"
        }
)
package io.github.gseobi.commerce.orchestration.order;
