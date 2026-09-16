package com.euiseon.friger.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Disabled accounts lose access on their next request; roles/login names refresh by immutable id. */
public class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountService accounts;
    public AccountSessionFilter(AccountService accounts) { this.accounts=accounts; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            var current=auth.getPrincipal() instanceof AccountPrincipal user ? accounts.findById(user.userId()) : null;
            if (current==null || !(auth.getPrincipal() instanceof AccountPrincipal previous)
                    || current.sessionVersion()!=previous.sessionVersion()) {
                SecurityContextHolder.clearContext();
                if(request.getSession(false)!=null) request.getSession(false).invalidate();
            } else {
                current.eraseCredentials();
                var refreshed=UsernamePasswordAuthenticationToken.authenticated(current,null,current.getAuthorities());
                refreshed.setDetails(auth.getDetails());
                SecurityContextHolder.getContext().setAuthentication(refreshed);
            }
        }
        chain.doFilter(request,response);
    }
}
