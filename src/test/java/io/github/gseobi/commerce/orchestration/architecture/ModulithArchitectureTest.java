package io.github.gseobi.commerce.orchestration.architecture;

import io.github.gseobi.commerce.orchestration.CommerceOrchestrationBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(CommerceOrchestrationBackendApplication.class).verify();
    }
}
