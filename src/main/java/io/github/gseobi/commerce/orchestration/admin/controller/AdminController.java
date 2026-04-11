package io.github.gseobi.commerce.orchestration.admin.controller;

import io.github.gseobi.commerce.orchestration.admin.api.AdminReprocessingFacade;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminNotificationReprocessResponse;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminOutboxReprocessResponse;
import io.github.gseobi.commerce.orchestration.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminReprocessingFacade adminReprocessingFacade;

    @PostMapping("/notification-events/{notificationEventId}/retry")
    public ApiResponse<AdminNotificationReprocessResponse> retryNotification(
            @PathVariable Long notificationEventId
    ) {
        return ApiResponse.success(adminReprocessingFacade.retryNotification(notificationEventId));
    }

    @PostMapping("/notification-events/{notificationEventId}/ignore")
    public ApiResponse<AdminNotificationReprocessResponse> ignoreNotification(
            @PathVariable Long notificationEventId
    ) {
        return ApiResponse.success(adminReprocessingFacade.ignoreNotification(notificationEventId));
    }

    @PostMapping("/outbox-events/{outboxEventId}/retry")
    public ApiResponse<AdminOutboxReprocessResponse> retryOutboxDeadLetter(
            @PathVariable Long outboxEventId
    ) {
        return ApiResponse.success(adminReprocessingFacade.retryOutboxDeadLetter(outboxEventId));
    }
}
