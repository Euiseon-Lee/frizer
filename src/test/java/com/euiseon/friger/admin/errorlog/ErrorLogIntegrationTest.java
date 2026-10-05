package com.euiseon.friger.admin.errorlog;

import com.euiseon.friger.account.AccountService;
import com.euiseon.friger.common.errorlog.*;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "frizer.security.enabled=true", "FRIZER_LOGIN_USERNAME=owner", "FRIZER_LOGIN_PASSWORD=test-only-strong-password",
        "frizer.error-log.app-version=test-build"})
@AutoConfigureMockMvc
@Testcontainers
@Import(ErrorLogIntegrationTest.FailureConfig.class)
class ErrorLogIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername); r.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired ErrorLogStore store;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestRestTemplate http;

    @TestConfiguration static class FailureConfig {
        @Bean FailureEndpoint failureEndpoint() { return new FailureEndpoint(); }
    }
    @RestController static class FailureEndpoint {
        @GetMapping("/css/test-failure") String fail() { throw new IllegalStateException("password=never-store-this"); }
        @GetMapping("/css/test-resolved") String resolved() { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "private-message"); }
    }

    @BeforeEach void clear() {
        jdbc.update("DELETE FROM application_error_log");
        jdbc.update("UPDATE app_user SET role='ADMIN' WHERE login_id='owner'");
    }

    @Test void capturesCsrfAndAuthenticationFailuresWithoutChangingResponses() throws Exception {
        mvc.perform(post("/login").param("username", "owner").param("password", "secret"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT error_code FROM application_error_log", String.class)).isEqualTo("CSRF_MISSING");
        mvc.perform(post("/login").with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("username", "owner").param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        assertThat(jdbc.queryForList("SELECT error_code FROM application_error_log", String.class))
                .containsExactlyInAnyOrder("CSRF_MISSING", "CSRF_INVALID", "AUTHENTICATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_error_log WHERE http_status = 302", Integer.class)).isEqualTo(1);
    }

    @Test void realServletErrorDispatchKeepsExistingPageAndStoresOnlyOnce() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setAccept(java.util.List.of(org.springframework.http.MediaType.TEXT_HTML));
        var response = http.exchange("/css/test-failure", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).contains("호출에 완벽히 실패해버렸달까.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_error_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stack_trace FROM application_error_log", String.class))
                .contains("FailureEndpoint.fail").doesNotContain("never-store-this");
    }

    @Test void resolvedServerExceptionRetainsCauseAndCsrfForwardRetainsOriginalPath() {
        assertThat(http.getForEntity("/css/test-resolved", String.class).getStatusCode().value()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT stack_trace FROM application_error_log", String.class))
                .contains("ResponseStatusException").doesNotContain("private-message");
        var response = http.postForEntity("/login", new org.springframework.util.LinkedMultiValueMap<String, String>(), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT request_path FROM application_error_log WHERE error_code='CSRF_MISSING'", String.class)).isEqualTo("/login");
    }

    @Test void independentTransactionAndRetentionBoundary() {
        var now = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.execute(status -> {
            seed(now, "SERVER_ERROR", "/inventory", 500, null, "stack");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_error_log", Integer.class)).isEqualTo(1);
        seed(now.minusDays(31), "SERVER_ERROR", "/old", 500, null, "stack");
        seed(now.minusDays(30), "SERVER_ERROR", "/boundary", 500, null, "stack");
        var cleanup = new ErrorLogRetention(jdbc, 30);
        assertThat(cleanup.deleteBefore(now.minusDays(30).toInstant())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_error_log", Integer.class)).isEqualTo(2);
    }

    private long seed(OffsetDateTime time, String code, String path, int status, Long userId, String stack) {
        var requestId = UUID.randomUUID();
        store.save(new ErrorLogEntry(0, time, requestId, userId, "GET", path, status, code,
                "java.lang.IllegalStateException", "테스트 오류", stack, "INVALID", "test-build"));
        return jdbc.queryForObject("SELECT id FROM application_error_log WHERE request_id=?", Long.class, requestId);
    }
}
