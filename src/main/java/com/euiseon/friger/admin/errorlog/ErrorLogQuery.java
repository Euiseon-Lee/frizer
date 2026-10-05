package com.euiseon.friger.admin.errorlog;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import org.springframework.web.util.UriComponentsBuilder;

public record ErrorLogQuery(LocalDateTime start, LocalDateTime end, String code, Integer status,
        String loginId, String path, int page) {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final Set<String> CODES = Set.of("SERVER_ERROR", "CSRF_MISSING", "CSRF_INVALID", "ACCESS_DENIED", "AUTHENTICATION_FAILED");
    public static ErrorLogQuery parse(Map<String, String> values) {
        var now = LocalDateTime.now(SEOUL).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        var start = values.getOrDefault("start", "").isBlank() ? now.minusDays(1) : LocalDateTime.parse(values.get("start"));
        var end = values.getOrDefault("end", "").isBlank() ? now : LocalDateTime.parse(values.get("end"));
        String code = values.getOrDefault("code", "");
        Integer status = values.getOrDefault("status", "").isBlank() ? null : Integer.valueOf(values.get("status"));
        String loginId = values.getOrDefault("loginId", "").trim();
        String path = values.getOrDefault("path", "").trim();
        int page = values.getOrDefault("page", "").isBlank() ? 0 : Integer.parseInt(values.get("page"));
        if (start.getYear() < 2000 || end.getYear() > 9999 || !start.isBefore(end) || ChronoUnit.DAYS.between(start, end) > 366
                || (!code.isEmpty() && !CODES.contains(code)) || (status != null && (status < 100 || status > 599))
                || loginId.length() > 100 || path.length() > 512 || page < 0 || page > 10000) {
            throw new IllegalArgumentException("Invalid log filter");
        }
        return new ErrorLogQuery(start, end, code, status, loginId, path, page);
    }

    public String url(String base, int targetPage) {
        return UriComponentsBuilder.fromPath(base).queryParam("start", start).queryParam("end", end)
                .queryParam("code", code).queryParam("status", status == null ? "" : status)
                .queryParam("loginId", loginId).queryParam("path", path)
                .queryParam("page", targetPage).build().encode().toUriString();
    }
}
