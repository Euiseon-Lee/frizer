package com.euiseon.friger.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${frizer.security.enabled:true}") boolean enabled, com.euiseon.friger.account.AccountService accounts,
            com.euiseon.friger.common.errorlog.ErrorLogRecorder errorLogs) throws Exception {
        if (!enabled) {
            // Explicit opt-out for the loopback-only local development profile and isolated tests.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(csrf -> csrf.disable());
        } else {
            var denied = new org.springframework.security.web.access.AccessDeniedHandlerImpl();
            denied.setErrorPage("/access-denied");
            var loginFailure = new org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler("/login?error");
            http.addFilterBefore(new com.euiseon.friger.account.AccountSessionFilter(accounts), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .addFilterBefore(new org.springframework.web.filter.OncePerRequestFilter() {
                    @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain)
                            throws jakarta.servlet.ServletException, java.io.IOException {
                        errorLogs.captureIdentity(request);
                        chain.doFilter(request, response);
                    }
                }, org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/login/csrf", "/health", "/css/**", "/assets/**", "/favicon.ico", "/apple-touch-icon.png", "/js/choco-selector.js", "/js/choco.js", "/js/notices.js", "/js/login.js", "/error", "/access-denied").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").successHandler(new RoleBasedLoginSuccessHandler())
                    .failureHandler((request, response, error) -> {
                        errorLogs.mark(request, "AUTHENTICATION_FAILED", 302, error);
                        loginFailure.onAuthenticationFailure(request, response, error);
                    }).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, error) -> {
                    String code = error instanceof org.springframework.security.web.csrf.MissingCsrfTokenException ? "CSRF_MISSING"
                            : error instanceof org.springframework.security.web.csrf.InvalidCsrfTokenException ? "CSRF_INVALID" : "ACCESS_DENIED";
                    if (error instanceof org.springframework.security.web.csrf.CsrfException
                            && "POST".equals(request.getMethod())
                            && (request.getContextPath() + "/login").equals(request.getRequestURI())) {
                        errorLogs.mark(request, code, 302, error);
                        response.sendRedirect(request.getContextPath() + "/login?expired");
                    } else {
                        errorLogs.mark(request, code, 403, error);
                        denied.handle(request, response, error);
                    }
                }));
        }
        return http.build();
    }
}
