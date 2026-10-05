package com.euiseon.friger.common.errorlog;

import com.euiseon.friger.account.AccountPrincipal;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

/** Never reads credentials, headers, parameters, or arbitrary exception messages. */
@Component
public class ErrorLogRecorder {
    private static final Logger log = LoggerFactory.getLogger(ErrorLogRecorder.class);
    private static final String STATE = ErrorLogRecorder.class.getName();
    private final ErrorLogStore store;
    private final String version;

    public ErrorLogRecorder(ErrorLogStore store, @Value("${frizer.error-log.app-version:unknown}") String version) {
        this.store = store;
        this.version = limit(version, 64);
    }

    private static final class State {
        final UUID id = UUID.randomUUID();
        final OffsetDateTime time = OffsetDateTime.now(ZoneOffset.UTC);
        Long userId;
        String session;
        String code;
        String path;
        Throwable error;
        int status;
        boolean saved;
    }

    private State state(HttpServletRequest request) {
        var state = (State) request.getAttribute(STATE);
        if (state == null) {
            state = new State();
            state.session = request.getRequestedSessionId() == null ? "NONE"
                    : request.isRequestedSessionIdValid() ? "VALID" : "INVALID";
            request.setAttribute(STATE, state);
        }
        return state;
    }

    public void captureIdentity(HttpServletRequest request) {
        var state = state(request);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal user) state.userId = user.userId();
    }

    public void mark(HttpServletRequest request, String code, int status, Throwable error) {
        captureIdentity(request);
        var state = state(request);
        if (state.code == null || (status >= 500 && state.status < 500)) {
            state.code = code;
            state.path = safePath(request);
            state.status = status;
            state.error = error;
        }
    }

    public void complete(HttpServletRequest request, int status) {
        var state = state(request);
        if (state.saved) return;
        if (state.code == null && status >= 500) {
            mark(request, "SERVER_ERROR", status, (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        }
        if (state.code == null) return;
        state.saved = true; // Also prevents recursive/error-dispatch logging after persistence fails.
        var entry = new ErrorLogEntry(0, state.time, state.id, state.userId,
                limit(request.getMethod(), 10), state.path, state.status, state.code,
                state.error == null ? null : limit(state.error.getClass().getName(), 255),
                message(state.code), safeStack(state.error), state.session, version);
        try {
            store.save(entry);
        } catch (RuntimeException failure) {
            // Do not print the persistence exception: SQL/driver messages can contain secrets.
            log.error("error_log_write_failed requestId={} code={} status={} path={} userId={} session={} version={} storageError={} trace={}",
                    entry.requestId(), entry.errorCode(), entry.httpStatus(), entry.requestPath(), entry.userId(),
                    entry.sessionState(), entry.appVersion(), failure.getClass().getName(), entry.stackTrace());
        }
    }

    private static String safePath(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null && !pattern.toString().equals("/error")) return limit(pattern.toString(), 512);
        // Security failures occur before MVC routing. Keep only known route characters;
        // unknown paths are omitted to avoid retaining tokens or personal text in URLs.
        String path = request.getRequestURI();
        if (path.matches("/(login|logout|admin(?:/error-logs(?:/[0-9]+)?)?|inventory(?:/[0-9]+)?|account|health|access-denied)")) return path;
        if (path.matches("/inventory/(new|bulk(?:/(template|preview|commit))?)")) return path;
        if (path.matches("/(inventory|foods)/[0-9]+(?:/(edit|warning|split|quantity|move|delete)(?:/preview)?)?")) {
            return path.replaceFirst("/[0-9]+", "/{id}");
        }
        return "[unmapped]";
    }

    private static String message(String code) {
        return switch (code) {
            case "CSRF_MISSING" -> "요청 검증 토큰을 확인할 세션 정보가 없습니다.";
            case "CSRF_INVALID" -> "요청 검증 토큰이 일치하지 않습니다.";
            case "ACCESS_DENIED" -> "요청 권한이 없습니다.";
            case "AUTHENTICATION_FAILED" -> "로그인 인증에 실패했습니다.";
            default -> "서버 요청 처리 중 오류가 발생했습니다. 예외 클래스와 호출 위치를 확인하세요.";
        };
    }

    static String safeStack(Throwable error) {
        if (error == null) return null;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var out = new StringBuilder();
        for (int depth = 0; error != null && depth < 8 && seen.add(error); depth++, error = error.getCause()) {
            out.append(depth == 0 ? "" : "Caused by: ").append(error.getClass().getName()).append('\n');
            for (var frame : error.getStackTrace()) {
                out.append("  at ").append(frame).append('\n');
                if (out.length() >= 16000) return limit(out.toString(), 16000);
            }
        }
        return out.toString();
    }

    private static String limit(String value, int size) { return value.substring(0, Math.min(size, value.length())); }
}
