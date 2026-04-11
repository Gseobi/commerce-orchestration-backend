package io.github.gseobi.commerce.orchestration.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record TokenRequest(
        @NotBlank(message = "username은 필수입니다.")
        String username,
        List<String> roles
) {
}
