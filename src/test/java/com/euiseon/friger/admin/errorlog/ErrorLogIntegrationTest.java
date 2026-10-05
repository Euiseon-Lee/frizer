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
    @Autowired ErrorLogReadService reads;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestRestTemplate http;

    @TestConfiguration static class FailureConfig {
        @Bean FailureEndpoint failureEndpoint() { return new FailureEndpoint(); }
    }
    @RestController static class FailureEndpoint {
        @Autowired JdbcTemplate jdbc;
        @GetMapping("/css/test-database-failure") String databaseFailure() {
            jdbc.update("INSERT INTO app_user (login_id,password_hash,role,enabled) SELECT login_id,password_hash,role,enabled FROM app_user WHERE login_id='owner'");
            return "unexpected success";
        }
        @GetMapping("/css/test-failure") String fail() { throw new IllegalStateException("password=never-store-this"); }
        @GetMapping("/css/test-resolved") String resolved() { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "private-message"); }
    }

    @BeforeEach void clear() {
        jdbc.update("DELETE FROM application_error_log");
        jdbc.update("UPDATE app_user SET role='ADMIN' WHERE login_id='owner'");
    }

    @Test void loginLandsOnRoleHomeInsteadOfSavedRequest() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/inventory").session(session)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post("/login").session(session).with(csrf()).param("username", "owner")
                .param("password", "test-only-strong-password")).andExpect(redirectedUrl("/admin"));
        assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
        mvc.perform(get("/admin").session(session)).andExpect(redirectedUrl("/admin/error-logs"));

        jdbc.update("UPDATE app_user SET role='USER' WHERE login_id='owner'");
        var userSession = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/admin").session(userSession)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post("/login").session(userSession).with(csrf()).param("username", "owner")
                .param("password", "test-only-strong-password")).andExpect(redirectedUrl("/"));
        assertThat(userSession.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
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
        assertThat(jdbc.queryForObject("SELECT diagnostic_context->>'authenticationFailure' FROM application_error_log WHERE error_code='AUTHENTICATION_FAILED'", String.class))
                .isEqualTo("BAD_CREDENTIALS");
    }

    @Test void databaseDiagnosticsSurvivePersistenceAndAppearOnlyInDetail() throws Exception {
        assertThat(http.getForEntity("/css/test-database-failure", String.class).getStatusCode().value()).isEqualTo(500);
        long id = jdbc.queryForObject("SELECT id FROM application_error_log", Long.class);
        var detail = reads.find(id).orElseThrow();
        assertThat(detail.diagnosticContext()).contains("23505", "constraint", "고유값 중복").doesNotContain("owner", "password_hash");
        assertThat(reads.search(ErrorLogQuery.parse(Map.of())).rows().getFirst().diagnosticContext()).isNull();
        mvc.perform(get("/admin/error-logs/" + id).with(user(accounts.loadUserByUsername("owner"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("23505")))
                .andExpect(content().string(containsString("진단 정보")));
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

    @Test void adminOnlyAndEscapedDetailWithSearchNavigation() throws Exception {
        long id = seed(OffsetDateTime.now(), "SERVER_ERROR", "/inventory/{id}", 500, 1L, "<script>alert('x')</script>");
        mvc.perform(get("/admin/error-logs")).andExpect(redirectedUrlPattern("**/login"));
        // Real principal so AccountSessionFilter accepts this user.
        var owner = accounts.loadUserByUsername("owner");
        assertThat(reads.find(id).orElseThrow().loginId()).isEqualTo("owner");
        var list = mvc.perform(get("/admin/error-logs").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SERVER_ERROR"))).andReturn();
        var detail = mvc.perform(get("/admin/error-logs/" + id).param("path", "/inventory").with(user(owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString("path=/inventory"))).andReturn();
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("<script>alert");
        assertThat(detail.getResponse().getContentAsString()).contains("<dd>owner</dd>");
        assertThat(list.getResponse().getContentAsString()).contains("<td>owner</td>");
        mvc.perform(get("/admin/error-logs").param("loginId", "x".repeat(101)).with(user(owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("검색 조건을 확인해주세요")));
        mvc.perform(get("/admin/error-logs/9223372036854775807").with(user(owner))).andExpect(status().isNotFound());
        // Change role only for this test and restore even on assertion failure.
        jdbc.update("UPDATE app_user SET role='USER' WHERE login_id='owner'");
        try {
            mvc.perform(get("/admin/error-logs").with(user(accounts.loadUserByUsername("owner")))).andExpect(status().isForbidden());
            mvc.perform(get("/admin/error-logs/" + id).with(user(accounts.loadUserByUsername("owner")))).andExpect(status().isForbidden());
        } finally { jdbc.update("UPDATE app_user SET role='ADMIN' WHERE login_id='owner'"); }
    }

    @Test void searchUsesSeoulTimeAndPagesWithoutFetchingStack() {
        var time = OffsetDateTime.parse("2026-10-05T01:00:00+09:00");
        for (int i = 0; i < 51; i++) seed(time, "SERVER_ERROR", "/inventory", 500, 1L, "private-stack");
        seed(time.minusDays(1), "SERVER_ERROR", "/inventory", 500, 1L, "old");
        long orphanId = seed(time, "SERVER_ERROR", "/inventory", 500, 7L, "orphan");
        assertThat(reads.find(orphanId).orElseThrow().userLabel()).isEqualTo("계정 없음 (#7)");
        var params = new java.util.HashMap<>(Map.of("start", "2026-10-05T00:00", "end", "2026-10-06T00:00", "loginId", "owner", "status", "500", "code", "SERVER_ERROR", "path", "/inventory"));
        var first = reads.search(ErrorLogQuery.parse(params));
        assertThat(first.total()).isEqualTo(51);
        assertThat(first.rows()).hasSize(50).allMatch(row -> row.stackTrace() == null);
        assertThat(first.rows()).allMatch(row -> row.userLabel().equals("owner"));
        assertThat(ErrorLogQuery.parse(params).url("/admin/error-logs", 1)).contains("loginId=owner");
        params.put("page", "1");
        assertThat(reads.search(ErrorLogQuery.parse(params)).rows()).hasSize(1);
        params.put("loginId", "own");
        assertThat(reads.search(ErrorLogQuery.parse(params)).total()).isEqualTo(51);
        params.put("loginId", "%");
        assertThat(reads.search(ErrorLogQuery.parse(params)).total()).isZero();
        params.put("loginId", "owner");
        params.put("path", "%' OR 1=1 --");
        assertThat(reads.search(ErrorLogQuery.parse(params)).total()).isZero();
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
        assertThat(cleanup.deleteBefore(now.minusDays(30).toInstant())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_error_log", Integer.class)).isEqualTo(2);
    }

    private long seed(OffsetDateTime time, String code, String path, int status, Long userId, String stack) {
        var requestId = UUID.randomUUID();
        store.save(new ErrorLogEntry(0, time, requestId, userId, "GET", path, status, code,
                "java.lang.IllegalStateException", "테스트 오류", stack, "INVALID", "test-build", "{}"));
        return jdbc.queryForObject("SELECT id FROM application_error_log WHERE request_id=?", Long.class, requestId);
    }
}
