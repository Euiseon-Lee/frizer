package com.euiseon.friger.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One-time claim of V15's locked legacy owner; never resets an existing password. */
@Component
public class LegacyAccountBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final String name;
    private final String password;
    public LegacyAccountBootstrap(JdbcTemplate jdbc, @Value("${frizer.security.enabled:true}") boolean enabled,
            @Value("${FRIZER_LOGIN_USERNAME:frizer}") String name, @Value("${FRIZER_LOGIN_PASSWORD:}") String password) {
        this.jdbc=jdbc; this.enabled=enabled; this.name=name; this.password=password;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (!enabled) return;
        var pending=jdbc.queryForObject("SELECT password_hash IS NULL FROM app_user WHERE user_id=1 FOR UPDATE",Boolean.class);
        if (!Boolean.TRUE.equals(pending)) return;
        AccountService.validateCredentials(name,password);
        jdbc.update("UPDATE app_user SET login_id=?,password_hash=?,role='USER',enabled=true WHERE user_id=1 AND password_hash IS NULL",
                name,AccountService.hash(password));
    }
}
