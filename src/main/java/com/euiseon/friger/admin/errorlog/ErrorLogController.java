package com.euiseon.friger.admin.errorlog;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class ErrorLogController {
    private final ErrorLogReadService logs;
    public ErrorLogController(ErrorLogReadService logs) { this.logs = logs; }

    @GetMapping("/admin")
    String index() { return "redirect:/admin/error-logs"; }

    @GetMapping("/admin/error-logs")
    String list(@RequestParam Map<String, String> params, Model model) {
        ErrorLogQuery query;
        try { query = ErrorLogQuery.parse(params); }
        catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            query = ErrorLogQuery.parse(Map.of());
            model.addAttribute("filterError", "검색 조건을 확인해주세요. 기간은 시작보다 종료가 늦어야 하며 최대 366일입니다. 상태 코드는 100~599, 사용자 ID는 100자 이내입니다.");
        }
        var result = model.containsAttribute("filterError") ? new ErrorLogReadService.Page(List.of(), 0) : logs.search(query);
        model.addAttribute("query", query);
        model.addAttribute("result", result);
        model.addAttribute("codes", ErrorLogQuery.CODES.stream().sorted().toList());
        model.addAttribute("previousUrl", query.page() > 0 ? query.url("/admin/error-logs", query.page() - 1) : null);
        model.addAttribute("nextUrl", (long) (query.page() + 1) * ErrorLogReadService.PAGE_SIZE < result.total()
                ? query.url("/admin/error-logs", query.page() + 1) : null);
        return "admin/error-logs";
    }

    @GetMapping("/admin/error-logs/{id}")
    String detail(@PathVariable long id, @RequestParam Map<String, String> params, Model model) {
        var entry = logs.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ErrorLogQuery query;
        try { query = ErrorLogQuery.parse(params); }
        catch (IllegalArgumentException | java.time.DateTimeException invalid) { query = ErrorLogQuery.parse(Map.of()); }
        model.addAttribute("entry", entry);
        model.addAttribute("backUrl", query.url("/admin/error-logs", query.page()));
        return "admin/error-log-detail";
    }
}
