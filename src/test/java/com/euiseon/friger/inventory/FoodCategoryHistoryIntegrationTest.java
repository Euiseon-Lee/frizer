package com.euiseon.friger.inventory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.euiseon.friger.common.type.StorageType;
import com.euiseon.friger.common.type.FoodActionType;
import com.euiseon.friger.history.dao.HistoryDao;
import com.euiseon.friger.history.dto.HistoryEntry;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class FoodCategoryHistoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired InventoryService inventory;
    @Autowired FoodMasterDao masters;
    @Autowired FoodRegistrationService registrations;
    @Autowired FoodDeleteService deletes;
    @Autowired ItemMoveService moves;
    @Autowired HistoryDao history;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void reset() {
        for (String table : List.of("food_delete_receipt", "food_item_move_receipt",
                "food_merge_receipt", "food_registration_receipt", "food_history", "food_item", "food_master"))
            jdbc.update("DELETE FROM " + table);
    }
    private FoodCreateForm form(String name, String category) {
        return new FoodCreateForm(name, StorageType.FRIDGE, category, BigDecimal.ONE,
                null,null,null,null,null,null,false,null,null,null,null,"팩");
    }
    private long classified(String name) {
        long item = inventory.create(form(name, "옛 분류"));
        inventory.updateCategorized(item, form(name, "옛 분류"), inventory.findById(item).updatedAt(), "soy", "soy_tofu");
        return item;
    }
    private long master(long item) { return masters.masterIdForItem(item); }
    private void remove(long group, List<Long> items, boolean removeGroup) {
        var selection = deletes.selection(group, items);
        deletes.delete(new FoodDeleteService.Command(group, selection.source().versionNo(), items,
                items.stream().map(selection.versions()::get).toList(), removeGroup), UUID.randomUUID());
    }
    private void move(long source, long target, List<Long> items, boolean whole) {
        moves.move(new ItemMoveService.Command(ItemMoveService.Mode.EXISTING, target, null, null,
                source, masters.find(source).versionNo(), masters.find(target).versionNo(), items, whole), UUID.randomUUID());
    }
    private String page(String path, String name) throws Exception {
        String html = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Path folder = Path.of("build/reports/category-history-ui"); Files.createDirectories(folder);
        Files.writeString(folder.resolve(name + ".html"), html);
        return html;
    }
    private List<HistoryEntry> updates() { return history.findEntries(null, "").stream().filter(e -> e.actionType()==FoodActionType.UPDATE).toList(); }

    @Test void homeAndHistoryShowOneUpdateForCategoryOnlyAndCombinedChanges() throws Exception {
        long item = registrations.createCategorized(form("test", null), null, null, "beverage", null, UUID.randomUUID());
        inventory.updateCategorized(item, form("test", null), inventory.findById(item).updatedAt(), "alcohol", null);
        assertThat(history.findRecent(5)).hasSize(2);
        assertThat(updates()).singleElement().satisfies(e -> assertThat(e.homeSummary()).isEqualTo("분류: 음료 → 주류"));
        var combined = new FoodCreateForm("test", StorageType.FRIDGE, null, new BigDecimal("2"),
                null,null,null,null,null,null,false,null,null,null,null,"팩");
        inventory.updateCategorized(item, combined, inventory.findById(item).updatedAt(), "beverage", null);
        assertThat(history.findRecent(5)).hasSize(3);
        assertThat(updates()).hasSize(2);
        assertThat(updates().getFirst().homeSummary()).isEqualTo("수정한 정보 2건");
        assertThat(updates().getFirst().detailFields()).extracting(HistoryEntry.DetailField::label).containsExactlyInAnyOrder("수량", "분류");
        String html = page("/", "home-combined");
        assertThat(html).contains("수정한 정보 2건", "분류: 음료 → 주류").doesNotContain("CATEGORY_CHANGE", "분류 변경:", ">분류</span>");
        assertThat(html.split("class=\"recent-history-row\"", -1)).hasSize(4);
        assertThat(page("/history", "history").split("class=\"history-row\"", -1)).hasSize(4);
        assertThat(jdbc.queryForList("SELECT category_before->>'major_code' FROM food_history WHERE action_type='UPDATE' ORDER BY history_id", String.class)).containsExactly("beverage", "alcohol");
        assertThat(jdbc.queryForList("SELECT category_after->>'major_code' FROM food_history WHERE action_type='UPDATE' ORDER BY history_id", String.class)).containsExactly("alcohol", "beverage");
    }

    @Test void historyKeepsSnapshotLabelsAndEscapesNamesWithoutSpecialMessages() throws Exception {
        classified("두부 <특별>");
        jdbc.update("UPDATE food_category_minor SET label='현재 기준명' WHERE code='soy_tofu'");
        try {
            assertThat(updates()).singleElement().satisfies(e -> {
                assertThat(e.changesText()).isEqualTo("분류: 옛 분류 (직접 입력) → 콩·두부 › 두부");
                assertThat(e.currentLocationNote()).isNull();
            });
            assertThat(page("/history", "labels")).contains("두부 &lt;특별&gt;").doesNotContain("현재 기준명", "CATEGORY_CHANGE", "그룹 전체에 적용된 분류 변경");
        } finally { jdbc.update("UPDATE food_category_minor SET label='두부' WHERE code='soy_tofu'"); }
    }

    @Test void deletionRemovesCategoryChangesWithItemAndKeepsSiblingHistory() throws Exception {
        long item = classified("삭제 음식"), group = master(item);
        long sibling = registrations.createCategorized(form("삭제 음식", null), group, masters.find(group).versionNo(), null, null, UUID.randomUUID());
        var siblingHistory = jdbc.queryForList("SELECT * FROM food_history WHERE food_id=?", sibling);
        assertThat(deletes.selection(group, List.of(item)).historyCount()).isEqualTo(2);
        assertThat(page("/foods/"+group+"/delete?items="+item, "delete")).contains("삭제할 항목 이력").doesNotContain("분류 변경 기록은 남아 있어");
        remove(group, List.of(item), false);
        assertThat(updates()).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM food_history WHERE food_id=?", sibling)).isEqualTo(siblingHistory);
        assertThat(masters.find(group).categoryMinorCode()).isEqualTo("soy_tofu");
        remove(group, List.of(sibling), true);
        assertThat(history.findEntries(null, "")).isEmpty();
        assertThat(inventory.findActive()).isEmpty();
        page("/history", "deleted");
    }

    @Test void additionalRegistrationKeepsBeforeStateAndHasOnlyCreateEvent() {
        long item = inventory.create(form("보완 음식", "직접 입력값")), group = master(item);
        String oldSnapshot = jdbc.queryForObject("SELECT after_snapshot::text FROM food_history WHERE food_id=?", String.class, item);
        long added = registrations.createCategorized(form("보완 음식", null), group, masters.find(group).versionNo(), "kimchi", null, UUID.randomUUID());
        assertThat(history.findEntries(null, "")).hasSize(2).allMatch(e -> e.actionType()==FoodActionType.CREATE);
        assertThat(jdbc.queryForObject("SELECT category_before->>'label' FROM food_history WHERE food_id=?", String.class, added)).isEqualTo("직접 입력값");
        assertThat(jdbc.queryForObject("SELECT category_after->>'major_code' FROM food_history WHERE food_id=?", String.class, added)).isEqualTo("kimchi");
        assertThat(jdbc.queryForObject("SELECT after_snapshot::text FROM food_history WHERE food_id=?", String.class, item)).isEqualTo(oldSnapshot);
    }

    @Test void repeatedMergesPreserveSingleItemUpdateAndOriginalGroupSnapshot() throws Exception {
        long item = classified("출발 음식"), a = master(item);
        long target = inventory.create(form("중간 음식", null)), b = master(target);
        long last = inventory.create(form("최종 음식", null)), c = master(last);
        String original = jdbc.queryForObject("SELECT category_after::text FROM food_history WHERE food_id=? AND action_type='UPDATE'", String.class, item);
        move(a,b,List.of(item),true); move(b,c,List.of(item,target),true);
        assertThat(updates()).singleElement().satisfies(e -> {
            assertThat(e.foodId()).isEqualTo(item); assertThat(e.currentMasterId()).isEqualTo(c);
        });
        assertThat(jdbc.queryForObject("SELECT category_after::text FROM food_history WHERE food_id=? AND action_type='UPDATE'", String.class, item)).isEqualTo(original);
        assertThat(history.findEntries(null,"최종 음식")).anyMatch(e -> e.actionType()==FoodActionType.UPDATE);
        page("/history", "merged");
        remove(c,List.of(item,target,last),true);
        assertThat(updates()).isEmpty();
    }

    @Test void readsAndUpdatesSucceedWithoutLegacyGroupAuditTable() {
        long item = classified("내 음식");
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.food_category_history')::text",String.class)).isNull();
        inventory.updateCategorized(item, form("내 음식",null), inventory.findById(item).updatedAt(),"kimchi",null);
        assertThat(updates()).hasSize(2);
    }

    @Test void categoryHistoryFollowsOwnerIsolationAndDateLimitRules() {
        long own = classified("검색%_음식");
        long other = jdbc.queryForObject("INSERT INTO app_user(login_id,password_hash,role,enabled) VALUES('category-other','unused','USER',true) ON CONFLICT(login_id) DO UPDATE SET enabled=true RETURNING user_id",Long.class);
        var principal = new com.euiseon.friger.account.AccountPrincipal(other,"category-other","unused","USER",true,0);
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        try {
            context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities()));
            classified("비공개 음식");
            assertThat(history.findEntries(null,"검색%_")).isEmpty();
            assertThat(updates()).singleElement().satisfies(e -> assertThat(e.foodName()).isEqualTo("비공개 음식"));
        } finally { context.setAuthentication(previous); }
        assertThat(history.findEntries(null,"비공개")).isEmpty();
        jdbc.update("UPDATE food_history SET created_at='2026-10-10T15:00:00Z' WHERE food_id=? AND action_type='UPDATE'",own);
        jdbc.update("UPDATE food_history SET created_at='2026-10-09T00:00:00Z' WHERE food_id=? AND action_type='CREATE'",own);
        var from=java.time.OffsetDateTime.parse("2026-10-11T00:00:00+09:00");
        assertThat(history.findFiltered(1,"%_",from,from.plusDays(1))).singleElement().satisfies(e -> assertThat(e.foodId()).isEqualTo(own));
        assertThat(history.findFiltered(null,"%_",from.minusDays(1),from)).isEmpty();
    }

    @Test void latestFiveAreFiveActionsNotDuplicateCategoryEvents() {
        long item = registrations.createCategorized(form("최근 음식", null), null, null, "beverage", null, UUID.randomUUID());
        for (int i=0; i<6; i++) inventory.updateCategorized(item, form("최근 음식", null), inventory.findById(item).updatedAt(),i%2==0?"alcohol":"beverage",null);
        assertThat(history.findRecent(5)).hasSize(5).allMatch(e -> e.actionType()==FoodActionType.UPDATE);
        assertThat(history.findEntries(null, "")).hasSize(7);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.food_category_history')::text",String.class)).isNull();
    }
}
