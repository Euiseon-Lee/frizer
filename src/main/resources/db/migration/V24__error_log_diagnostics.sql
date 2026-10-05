ALTER TABLE application_error_log
    ADD COLUMN diagnostic_context jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT ck_error_log_diagnostic_size CHECK (octet_length(diagnostic_context::text) <= 4096);
