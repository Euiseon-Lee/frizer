package com.euiseon.friger.common.errorlog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErrorLogStore {
    private final JdbcTemplate jdbc;
    public ErrorLogStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 3)
    public void save(ErrorLogEntry e) {
        jdbc.update("""
            INSERT INTO application_error_log
            (occurred_at, request_id, user_id, http_method, request_path, http_status, error_code,
             exception_class, message, stack_trace, session_state, app_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (request_id) DO NOTHING
            """, e.occurredAt(), e.requestId(), e.userId(), e.httpMethod(), e.requestPath(),
                e.httpStatus(), e.errorCode(), e.exceptionClass(), e.message(), e.stackTrace(), e.sessionState(), e.appVersion());
    }
}
