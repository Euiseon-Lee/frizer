package com.euiseon.friger.account;

import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Service;

@Service
public class AccountService implements UserDetailsService {
    public enum Role { ADMIN, USER }
    private final JdbcTemplate jdbc;
    public AccountService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public AccountPrincipal loadUserByUsername(String name) {
        var users = jdbc.query("SELECT user_id,login_id,password_hash,role,enabled,session_version FROM app_user WHERE login_id=?",
                (r,n) -> new AccountPrincipal(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getLong(6)), name);
        if (users.isEmpty()) throw new UsernameNotFoundException("계정을 찾을 수 없습니다.");
        return users.getFirst();
    }
    public AccountPrincipal findById(long id) {
        var users = jdbc.query("SELECT user_id,login_id,password_hash,role,enabled,session_version FROM app_user WHERE user_id=? AND enabled",
                (r,n) -> new AccountPrincipal(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),true,r.getLong(6)), id);
        return users.isEmpty() ? null : users.getFirst();
    }
    public static void validateCredentials(String name, String password) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("아이디를 입력해줘.");
        if (!name.equals(name.strip()))
            throw new IllegalArgumentException("아이디 앞뒤의 공백을 지워줘.");
        if (name.length()>100)
            throw new IllegalArgumentException("아이디는 100자 이내로 입력해줘.");
        if (password == null || password.length()<10)
            throw new IllegalArgumentException("비밀번호는 10자 이상으로 입력해줘.");
        if (password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("비밀번호가 너무 길어. 조금 더 짧게 입력해줘.");
    }
    static String hash(String password) { return PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password); }
}
