package io.github.gseobi.commerce.orchestration.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank(message = "customerId는 필수입니다.")
        String customerId,
        @NotNull(message = "totalAmount는 필수입니다.")
        @Positive(message = "totalAmount는 0보다 커야 합니다.")
        BigDecimal totalAmount,
        @NotBlank(message = "currency는 필수입니다.")
        String currency,
        String description
) {
}
