package com.euiseon.friger.history.controller;

import com.euiseon.friger.history.dao.HistoryDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistoryController {
    private final HistoryDao history;
    public HistoryController(HistoryDao history) { this.history = history; }
    @GetMapping("/history")
    String history(Model model) {
        model.addAttribute("entries", history.findRecent(100));
        return "history/list";
    }
}
