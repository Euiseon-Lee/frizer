CREATE TABLE application_error_log (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    request_id uuid NOT NULL UNIQUE,
    user_id bigint,
    http_method varchar(10) NOT NULL,
    request_path varchar(512) NOT NULL,
    http_status smallint NOT NULL,
    error_code varchar(64) NOT NULL,
    exception_class varchar(255),
    message text NOT NULL,
    stack_trace text,
    session_state varchar(32) NOT NULL,
    app_version varchar(64) NOT NULL
);
CREATE INDEX application_error_log_occurred_idx ON application_error_log (occurred_at DESC, id DESC);

