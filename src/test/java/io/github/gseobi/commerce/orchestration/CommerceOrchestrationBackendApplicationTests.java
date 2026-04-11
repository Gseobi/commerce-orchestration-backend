package io.github.gseobi.commerce.orchestration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
		"spring.docker.compose.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class CommerceOrchestrationBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
