package com.euiseon.friger.inventory;

import com.euiseon.friger.inventory.bulk.BulkWorkbook;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties={"frizer.security.enabled=true", "FRIZER_LOGIN_USERNAME=owner", "FRIZER_LOGIN_PASSWORD=test-only-strong-password"})
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.11");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername); r.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired BulkWorkbook workbook;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.euiseon.friger.account.AccountService accounts;

    @ParameterizedTest @ValueSource(strings={"/", "/inventory", "/inventory/new", "/history", "/inventory/bulk", "/inventory/bulk/template", "/account", "/foods/1", "/inventory/1/move/choices?q=test"})
    void anonymousCannotReadPrivatePages(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }
    @Test void publicHealthAndLoginContainNoStock() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(content().string("ok"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
    }
    @Test void versionedImagesArePublicAndCacheableButLoginIsNot() throws Exception {
        mvc.perform(get("/assets/choco/web/v1/happy-run-front.webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        mvc.perform(get("/login"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
    @Test void rejectedRequestsUseSharedErrorViewWithForbiddenStatus() throws Exception {
        mvc.perform(get("/access-denied"))
                .andExpect(status().isForbidden()).andExpect(view().name("error"))
                .andExpect(content().string(containsString("아무리 쪼코라도 다시 시도하는 것밖엔 방법이 없달까😢")))
                .andExpect(content().string(containsString("href=\"/\">홈으로 돌아가자")));
    }
    @ParameterizedTest @ValueSource(ints={403, 404, 500})
    void sharedErrorViewKeepsStatusSpecificMessageAndDestination(int code) throws Exception {
        String message = code == 403 ? "아무리 쪼코라도 다시 시도하는 것밖엔 방법이 없달까😢" : code == 404 ? "찾을 수 없는 페이지라 열 수 없어." : "호출에 완벽히 실패해버렸달까.";
        String action = code == 404 ? "href=\"/inventory\">음식 목록으로 가자" : "href=\"/\">홈으로 돌아가자";
        mvc.perform(get("/error").accept("text/html").requestAttr("jakarta.servlet.error.status_code", code))
                .andExpect(status().is(code)).andExpect(view().name("error"))
                .andExpect(content().string(containsString(message)))
                .andExpect(content().string(containsString(action)));
    }
    @Test void loginRejectsWrongPasswordAndAcceptsRealCredentialsThenLogoutEndsSession() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "owner").param("password", "wrong"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        var result = mvc.perform(post("/login").with(csrf()).param("username", "owner").param("password", "test-only-strong-password"))
                .andExpect(authenticated().withUsername("owner")).andExpect(redirectedUrl("/")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/inventory").session(session)).andExpect(status().isOk());
        mvc.perform(get("/account").session(session)).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void writesNeedCsrfEvenWhenLoggedIn() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM food_item", Integer.class);
        mvc.perform(post("/inventory").with(user(accounts.loadUserByUsername("owner")))).andExpect(status().isForbidden());
        mvc.perform(post("/inventory").with(user(accounts.loadUserByUsername("owner"))).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item", Integer.class)).isEqualTo(before);
        mvc.perform(post("/login").param("username", "owner").param("password", "test-only-strong-password")).andExpect(status().isForbidden());
    }
    @Test void renderedFormTokenAllowsRegistrationAndInvalidPostDoesNot() throws Exception {
        var page = mvc.perform(get("/inventory/new").with(user(accounts.loadUserByUsername("owner")))).andExpect(status().isOk()).andReturn();
        var html = page.getResponse().getContentAsString();
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(token.find()).isTrue();
        // Invalid business input still reaches MVC when the real rendered CSRF token is present.
        mvc.perform(post("/inventory").session((MockHttpSession) page.getRequest().getSession(false))
                .with(user(accounts.loadUserByUsername("owner"))).param("_csrf", token.group(1)).param("foodName", ""))
                .andExpect(status().isOk()).andExpect(view().name("inventory/new"));
    }
    @Test void multipartPreviewAcceptsHeaderTokenAndRejectsMissingToken() throws Exception {
        var page = mvc.perform(get("/inventory/bulk").with(user(accounts.loadUserByUsername("owner")))).andReturn();
        var session = (MockHttpSession) page.getRequest().getSession(false);
        var formToken = session.getAttribute("bulkOwner").toString();
        var file = new MockMultipartFile("file", "foods.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook.template());
        mvc.perform(multipart("/inventory/bulk/preview").file(file).session(session).with(user(accounts.loadUserByUsername("owner"))).param("formToken", formToken))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/inventory/bulk/preview").file(file).session(session).with(user(accounts.loadUserByUsername("owner"))).with(csrf().asHeader()).param("formToken", formToken))
                .andExpect(status().isOk()).andExpect(content().string(containsString("등록할 음식이 없어")));
    }
}
