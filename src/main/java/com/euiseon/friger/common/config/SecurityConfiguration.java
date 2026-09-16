package com.euiseon.friger.common.config;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    UserDetailsService personalUser(
            @Value("${frizer.security.enabled:true}") boolean enabled,
            @Value("${FRIZER_LOGIN_USERNAME:frizer}") String username,
            @Value("${FRIZER_LOGIN_PASSWORD:}") String password) {
        if (!enabled) return new InMemoryUserDetailsManager();
        if (username.isBlank() || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Set FRIZER_LOGIN_USERNAME and FRIZER_LOGIN_PASSWORD (at least 12 characters, at most 72 UTF-8 bytes).");
        }
        var encoded = new BCryptPasswordEncoder().encode(password);
        return new InMemoryUserDetailsManager(User.withUsername(username).password("{bcrypt}" + encoded).roles("OWNER").build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${frizer.security.enabled:true}") boolean enabled) throws Exception {
        if (!enabled) {
            // Explicit opt-out for the loopback-only local development profile and isolated tests.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(csrf -> csrf.disable());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/health", "/css/**", "/assets/**", "/js/choco-selector.js", "/js/choco.js", "/js/notices.js", "/error", "/access-denied").permitAll()
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
                .exceptionHandling(errors -> errors.accessDeniedPage("/access-denied"));
        }
        return http.build();
    }
}
