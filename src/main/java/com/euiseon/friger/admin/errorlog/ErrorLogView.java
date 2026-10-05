package com.euiseon.friger.admin.errorlog;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model enriched with the account's current login ID; never contains credentials. */
public record ErrorLogView(long id, OffsetDateTime occurredAt, UUID requestId, Long userId,
        String httpMethod, String requestPath, int httpStatus, String errorCode,
        String exceptionClass, String message, String stackTrace, String sessionState, String appVersion,
        String loginId) {
    public String userLabel() {
        return loginId != null ? loginId : userId == null ? "미확인" : "계정 없음 (#" + userId + ")";
    }
}
