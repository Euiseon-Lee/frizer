package com.euiseon.friger.account;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Ownership always uses this immutable database identifier, never the login name. */
public final class AccountPrincipal extends User {
    private final long userId;
    private final long sessionVersion;
    public AccountPrincipal(long userId, String loginId, String hash, String role, boolean enabled, long sessionVersion) {
        super(loginId, hash, enabled, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.userId = userId;
        this.sessionVersion = sessionVersion;
    }
    public long userId() { return userId; }
    public long sessionVersion() { return sessionVersion; }
}
