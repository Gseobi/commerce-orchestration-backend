package io.github.gseobi.commerce.orchestration.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATE(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATE", "현재 주문 상태에서는 요청을 처리할 수 없습니다."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED", "결제 처리에 실패했습니다."),
    SETTLEMENT_REQUEST_FAILED(HttpStatus.BAD_REQUEST, "SETTLEMENT_REQUEST_FAILED", "정산 요청 처리에 실패했습니다."),
    NOTIFICATION_REQUEST_FAILED(HttpStatus.BAD_REQUEST, "NOTIFICATION_REQUEST_FAILED", "알림 요청 처리에 실패했습니다."),
    OUTBOX_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "OUTBOX_PUBLISH_FAILED", "Outbox publish에 실패했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
