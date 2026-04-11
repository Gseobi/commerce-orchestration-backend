package io.github.gseobi.commerce.orchestration.payment.client;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.config.PaymentProviderProperties;
import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import io.netty.channel.ChannelOption;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.provider", name = "mode", havingValue = "external")
public class ExternalPaymentProviderClient implements PaymentProviderClient {

    private final WebClient.Builder webClientBuilder;
    private final PaymentProviderProperties paymentProviderProperties;

    @Override
    public PaymentProviderResult approve(Long orderId, BigDecimal amount, String description) {
        return webClient()
                .post()
                .uri(paymentProviderProperties.approvePath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApprovePaymentRequest(orderId, amount, description))
                .retrieve()
                .bodyToMono(PaymentProviderResponse.class)
                .map(response -> new PaymentProviderResult(
                        resolveStatus(response.status()),
                        defaultReference(response.providerReference(), "EXT-PAYMENT-" + orderId),
                        defaultMessage(response.message(), "External payment processed")
                ))
                .onErrorMap(this::mapExternalError)
                .block(paymentProviderProperties.readTimeout());
    }

    @Override
    public PaymentProviderResult cancel(Long orderId, String reason) {
        return webClient()
                .post()
                .uri(paymentProviderProperties.cancelPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CancelPaymentRequest(orderId, reason))
                .retrieve()
                .bodyToMono(PaymentProviderResponse.class)
                .map(response -> new PaymentProviderResult(
                        resolveStatus(response.status()),
                        defaultReference(response.providerReference(), "EXT-CANCEL-" + orderId),
                        defaultMessage(response.message(), "External payment cancelled")
                ))
                .onErrorMap(this::mapExternalError)
                .block(paymentProviderProperties.readTimeout());
    }

    private WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(paymentProviderProperties.connectTimeout().toMillis())
                );

        WebClient.Builder builder = webClientBuilder
                .clone()
                .baseUrl(paymentProviderProperties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (paymentProviderProperties.apiKey() != null && !paymentProviderProperties.apiKey().isBlank()) {
            builder.defaultHeader("X-API-Key", paymentProviderProperties.apiKey());
        }

        return builder.build();
    }

    private Throwable mapExternalError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException responseException) {
            String message = "External payment provider error: status=%s body=%s"
                    .formatted(responseException.getStatusCode(), responseException.getResponseBodyAsString());
            return new BusinessException(ErrorCode.PAYMENT_FAILED, message);
        }
        if (throwable instanceof TimeoutException) {
            return new BusinessException(ErrorCode.PAYMENT_FAILED, "External payment provider timed out");
        }
        return new BusinessException(
                ErrorCode.PAYMENT_FAILED,
                "External payment provider request failed: " + throwable.getMessage()
        );
    }

    private PaymentStatus resolveStatus(String status) {
        if (status == null || status.isBlank()) {
            return PaymentStatus.FAILED;
        }
        return switch (status.trim().toUpperCase()) {
            case "APPROVED", "SUCCESS" -> PaymentStatus.APPROVED;
            case "CANCELLED", "CANCELED" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.FAILED;
        };
    }

    private String defaultReference(String providerReference, String fallback) {
        return Optional.ofNullable(providerReference)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    private String defaultMessage(String message, String fallback) {
        return Optional.ofNullable(message)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    private record ApprovePaymentRequest(
            Long orderId,
            BigDecimal amount,
            String description
    ) {
    }

    private record CancelPaymentRequest(
            Long orderId,
            String reason
    ) {
    }

    private record PaymentProviderResponse(
            String status,
            String providerReference,
            String message,
            Map<String, Object> metadata
    ) {
    }
}
