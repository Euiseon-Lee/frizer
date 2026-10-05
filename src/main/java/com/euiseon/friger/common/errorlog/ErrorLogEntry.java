package com.euiseon.friger.common.errorlog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ErrorLogEntry(long id, OffsetDateTime occurredAt, UUID requestId, Long userId,
        String httpMethod, String requestPath, int httpStatus, String errorCode,
        String exceptionClass, String message, String stackTrace, String sessionState, String appVersion) { }
