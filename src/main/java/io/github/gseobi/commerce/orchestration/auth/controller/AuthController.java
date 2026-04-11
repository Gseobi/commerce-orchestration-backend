package io.github.gseobi.commerce.orchestration.auth.controller;

import io.github.gseobi.commerce.orchestration.auth.dto.request.TokenRequest;
import io.github.gseobi.commerce.orchestration.auth.dto.response.TokenResponse;
import io.github.gseobi.commerce.orchestration.common.api.ApiResponse;
import io.github.gseobi.commerce.orchestration.security.JwtTokenProvider;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/token")
    public ApiResponse<TokenResponse> issueToken(@Valid @RequestBody TokenRequest request) {
        List<String> roles = request.roles() == null || request.roles().isEmpty()
                ? List.of("ROLE_USER")
                : request.roles();
        String accessToken = jwtTokenProvider.createAccessToken(request.username(), roles);
        return ApiResponse.success(new TokenResponse("Bearer", accessToken, roles));
    }
}
