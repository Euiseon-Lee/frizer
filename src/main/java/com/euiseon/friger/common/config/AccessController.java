package com.euiseon.friger.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AccessController {
    @GetMapping("/login")
    String login() { return "login"; }

    @GetMapping("/account")
    String account() { return "account"; }

    @RequestMapping("/access-denied")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String denied(Model model) {
        model.addAttribute("requestRejected", true);
        return "error";
    }

    @GetMapping("/health")
    @ResponseBody
    String health() { return "ok"; }
}
