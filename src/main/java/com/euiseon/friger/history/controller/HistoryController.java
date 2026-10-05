package com.euiseon.friger.history.controller;

import com.euiseon.friger.history.dao.HistoryDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HistoryController {
    private final HistoryDao history;
    private final java.time.Clock clock;
    private static final java.time.ZoneId SEOUL = java.time.ZoneId.of("Asia/Seoul");
    public HistoryController(HistoryDao history, java.time.Clock clock) { this.history = history; this.clock = clock; }
    @GetMapping("/history")
    String history(@RequestParam(defaultValue = "100") String limit,
                   @RequestParam(defaultValue = "") String q,
                   @RequestParam(defaultValue = "") String start,
                   @RequestParam(defaultValue = "") String end,
                   @RequestParam(defaultValue = "") String period, Model model) {
        String selectedLimit = java.util.Set.of("all", "50", "100", "300", "500").contains(limit) ? limit : "100";
        String query = q.strip();
        if (query.length() > 100) query = query.substring(0, 100);
        var today = java.time.LocalDate.now(clock.withZone(SEOUL));
        if (period.equals("all")) { start = ""; end = ""; }
        else if (period.equals("7") || period.equals("30")) {
            start = today.minusDays(Integer.parseInt(period) - 1).toString();
            end = today.toString();
        }
        java.time.LocalDate from = null, through = null;
        String dateError = null;
        try {
            if (!start.isBlank()) from = parseDate(start);
            if (!end.isBlank()) through = parseDate(end);
            if (from != null && through != null && from.isAfter(through))
                dateError = "시작일은 종료일보다 늦을 수 없어.";
        } catch (java.time.DateTimeException e) {
            dateError = "날짜를 올바르게 입력해줘.";
        }
        model.addAttribute("entries", dateError != null ? java.util.List.of() : history.findFiltered(
                selectedLimit.equals("all") ? null : Integer.valueOf(selectedLimit), query,
                from == null ? null : from.atStartOfDay(SEOUL).toOffsetDateTime(),
                through == null ? null : through.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime()));
        String selectedPeriod = "custom";
        if (start.isBlank() && end.isBlank() && !period.equals("custom")) selectedPeriod = "all";
        else if (!period.equals("custom") && dateError == null && today.equals(through)) {
            if (today.minusDays(6).equals(from)) selectedPeriod = "7";
            else if (today.minusDays(29).equals(from)) selectedPeriod = "30";
        }
        model.addAttribute("selectedPeriod", selectedPeriod);
        model.addAttribute("historyToday", today);
        model.addAttribute("selectedLimit", selectedLimit);
        model.addAttribute("query", query);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("dateError", dateError);
        model.addAttribute("filtered", !query.isEmpty() || !start.isBlank() || !end.isBlank());
        return "history/list";
    }

    private static java.time.LocalDate parseDate(String value) {
        var date = java.time.LocalDate.parse(value);
        if (date.getYear() < 1 || date.getYear() > 9999) throw new java.time.DateTimeException("Invalid year");
        return date;
    }
}
