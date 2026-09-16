package com.euiseon.friger.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final boolean enabled;
    public CurrentUser(@Value("${frizer.security.enabled:true}") boolean enabled) { this.enabled = enabled; }
    public long id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountPrincipal user)
            return user.userId();
        // Existing isolated business tests explicitly disable the web security chain.
        if (!enabled) return 1L;
        throw new AccessDeniedException("로그인이 필요합니다.");
    }
}
