package com.euiseon.friger.inventory;

import com.euiseon.friger.account.*;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.bulk.*;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.history.dao.HistoryDao;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties={"frizer.security.enabled=true","FRIZER_LOGIN_USERNAME=owner","FRIZER_LOGIN_PASSWORD=test-only-strong-password"})
@AutoConfigureMockMvc
@Testcontainers
class UserIsolationIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.11");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired LegacyAccountBootstrap bootstrap;
    @Autowired InventoryService inventory;
    @Autowired InventoryDao items;
    @Autowired FoodMasterDao masters;
    @Autowired FoodRegistrationService registrations;
    @Autowired FoodRegistrationDao registrationReceipts;
    @Autowired FoodQuantityService quantities;
    @Autowired FoodQuantityDao quantityDao;
    @Autowired ItemMoveService moves;
    @Autowired FoodSplitService split;
    @Autowired FoodDeleteService deleteService;
    @Autowired ItemMoveDao moveDao;
    @Autowired HistoryDao histories;
    @Autowired BulkRegistrationService bulk;
    @Autowired BulkWorkbook workbook;
    long bob;
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        for(String table:List.of("food_delete_receipt","food_quantity_receipt","food_registration_receipt","food_item_move_receipt","food_merge_receipt","food_bulk_receipt","food_bulk_preview","food_split_receipt","food_history","food_item","food_master")) jdbc.update("DELETE FROM "+table);
        jdbc.update("DELETE FROM app_user WHERE user_id<>1");
        jdbc.update("UPDATE app_user SET login_id='owner',enabled=true,role='USER' WHERE user_id=1");
        bob=jdbc.queryForObject("INSERT INTO app_user(login_id,password_hash,role,enabled) VALUES('tester',?,'USER',true) RETURNING user_id",Long.class,
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("test-only-second-password"));
        as("owner");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void as(String name) {
        var principal=accounts.loadUserByUsername(name);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
    }
    FoodCreateForm form(String name) {
        return new FoodCreateForm(name,StorageType.FRIDGE,null,new BigDecimal("2"),null,null,null,null,null,FreezeType.NONE,false,null,null,null,null,"개");
    }
    @Test void bothRealLoginsWorkAndUserIdsSurviveLoginRename() throws Exception {
        for(String name:List.of("owner","tester")) {
            org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
            var result=mvc.perform(post("/login").session(new MockHttpSession()).with(csrf()).param("username",name)
                    .param("password",name.equals("owner")?"test-only-strong-password":"test-only-second-password"))
                    .andExpect(redirectedUrl("/")).andReturn();
            var context=(org.springframework.security.core.context.SecurityContext)result.getRequest().getSession(false).getAttribute("SPRING_SECURITY_CONTEXT");
            assertThat(context.getAuthentication().getName()).isEqualTo(name);
        }
        as("owner"); long id=inventory.create(form("내 음식"));
        jdbc.update("UPDATE app_user SET login_id='renamed' WHERE user_id=1");
        as("renamed"); assertThat(inventory.findById(id).foodName()).isEqualTo("내 음식");
        assertThat(accounts.loadUserByUsername("renamed").userId()).isEqualTo(1);
    }
    @Test void listsDetailsHistoryAndDownloadChoicesAreScoped() throws Exception {
        long mine=inventory.create(form("소유자 전용 음식")); long master=masters.masterIdForItem(mine);
        as("tester"); long own=inventory.create(form("테스터 전용 음식"));
        assertThat(items.findActive()).extracting(i->i.foodId()).containsExactly(own);
        assertThat(items.findById(mine)).isNull();assertThat(items.findByIdForUpdate(mine)).isNull();
        assertThat(masters.find(master)).isNull();assertThat(masters.lock(master)).isNull();
        assertThat(items.findByMaster(master)).isEmpty();assertThat(masters.registrationChoices()).hasSize(1);
        assertThat(histories.findRecent(100)).allMatch(h->Objects.equals(h.foodId(),own));
        assertThat(quantities.history(mine)).isEmpty();
        mvc.perform(get("/inventory").with(user(accounts.loadUserByUsername("tester"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("소유자 전용 음식"))));
        mvc.perform(get("/inventory/"+mine).with(user(accounts.loadUserByUsername("tester")))).andExpect(status().isNotFound());
        mvc.perform(get("/foods/"+master).with(user(accounts.loadUserByUsername("tester")))).andExpect(status().isNotFound());
    }
    @Test void forgedUpdateDeleteQuantityMergeMoveAndAdditionalRegistrationCannotTouchOtherOwner() {
        long id=inventory.create(form("소유자 음식")); var before=inventory.findById(id); long master=masters.masterIdForItem(id);
        as("tester"); long other=inventory.create(form("테스터 음식")); long otherMaster=masters.masterIdForItem(other);
        assertThatThrownBy(()->inventory.update(id,form("변조"),before.updatedAt())).isInstanceOf(RuntimeException.class);
        assertThat(masters.delete(master)).isZero();assertThat(masters.update(master,"변조",null)).isZero();
        assertThatThrownBy(()->quantities.apply(id,FoodQuantityService.Action.CONSUME,0,null,UUID.randomUUID(),null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->moves.move(new ItemMoveService.Command(ItemMoveService.Mode.EXISTING,otherMaster,null,null,master,0,0L,List.of(id),true),UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->moves.preview(otherMaster,List.of(other),ItemMoveService.Mode.EXISTING,master,null,null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->split.split(id,new FoodSplitService.Command(new java.math.BigDecimal("1"),StorageType.FRIDGE,null,null,false),0,UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->inventory.create(form("침입"),master,0L)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->deleteService.selection(master,List.of(id))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->deleteService.delete(new FoodDeleteService.Command(master,0,List.of(id),List.of(0L),false),UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        as("owner");assertThat(inventory.findById(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(2);
    }
    @Test void sameRegistrationTokenIsIndependentAndForeignReceiptsAreInvisible() {
        UUID token=UUID.randomUUID(); long first=registrations.create(form("같은 음식"),token);
        as("tester"); assertThat(registrationReceipts.find(token)).isNull();
        long second=registrations.create(form("같은 음식"),token);assertThat(second).isNotEqualTo(first);
        assertThat(registrations.create(form("같은 음식"),token)).isEqualTo(second);
        as("owner");assertThat(registrations.create(form("같은 음식"),token)).isEqualTo(first);
    }
    @Test void endedItemsAndQuantityReceiptsStayPrivate() {
        long id=inventory.create(form("종료 음식"));UUID token=UUID.randomUUID();
        long event=quantities.apply(id,FoodQuantityService.Action.CONSUME,0,null,token,null);
        long master=masters.masterIdForItem(id);
        as("tester");assertThat(items.findEnded()).isEmpty();assertThat(items.endedLinks()).isEmpty();
        assertThat(quantities.endedSummaries(master)).isEmpty();assertThat(quantityDao.receipt(token)).isNull();
        assertThatThrownBy(()->quantities.apply(id,FoodQuantityService.Action.CANCEL,1,event,token,null)).isInstanceOf(RuntimeException.class);
    }
    byte[] file(Long master) throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(java.util.List.of())));var out=new ByteArrayOutputStream()) {
            var row=book.getSheet(master==null?"신규 등록":"추가 등록").getRow(4);
            var values=master==null?Map.of(0,"공통 일괄 음식",1,"2",2,"개",6,"냉장실"):
                    Map.of(0,master.toString(),3,"2",4,"개",8,"냉장실");
            values.forEach((c,v)->row.getCell(c,org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(v));
            book.write(out);return out.toByteArray();
        }
    }
    @Test void bulkPreviewCannotCrossAccountsEvenWithLeakedSessionTokenAndDuplicateIsPerOwner() throws Exception {
        var bytes=file(null);UUID sessionToken=UUID.randomUUID();var first=bulk.preview(bytes,sessionToken);assertThat(first.valid()).isTrue();
        as("tester");assertThatThrownBy(()->bulk.commit(first.requestId(),sessionToken,false)).isInstanceOf(IllegalArgumentException.class);
        as("owner");bulk.commit(first.requestId(),sessionToken,false);
        as("tester");var second=bulk.preview(bytes,sessionToken);assertThat(second.duplicate()).isFalse();bulk.commit(second.requestId(),sessionToken,false);
        assertThat(items.findActive()).hasSize(1);
        as("owner");assertThat(bulk.preview(bytes,sessionToken).duplicate()).isTrue();assertThat(items.findActive()).hasSize(1);
    }
    @Test void bulkAdditionalRowsCannotReferenceForeignMaster() throws Exception {
        long id=inventory.create(form("비공개"));long master=masters.masterIdForItem(id);as("tester");
        var preview=bulk.preview(file(master),UUID.randomUUID());assertThat(preview.valid()).isFalse();
        assertThat(preview.rows().getFirst().errors()).isNotEmpty();assertThat(items.findActive()).isEmpty();
    }
    @Test void databaseRejectsMissingOwnerAndCrossOwnerRelations() {
        long id=inventory.create(form("내 음식"));long master=masters.masterIdForItem(id);
        assertThatThrownBy(()->jdbc.update("INSERT INTO food_master(food_name) VALUES('missing')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO food_item(user_id,master_id,storage_type) VALUES(?,?,'FRIDGE')",bob,master)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO food_history(user_id,food_id,action_type,new_storage_type) VALUES(?,?,'CREATE','FRIDGE')",bob,id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void missingAuthenticationFailsClosedAtServiceLayer() {
        SecurityContextHolder.clearContext();assertThatThrownBy(()->inventory.findActive()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->inventory.create(form("미인증"))).isInstanceOf(RuntimeException.class);
    }
    @Test void disabledAccountsLoseExistingSessionAndCannotLogIn() throws Exception {
        SecurityContextHolder.clearContext();
        var result=mvc.perform(post("/login").with(csrf()).param("username","tester").param("password","test-only-second-password")).andExpect(authenticated()).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);
        jdbc.update("UPDATE app_user SET enabled=false WHERE user_id=?",bob);
        mvc.perform(get("/inventory").session(session)).andExpect(redirectedUrlPattern("**/login"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(post("/login").with(csrf()).param("username","tester").param("password","test-only-second-password")).andExpect(unauthenticated());
    }
    @Test void bootstrapDoesNotOverwriteAccountAndRolesAreReservedWithoutDataBypass() throws Exception {
        var hash=jdbc.queryForObject("SELECT password_hash FROM app_user WHERE user_id=1",String.class);
        bootstrap.run(new DefaultApplicationArguments());assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE user_id=1",String.class)).isEqualTo(hash);
        assertThat(accounts.loadUserByUsername("owner").getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        long id=inventory.create(form("본인 음식"));
        jdbc.update("UPDATE app_user SET role='ADMIN' WHERE user_id=?",bob);as("tester");assertThat(items.findById(id)).isNull();
        mvc.perform(get("/admin/users").with(user(accounts.loadUserByUsername("owner")))).andExpect(status().isForbidden());
    }
    @Test void passwordPolicyAcceptsTenCharactersAndRejectsShorterOrOversizeUtf8() {
        assertThatCode(()->AccountService.validateCredentials("sample","a".repeat(10))).doesNotThrowAnyException();
        assertThatThrownBy(()->AccountService.validateCredentials("sample","a".repeat(9))).hasMessage("비밀번호는 10자 이상으로 입력해줘.");
        assertThatThrownBy(()->AccountService.validateCredentials("sample","가".repeat(25))).hasMessage("비밀번호가 너무 길어. 조금 더 짧게 입력해줘.");
        assertThatThrownBy(()->AccountService.validateCredentials(" sample","a".repeat(10))).hasMessage("아이디 앞뒤의 공백을 지워줘.");
        assertThatThrownBy(()->AccountService.validateCredentials("","a".repeat(10))).hasMessage("아이디를 입력해줘.");
        assertThatThrownBy(()->AccountService.validateCredentials("a".repeat(101),"a".repeat(10))).hasMessage("아이디는 100자 이내로 입력해줘.");
    }
    @Test void operatorProvisioningCreatesUserAndNeverResetsAnExistingPassword() throws Exception {
        var command=new AccountProvisionCommand(jdbc,"extra-test","extra-test-password");
        command.run(new DefaultApplicationArguments());
        var created=accounts.loadUserByUsername("extra-test");
        assertThat(created.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("extra-test-password",created.getPassword())).isTrue();
        assertThatThrownBy(()->new AccountProvisionCommand(jdbc,"extra-test","changed-test-password").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.loadUserByUsername("extra-test").getPassword()).isEqualTo(created.getPassword());
        SecurityContextHolder.clearContext();
        mvc.perform(post("/admin/users").with(csrf()).param("loginId","public-signup").param("password","public-test-password"))
                .andExpect(redirectedUrlPattern("**/login"));
    }
    @Test void ownerIsPartOfMybatisCacheKeyWithinSameTransaction() {
        long id=inventory.create(form("캐시도 비공개"));
        var transaction=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status->{
            as("owner");assertThat(items.findById(id)).isNotNull();
            as("tester");assertThat(items.findById(id)).isNull();
            as("owner");assertThat(items.findById(id)).isNotNull();
        });
    }

    MockHttpSession login(String name,String password,MockHttpSession session) throws Exception {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        var result=mvc.perform(post("/login").session(session).with(csrf()).param("username",name).param("password",password))
                .andExpect(redirectedUrl("/")).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    @Test void realLoginRotatesSessionIdAndLogoutInvalidatesIt() throws Exception {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)page.getRequest().getSession(false);
        String before=session.getId();
        var matcher=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        mvc.perform(post("/login").session(session).param("_csrf",matcher.group(1)).param("username","owner").param("password","test-only-strong-password"))
                .andExpect(redirectedUrl("/"));
        assertThat(session.getId()).isNotEqualTo(before);
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/inventory")).andExpect(redirectedUrlPattern("**/login"));
    }
    @Test void passwordChangeAndDisableReenableRevokeExistingSessions() throws Exception {
        var session=login("tester","test-only-second-password",new MockHttpSession());
        jdbc.update("UPDATE app_user SET password_hash=? WHERE user_id=?",PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("new-test-only-password"),bob);
        mvc.perform(get("/inventory").session(session)).andExpect(redirectedUrlPattern("**/login"));
        assertThat(session.isInvalid()).isTrue();
        session=login("tester","new-test-only-password",new MockHttpSession());
        jdbc.update("UPDATE app_user SET enabled=false WHERE user_id=?",bob);
        jdbc.update("UPDATE app_user SET enabled=true WHERE user_id=?",bob);
        mvc.perform(get("/inventory").session(session)).andExpect(redirectedUrlPattern("**/login"));
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void independentSessionsAndAccountSwitchCannotReuseAnotherAccountsBulkPreview() throws Exception {
        long id=inventory.create(form("본인 세션 전용"));
        UUID browserToken=UUID.randomUUID();var preview=bulk.preview(file(null),browserToken);
        var ownerSession=login("owner","test-only-strong-password",new MockHttpSession());
        var testerSession=login("tester","test-only-second-password",new MockHttpSession());
        mvc.perform(get("/inventory/"+id).session(ownerSession)).andExpect(status().isOk());
        mvc.perform(get("/inventory/"+id).session(testerSession)).andExpect(status().isNotFound());
        // Simulate an existing browser changing accounts without closing its old preview tab.
        ownerSession.setAttribute("bulkOwner",browserToken);
        ownerSession=login("tester","test-only-second-password",ownerSession);
        mvc.perform(post("/inventory/bulk/commit").session(ownerSession).with(csrf()).param("requestId",preview.requestId().toString()))
                .andExpect(redirectedUrl("/inventory/bulk"));
        mvc.perform(get("/inventory/"+id).session(ownerSession)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(1);
    }
    @Test void renamedLoginKeepsSessionOwnershipButRoleChangesRevokeSession() throws Exception {
        long id=inventory.create(form("이름 변경 전 음식"));
        var session=login("owner","test-only-strong-password",new MockHttpSession());
        jdbc.update("UPDATE app_user SET login_id='renamed' WHERE user_id=1");
        mvc.perform(get("/inventory/"+id).session(session)).andExpect(status().isOk());
        jdbc.update("UPDATE app_user SET role='ADMIN' WHERE user_id=1");
        mvc.perform(get("/inventory").session(session)).andExpect(redirectedUrlPattern("**/login"));
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void v14UpgradePreservesExistingDataAndLocksLegacyOwner() {
        String schema="users_upgrade";
        Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).target("14").load().migrate();
        jdbc.update("INSERT INTO users_upgrade.food_master(food_name) VALUES('legacy')");
        jdbc.update("INSERT INTO users_upgrade.food_item(master_id,storage_type,quantity_amount,quantity_unit) VALUES(1,'FRIDGE',2,'개')");
        jdbc.update("INSERT INTO users_upgrade.food_history(food_id,action_type,new_storage_type) VALUES(1,'CREATE','FRIDGE')");
        var before=jdbc.queryForMap("SELECT * FROM users_upgrade.food_item");
        jdbc.update("INSERT INTO users_upgrade.food_registration_receipt(request_id,request_payload,food_id) VALUES(?,'legacy',1)",UUID.randomUUID());
        jdbc.update("INSERT INTO users_upgrade.food_quantity_receipt(request_id,request_payload,food_id,history_id) VALUES(?,'legacy',1,1)",UUID.randomUUID());
        jdbc.update("INSERT INTO users_upgrade.food_merge_receipt(request_id,source_id,target_id,source_version,target_version,source_name,target_name,item_count) VALUES(?,2,1,0,0,'old','legacy',1)",UUID.randomUUID());
        jdbc.update("INSERT INTO users_upgrade.food_item_move_receipt(request_id,fingerprint,food_id,source_id,target_id,source_name,target_name,source_removed) VALUES(?,'legacy',1,3,1,'old','legacy',true)",UUID.randomUUID());
        jdbc.update("INSERT INTO users_upgrade.food_bulk_preview(request_id,owner_id,payload,fingerprint,expires_at) VALUES(?,?,'legacy','legacy',CURRENT_TIMESTAMP)",UUID.randomUUID(),UUID.randomUUID());
        jdbc.update("INSERT INTO users_upgrade.food_bulk_receipt(fingerprint,request_id) VALUES('legacy',?)",UUID.randomUUID());
        Flyway.configure().dataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()).schemas(schema).defaultSchema(schema).load().migrate();
        for(String table:List.of("food_master","food_item","food_history","food_registration_receipt","food_quantity_receipt","food_merge_receipt","food_item_move_receipt","food_bulk_preview","food_bulk_receipt"))
            assertThat(jdbc.queryForObject("SELECT user_id FROM users_upgrade."+table,Long.class)).isEqualTo(1);
        var after=jdbc.queryForMap("SELECT * FROM users_upgrade.food_item");
        after.remove("user_id");assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT enabled FROM users_upgrade.app_user",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users_upgrade.app_user",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT quantity_amount FROM users_upgrade.food_item",BigDecimal.class)).isEqualByComparingTo("2");
    }
}
