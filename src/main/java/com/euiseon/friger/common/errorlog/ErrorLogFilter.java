package com.euiseon.friger.common.errorlog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ErrorLogFilter extends OncePerRequestFilter {
    private final ErrorLogRecorder recorder;
    private final org.springframework.boot.web.servlet.error.ErrorAttributes errors;
    public ErrorLogFilter(ErrorLogRecorder recorder, org.springframework.boot.web.servlet.error.ErrorAttributes errors) {
        this.recorder = recorder;
        this.errors = errors;
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        recorder.captureIdentity(request);
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException error) {
            recorder.mark(request, "SERVER_ERROR", 500, error);
            throw error;
        } finally {
            if (response.getStatus() >= 500) {
                recorder.mark(request, "SERVER_ERROR", response.getStatus(),
                        errors.getError(new org.springframework.web.context.request.ServletWebRequest(request)));
            }
            recorder.complete(request, response.getStatus());
        }
    }
}
