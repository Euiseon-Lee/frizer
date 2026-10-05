package com.euiseon.friger.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/** Role landing pages take precedence over a page visited before login. */
public class RoleBasedLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    public RoleBasedLoginSuccessHandler() {
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return admin ? "/admin" : "/";
    }
}
