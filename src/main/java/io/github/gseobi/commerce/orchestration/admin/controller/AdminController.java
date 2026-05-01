package io.github.gseobi.commerce.orchestration.admin.controller;

import io.github.gseobi.commerce.orchestration.admin.api.AdminReprocessingFacade;
import io.github.gseobi.commerce.orchestration.admin.api.AdminRecoveryContext;
import io.github.gseobi.commerce.orchestration.admin.dto.request.AdminRecoveryRequest;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminNotificationReprocessResponse;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminOutboxReprocessResponse;
import io.github.gseobi.commerce.orchestration.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminReprocessingFacade adminReprocessingFacade;

    @PostMapping("/notification-events/{notificationEventId}/retry")
    public ApiResponse<AdminNotificationReprocessResponse> retryNotification(
            @PathVariable Long notificationEventId,
            @RequestBody(required = false) AdminRecoveryRequest request
    ) {
        return ApiResponse.success(adminReprocessingFacade.retryNotification(
                notificationEventId,
                recoveryContext(request)
        ));
    }

    @PostMapping("/notification-events/{notificationEventId}/ignore")
    public ApiResponse<AdminNotificationReprocessResponse> ignoreNotification(
            @PathVariable Long notificationEventId,
            @RequestBody(required = false) AdminRecoveryRequest request
    ) {
        return ApiResponse.success(adminReprocessingFacade.ignoreNotification(
                notificationEventId,
                recoveryContext(request)
        ));
    }

    @PostMapping("/outbox-events/{outboxEventId}/retry")
    public ApiResponse<AdminOutboxReprocessResponse> retryOutboxDeadLetter(
            @PathVariable Long outboxEventId,
            @RequestBody(required = false) AdminRecoveryRequest request
    ) {
        return ApiResponse.success(adminReprocessingFacade.retryOutboxDeadLetter(
                outboxEventId,
                recoveryContext(request)
        ));
    }

    private AdminRecoveryContext recoveryContext(AdminRecoveryRequest request) {
        return request == null
                ? AdminRecoveryContext.defaults()
                : request.toContext();
    }
}
