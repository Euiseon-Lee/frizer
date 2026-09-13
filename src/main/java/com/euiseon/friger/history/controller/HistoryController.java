package com.euiseon.friger.history.controller;

import com.euiseon.friger.history.service.HistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistoryController {
    private final HistoryService service;
    public HistoryController(HistoryService service) { this.service = service; }
    @GetMapping("/history")
    String history(Model model) {
        model.addAttribute("entries", service.recent(100));
        return "history/list";
    }
}
