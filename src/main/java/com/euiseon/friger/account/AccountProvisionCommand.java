package com.euiseon.friger.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit operator-only, non-web command. No public signup or password reset endpoint. */
@Component
@ConditionalOnNotWebApplication
@ConditionalOnProperty(name="frizer.account-provision.enabled",havingValue="true")
public class AccountProvisionCommand implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final String login;
    private final String password;
    public AccountProvisionCommand(JdbcTemplate jdbc,@Value("${FRIZER_NEW_LOGIN_ID}") String login,
            @Value("${FRIZER_NEW_PASSWORD}") String password) { this.jdbc=jdbc;this.login=login;this.password=password; }
    @Override @Transactional public void run(ApplicationArguments args) {
        AccountService.validateCredentials(login,password);
        // Existing accounts are never overwritten, activated, or silently given a new password.
        int created=jdbc.update("INSERT INTO app_user(login_id,password_hash,role,enabled) VALUES(?,?,'USER',true) ON CONFLICT(login_id) DO NOTHING",login,AccountService.hash(password));
        if(created==0)
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
    }
}
