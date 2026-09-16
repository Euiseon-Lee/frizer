package com.euiseon.friger.common.config;

import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AccessModelAdvice {
    private final boolean enabled;
    public AccessModelAdvice(@Value("${frizer.security.enabled:true}") boolean enabled) { this.enabled = enabled; }
    @ModelAttribute("accessEnabled")
    boolean accessEnabled() { return enabled; }
    @ModelAttribute("signedIn")
    boolean signedIn(Principal principal) { return principal != null; }
}
