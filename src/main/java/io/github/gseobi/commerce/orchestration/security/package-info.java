@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::api",
                "common::error",
                "common::web",
                "config"
        }
)
package io.github.gseobi.commerce.orchestration.security;
