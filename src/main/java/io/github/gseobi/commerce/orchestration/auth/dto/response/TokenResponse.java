package io.github.gseobi.commerce.orchestration.auth.dto.response;

import java.util.List;

public record TokenResponse(
        String tokenType,
        String accessToken,
        List<String> roles
) {
}
