package com.euiseon.friger.admin.errorlog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ErrorLogReadService {
    public static final int PAGE_SIZE = 50;
    private final JdbcTemplate jdbc;
    public ErrorLogReadService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Page(List<ErrorLogView> rows, long total) { }

    public Page search(ErrorLogQuery q) {
        StringBuilder where = new StringBuilder(" WHERE occurred_at >= ? AND occurred_at < ?");
        var args = new ArrayList<Object>();
        args.add(q.start().atZone(ErrorLogQuery.SEOUL).toOffsetDateTime());
        args.add(q.end().atZone(ErrorLogQuery.SEOUL).toOffsetDateTime());
        if (!q.code().isBlank()) { where.append(" AND error_code = ?"); args.add(q.code()); }
        if (q.status() != null) { where.append(" AND http_status = ?"); args.add(q.status()); }
        if (!q.loginId().isBlank()) { where.append(" AND strpos(u.login_id, ?) > 0"); args.add(q.loginId()); }
        if (!q.path().isBlank()) { where.append(" AND strpos(request_path, ?) > 0"); args.add(q.path()); }
        long total = jdbc.queryForObject("SELECT count(*) FROM application_error_log e LEFT JOIN app_user u ON u.user_id = e.user_id" + where, Long.class, args.toArray());
        args.add(PAGE_SIZE);
        args.add((long) q.page() * PAGE_SIZE);
        // Large diagnostic text is fetched only on the detail page.
        var rows = jdbc.query("""
                SELECT e.id, occurred_at, request_id, e.user_id, http_method, request_path, http_status, error_code,
                       exception_class, '' AS message, NULL AS stack_trace, session_state, app_version, u.login_id
                FROM application_error_log e LEFT JOIN app_user u ON u.user_id = e.user_id
                """ + where + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?", ErrorLogReadService::map, args.toArray());
        return new Page(rows, total);
    }

    public Optional<ErrorLogView> find(long id) {
        return jdbc.query("""
                SELECT e.id, occurred_at, request_id, e.user_id, http_method, request_path, http_status, error_code,
                       exception_class, message, stack_trace, session_state, app_version, u.login_id
                FROM application_error_log e LEFT JOIN app_user u ON u.user_id = e.user_id WHERE e.id = ?
                """, ErrorLogReadService::map, id).stream().findFirst();
    }

    private static ErrorLogView map(ResultSet r, int row) throws SQLException {
        return new ErrorLogView(r.getLong("id"), r.getObject("occurred_at", OffsetDateTime.class),
                r.getObject("request_id", UUID.class), r.getObject("user_id", Long.class),
                r.getString("http_method"), r.getString("request_path"), r.getInt("http_status"),
                r.getString("error_code"), r.getString("exception_class"), r.getString("message"),
                r.getString("stack_trace"), r.getString("session_state"), r.getString("app_version"), r.getString("login_id"));
    }
}
