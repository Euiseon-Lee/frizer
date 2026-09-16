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
            @Value("${frizer.security.enabled:true}") boolean enabled, com.euiseon.friger.account.AccountService accounts) throws Exception {
        if (!enabled) {
            // Explicit opt-out for the loopback-only local development profile and isolated tests.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(csrf -> csrf.disable());
        } else {
            http.addFilterBefore(new com.euiseon.friger.account.AccountSessionFilter(accounts), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/health", "/css/**", "/assets/**", "/js/choco-selector.js", "/js/choco.js", "/js/notices.js", "/error", "/access-denied").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
                .exceptionHandling(errors -> errors.accessDeniedPage("/access-denied"));
        }
        return http.build();
    }
}
