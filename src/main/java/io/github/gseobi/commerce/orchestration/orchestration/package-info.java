@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"common", "infrastructure", "order", "payment", "settlement", "notification", "audit", "outbox"}
)
package io.github.gseobi.commerce.orchestration.orchestration;
