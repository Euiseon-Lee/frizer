package com.euiseon.friger.common.errorlog;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "frizer.error-log.cleanup-enabled", havingValue = "true")
public class ErrorLogRetention {
    private static final Logger log = LoggerFactory.getLogger(ErrorLogRetention.class);
    private final JdbcTemplate jdbc;
    private final int days;
    public ErrorLogRetention(JdbcTemplate jdbc, @Value("${frizer.error-log.retention-days:30}") int days) {
        if (days < 1) throw new IllegalArgumentException("Log retention must be at least one day");
        this.jdbc = jdbc;
        this.days = days;
    }

    @Scheduled(cron = "${frizer.error-log.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void clean() {
        try {
            int total = deleteBefore(Instant.now().minus(days, ChronoUnit.DAYS));
            log.info("error_log_cleanup deleted={} retentionDays={}", total, days);
        } catch (RuntimeException failure) {
            log.error("error_log_cleanup_failed type={}", failure.getClass().getName());
        }
    }

    public int deleteBefore(Instant cutoff) {
        int total = 0;
        // Bound work per run; each statement commits independently.
        for (int batch = 0; batch < 100; batch++) {
            int deleted = jdbc.update("""
                DELETE FROM application_error_log WHERE id IN
                (SELECT id FROM application_error_log WHERE occurred_at < ? ORDER BY occurred_at, id LIMIT 1000)
                """, Timestamp.from(cutoff));
            total += deleted;
            if (deleted < 1000) break;
        }
        return total;
    }
}
