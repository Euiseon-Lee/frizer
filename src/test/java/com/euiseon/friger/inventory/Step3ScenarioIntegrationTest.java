package com.euiseon.friger.inventory;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Current implemented behavior only; no future consume/split APIs are assumed. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Step3ScenarioIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired InventoryService inventory;
    @Autowired FoodMasterService masters;
    @Autowired FoodMasterDao dao;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired Clock clock;
    @Autowired FoodRegistrationService registrations;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM food_history");
        jdbc.update("DELETE FROM food_item");
        jdbc.update("DELETE FROM food_master");
        jdbc.update("DELETE FROM food_merge_receipt");
        jdbc.update("DELETE FROM food_registration_receipt");
    }
    private FoodCreateForm form(String name) {
        return new FoodCreateForm(name, StorageType.FRIDGE, "반찬", new BigDecimal("2"),
                null, null, null, null, null, null, false, "메모", null, null, null, "모");
    }
    private long food(String name) { return masters.masterId(inventory.create(form(name))); }
    private Map<String, Object> snapshot() {
        return Map.of("masters", jdbc.queryForList("SELECT * FROM food_master ORDER BY master_id"),
                "items", jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id"),
                "history", jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id"),
                "receipts", jdbc.queryForList("SELECT * FROM food_merge_receipt ORDER BY request_id"),
                "registrations", jdbc.queryForList("SELECT * FROM food_registration_receipt ORDER BY request_id"));
    }
    private void merge(long a, long b) {
        masters.merge(a, b, masters.find(a).versionNo(), masters.find(b).versionNo(), UUID.randomUUID());
    }

    static Stream<Arguments> lengths() {
        return Stream.of("new", "existing").flatMap(mode -> Stream.of(
                new Object[]{"foodName",100}, new Object[]{"category",50}, new Object[]{"memo",500},
                new Object[]{"capacityText",50}, new Object[]{"sourceMemo",200}, new Object[]{"quantityUnit",10})
            .flatMap(field -> Stream.of(-1,0,1).map(delta -> Arguments.of(mode,field[0],(int)field[1]+delta,delta <= 0))));
    }
    @ParameterizedTest @MethodSource("lengths")
    void textBoundariesOnBothRegistrationPaths(String mode, String field, int length, boolean allowed) throws Exception {
        long master = food("기존 음식");
        var before = snapshot();
        var values = new org.springframework.util.LinkedMultiValueMap<String,String>();
        values.set("registrationMode",mode); values.set("masterId",""+master); values.set("masterVersion","0");
        values.set("foodName","신규 음식"); values.set("quantityAmount","1"); values.set("quantityUnit","개");
        values.set("storageType","ROOM"); values.set("sourceType","ETC"); values.set(field,"가".repeat(length));
        boolean shared = mode.equals("existing") && (field.equals("foodName") || field.equals("category"));
        var result = mvc.perform(post("/inventory").param("registrationRequestId",java.util.UUID.randomUUID().toString()).params(values));
        if (allowed || shared) {
            result.andExpect(status().is3xxRedirection());
            assertThat(inventory.findActive()).hasSize(2);
            if (shared) assertThat(masters.items(master)).allMatch(i -> i.foodName().equals("기존 음식"));
        } else {
            result.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm",field));
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","1.234","1000000000","NaN","1e999",""})
    void invalidAdditionalQuantityNeverChangesMasterOrHistory(String amount) throws Exception {
        long master=food("두부"); var before=snapshot();
        mvc.perform(post("/inventory").param("registrationRequestId",java.util.UUID.randomUUID().toString()).param("registrationMode","existing").param("masterId",""+master)
                .param("masterVersion","0").param("quantityAmount",amount).param("quantityUnit","모")
                .param("storageType","FRIDGE"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"bogus","EXISTING"," new "})
    void unknownRegistrationModeDoesNotSilentlyCreate(String mode) throws Exception {
        var before=snapshot();
        mvc.perform(post("/inventory").param("registrationRequestId",java.util.UUID.randomUUID().toString()).param("registrationMode",mode).param("foodName","두부")
                .param("quantityAmount","1").param("quantityUnit","모").param("storageType","FRIDGE"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @CsvSource({"/foods/-1,404","/foods/nope,400","/foods/-1/merge,404",
            "/inventory/-1,404","/inventory/nope,400","/inventory/-1/edit,404",
            "/inventory/new?masterId=-1,404","/inventory/new?masterId=nope,400","/inventory?storage=NOPE,400"})
    void missingAndMalformedRoutesDoNotWrite(String url,int status) throws Exception {
        var before=snapshot(); mvc.perform(get(url)).andExpect(status().is(status));
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"targetId","sourceVersion","targetVersion","requestId"})
    void missingOrMalformedMergeParametersDoNotWrite(String field) throws Exception {
        long a=food("A"),b=food("B"); var before=snapshot();
        for (String value : new String[]{null,"not-a-number-or-uuid"}) {
            var params=new org.springframework.util.LinkedMultiValueMap<String,String>();
            params.set("targetId",""+b); params.set("sourceVersion","0"); params.set("targetVersion","0");
            params.set("requestId",UUID.randomUUID().toString());
            if(value==null) params.remove(field); else params.set(field,value);
            mvc.perform(post("/foods/"+a+"/merge").params(params)).andExpect(status().isBadRequest());
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    static Stream<Arguments> registrationCombinations() {
        return Stream.of(null,FoodSourceType.PURCHASE,FoodSourceType.DELIVERY_LEFTOVER,FoodSourceType.COOKED,FoodSourceType.PARENTS,FoodSourceType.ETC)
                .flatMap(source -> Stream.of(null,StorageType.FRIDGE,StorageType.ROOM,StorageType.FREEZER)
                .flatMap(storage -> Stream.of(false,true).map(today -> Arguments.of(source,storage,today))));
    }
    @ParameterizedTest @MethodSource("registrationCombinations")
    void sourceStorageTodayCombinationsPreserveAdditionalPurchase(FoodSourceType source,StorageType storage,boolean today) {
        long master=food("기준"); var before=snapshot(); LocalDate date=LocalDate.now(clock).minusDays(2);
        var input=new FoodCreateForm("조작된 이름",storage,"조작 분류",new BigDecimal("0.01"),null,date,date,date,
                source,FreezeType.COMMERCIAL_FROZEN,today,"새 메모","200g","출처 메모",null,"g");
        if(storage==null && source!=FoodSourceType.DELIVERY_LEFTOVER) {
            assertThatThrownBy(()->inventory.create(input,master,0L)).isInstanceOf(InvalidFoodException.class);
            assertThat(snapshot()).isEqualTo(before); return;
        }
        var added=inventory.findById(inventory.create(input,master,0L));
        StorageType expected=storage==null?StorageType.FREEZER:storage;
        assertThat(added.storageType()).isEqualTo(expected);
        assertThat(added.sourceType()).isEqualTo(source);
        assertThat(added.sourceMemo()).isEqualTo(source==FoodSourceType.ETC?"출처 메모":null);
        assertThat(added.foodName()).isEqualTo("기준");
        assertThat(added.quantityAmount()).isEqualByComparingTo("0.01");
        assertThat(added.freezeType()).isEqualTo(expected!=StorageType.FREEZER?FreezeType.NONE:
                source==FoodSourceType.DELIVERY_LEFTOVER?FreezeType.HOME_FROZEN:FreezeType.COMMERCIAL_FROZEN);
        assertThat(added.frozenAt()).isEqualTo(expected!=StorageType.FREEZER?null:today?LocalDate.now(clock):date);
        assertThat(dao.find(master).versionNo()).isEqualTo(1);
    }
    @Test void additionalHistoryFailureRollsBackAllTables() {
        long master=food("기준"); var before=snapshot();
        jdbc.execute("ALTER TABLE food_history ADD CONSTRAINT audit_reject_history CHECK (memo <> '음식 등록') NOT VALID");
        try {
            assertThatThrownBy(()->inventory.create(form("추가"),master,0L)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(snapshot()).isEqualTo(before);
        } finally { jdbc.execute("ALTER TABLE food_history DROP CONSTRAINT audit_reject_history"); }
    }
    @Test void mergeRejectsMissingMastersNullTokenAndChangedRequestContent() {
        long a=food("A"),b=food("B"),c=food("C"); var before=snapshot();
        assertThatThrownBy(()->masters.merge(a,b,0,0,null)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->masters.merge(-1,b,0,0,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->masters.merge(a,-1,0,0,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(snapshot()).isEqualTo(before);
        UUID token=UUID.randomUUID(); masters.merge(a,b,0,0,token); var merged=snapshot();
        assertThatThrownBy(()->masters.merge(c,b,0,1,token)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->masters.merge(a,b,1,0,token)).isInstanceOf(InvalidFoodException.class);
        assertThat(snapshot()).isEqualTo(merged);
    }
    @Test void chainedMergeRetryDoesNotRecreateDeletedFoods() throws Exception {
        long a=food("A"),b=food("B"),c=food("C"); UUID token=UUID.randomUUID();
        masters.merge(a,b,0,0,token); merge(b,c); var before=snapshot();
        mvc.perform(post("/foods/"+a+"/merge").param("targetId",""+b).param("sourceVersion","0")
                .param("targetVersion","0").param("requestId",token.toString())).andExpect(redirectedUrl("/inventory"));
        assertThat(snapshot()).isEqualTo(before); assertThat(masters.items(c)).hasSize(3);
    }
    @Test void mixedUnitsLocationsTerminalStatesAndLegacyQuantitiesSurviveMerge() {
        long a=food("A"),b=food("B");
        for(StorageType storage:StorageType.values()) {
            var f=new FoodCreateForm("A",storage,null,new BigDecimal("0.5"),null,null,null,null,null,null,false,null,null,null,null,storage.name());
            inventory.create(f,a,dao.find(a).versionNo());
        }
        var original=masters.items(a);
        jdbc.update("UPDATE food_item SET status='DEPLETED',quantity_amount=0,quantity_unit=COALESCE(quantity_unit,'개'),quantity_text='0개' WHERE food_id=?",original.get(0).foodId());
        jdbc.update("UPDATE food_item SET status='DEPLETED',quantity_amount=0,quantity_unit=COALESCE(quantity_unit,'개'),quantity_text='0개' WHERE food_id=?",original.get(1).foodId());
        jdbc.update("UPDATE food_item SET quantity_amount=NULL,quantity_unit=NULL,quantity_text='반 봉지쯤' WHERE food_id=?",original.get(2).foodId());
        var items=jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id");
        var history=jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        var p=masters.preview(a,b); assertThat(p.itemCount()).isEqualTo(4); assertThat(p.historyCount()).isEqualTo(4);
        merge(a,b);
        var after=jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id");
        for(var rows:List.of(items,after)) rows.forEach(row->{row.remove("master_id");row.remove("updated_at");row.remove("version_no");row.remove("stock_revision");});
        assertThat(after).isEqualTo(items); assertThat(masters.items(b)).hasSize(5);
        assertThat(jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id")).isEqualTo(history);
        for(StorageType storage:StorageType.values()) {
            var expected=inventory.findActive().stream().filter(i->i.storageType()==storage).toList();
            assertThat(masters.groups(storage).stream().flatMap(g->g.items().stream()).toList()).containsExactlyElementsOf(expected);
        }
    }

    private List<Boolean> race(Callable<?> first,Callable<?> second) throws Exception {
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var work:List.of(first,second)) futures.add(pool.submit(()->{
                gate.await(); try {work.call(); return true;} catch(InvalidFoodException expected) {return false;}
            }));
            gate.countDown();
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        }
    }
    @RepeatedTest(3) void simultaneousAdditionalPurchasesWithSameVersionApplyOnce() throws Exception {
        long a=food("A");
        assertThat(race(()->inventory.create(form("one"),a,0L),()->inventory.create(form("two"),a,0L)))
                .containsExactlyInAnyOrder(true,false);
        assertThat(dao.countItems(a)).isEqualTo(2); assertThat(dao.countHistory(a)).isEqualTo(2);
        assertThat(dao.find(a).versionNo()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void additionalPurchaseVersusMergeHasOneWinner(boolean addToTarget) throws Exception {
        long a=food("A"),b=food("B"),destination=addToTarget?b:a;
        var outcomes=race(()->inventory.create(form("add"),destination,0L),()->masters.merge(a,b,0,0,UUID.randomUUID()));
        assertThat(outcomes).containsExactlyInAnyOrder(true,false);
        assertThat(inventory.findActive()).hasSize(outcomes.get(0)?3:2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(outcomes.get(0)?3:2);
        assertThat(dao.find(b)).isNotNull();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sharedEditVersusMergeHasOneWinner(boolean editTarget) throws Exception {
        long a=food("A"),b=food("B"); var item=masters.items(editTarget?b:a).getFirst();
        var outcomes=race(()->inventory.update(item.foodId(),FoodCreateForm.from(item).withIdentity("변경 이름","변경 분류"),item.updatedAt()),
                ()->masters.merge(a,b,0,0,UUID.randomUUID()));
        assertThat(outcomes).containsExactlyInAnyOrder(true,false);
        assertThat(inventory.findActive()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(outcomes.get(0)?3:2);
    }
    @RepeatedTest(3) void reciprocalMergesDoNotDeadlockOrLoseItems() throws Exception {
        long a=food("A"),b=food("B");
        assertThat(race(()->masters.merge(a,b,0,0,UUID.randomUUID()),()->masters.merge(b,a,0,0,UUID.randomUUID())))
                .containsExactlyInAnyOrder(true,false);
        assertThat(masters.groups(null)).hasSize(1); assertThat(inventory.findActive()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(2);
    }
    @Test void competingMergesIntoSameTargetRejectStaleLoser() throws Exception {
        long a=food("A"),b=food("B"),c=food("C");
        assertThat(race(()->masters.merge(a,c,0,0,UUID.randomUUID()),()->masters.merge(b,c,0,0,UUID.randomUUID())))
                .containsExactlyInAnyOrder(true,false);
        assertThat(dao.countItems(c)).isEqualTo(2); assertThat(inventory.findActive()).hasSize(3);
    }

    static Stream<Arguments> malformedFields() {
        return Stream.of("new","existing","edit").flatMap(mode -> Stream.of(
                "quantityAmount","expiredAt","sellByAt","purchasedAt","openedAt","frozenAt","sourceType","storageType","freezeType")
                .map(field -> Arguments.of(mode,field)));
    }
    @ParameterizedTest @MethodSource("malformedFields")
    void malformedFieldsRenderErrorsOnEveryWritePath(String mode,String field) throws Exception {
        long master=food("두부"); var item=masters.items(master).getFirst();var before=snapshot();
        var params=new org.springframework.util.LinkedMultiValueMap<String,String>();
        params.set("foodName","두부");params.set("quantityAmount","1");params.set("quantityUnit","모");
        params.set("storageType","FREEZER");params.set("registrationMode",mode.equals("existing")?"existing":"new");
        params.set("masterId",""+master);params.set("masterVersion","0");params.set("expectedUpdatedAt",item.updatedAt().toString());
        params.set(field,"invalid-input");
        String path=mode.equals("edit")?"/inventory/"+item.foodId()+"/edit":"/inventory";
        mvc.perform(post(path).params(params)).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("foodForm",field));
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @CsvSource({"purchasedAt,new","openedAt,new","frozenAt,new",
            "purchasedAt,existing","openedAt,existing","frozenAt,existing","purchasedAt,edit","openedAt,edit","frozenAt,edit"})
    void tomorrowIsRejectedOnEveryWritePath(String field,String mode) throws Exception {
        long master=food("두부");var item=masters.items(master).getFirst();var before=snapshot();
        var params=new org.springframework.util.LinkedMultiValueMap<String,String>();
        params.set("foodName","두부");params.set("quantityAmount","1");params.set("quantityUnit","모");
        params.set("storageType","FREEZER");params.set("registrationMode",mode.equals("existing")?"existing":"new");
        params.set("masterId",""+master);params.set("masterVersion","0");params.set("expectedUpdatedAt",item.updatedAt().toString());
        params.set(field,LocalDate.now(clock).plusDays(1).toString());
        mvc.perform(post(mode.equals("edit")?"/inventory/"+item.foodId()+"/edit":"/inventory").params(params))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm",field));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void sharedIdentityEditFailureRollsBackSiblingTimestampsAndMaster() {
        long master=food("두부"); inventory.create(form("추가"),master,0L);
        var item=masters.items(master).getFirst();var before=snapshot();
        jdbc.execute("ALTER TABLE food_history ADD CONSTRAINT audit_reject_update CHECK(action_type <> 'UPDATE') NOT VALID");
        try {
            assertThatThrownBy(()->inventory.update(item.foodId(),FoodCreateForm.from(item).withIdentity("변경","변경 분류"),item.updatedAt()))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(snapshot()).isEqualTo(before);
        } finally {jdbc.execute("ALTER TABLE food_history DROP CONSTRAINT audit_reject_update");}
    }
    @Test void sequentialConflictsRequireReselectionAndFreshVersions() {
        long a=food("A"),b=food("B");
        inventory.create(form("추가"),a,0L);var afterAdd=snapshot();
        assertThatThrownBy(()->masters.merge(a,b,0,0,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(snapshot()).isEqualTo(afterAdd);
        merge(a,b);var afterMerge=snapshot();
        assertThatThrownBy(()->inventory.create(form("추가"),a,1L)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->inventory.create(form("추가"),b,0L)).isInstanceOf(InvalidFoodException.class);
        assertThat(snapshot()).isEqualTo(afterMerge);
        inventory.create(form("새 구매"),b,dao.find(b).versionNo());assertThat(dao.countItems(b)).isEqualTo(4);
    }
    @Test void v8PreservesExplicitEtcAndRejectsMemoWithoutSource() {
        String schema="source_upgrade_audit";
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        flyway.schemas(schema).defaultSchema(schema).target("7").load().migrate();
        jdbc.update("INSERT INTO source_upgrade_audit.food_master(food_name) VALUES('기준')");
        jdbc.update("INSERT INTO source_upgrade_audit.food_item(master_id,storage_type,quantity_text,source_type,source_memo) VALUES(1,'FRIDGE','한 모','ETC','선물')");
        var before=jdbc.queryForList("SELECT * FROM source_upgrade_audit.food_item");
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        var migrated=jdbc.queryForList("SELECT * FROM source_upgrade_audit.food_item");
        migrated.forEach(row->{assertThat(row.remove("user_id")).isEqualTo(1L);assertThat(row.remove("version_no")).isEqualTo(0L);assertThat(row.remove("stock_revision")).isEqualTo(0L);});
        assertThat(migrated).isEqualTo(before);
        assertThatThrownBy(()->jdbc.update("UPDATE source_upgrade_audit.food_item SET source_type=NULL"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("UPDATE source_upgrade_audit.food_item SET source_type=NULL,source_memo=NULL");
        assertThat(jdbc.queryForObject("SELECT source_type FROM source_upgrade_audit.food_item",String.class)).isNull();
    }
    @RepeatedTest(3) void sameTokenForDifferentConcurrentMergesRejectsLoserWithoutServerError() throws Exception {
        long a=food("A"),b=food("B"),c=food("C"),d=food("D");UUID token=UUID.randomUUID();
        assertThat(race(()->masters.merge(a,b,0,0,token),()->masters.merge(c,d,0,0,token)))
                .containsExactlyInAnyOrder(true,false);
        assertThat(inventory.findActive()).hasSize(4);assertThat(masters.groups(null)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_merge_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void newRegistrationWithDifferentTokensKeepsSameNamedFoodsSeparate() throws Exception {
        for(int i=0;i<2;i++) mvc.perform(post("/inventory").param("registrationRequestId",java.util.UUID.randomUUID().toString()).param("foodName","동일 요청")
                .param("quantityAmount","1").param("quantityUnit","개").param("storageType","FRIDGE"))
                .andExpect(redirectedUrl("/inventory"));
        assertThat(masters.groups(null)).hasSize(2);assertThat(inventory.findActive()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(2);
    }
    @Test void registrationPostRetryReturnsSuccessWithoutAnotherFoodOrHistory() throws Exception {
        UUID token=UUID.randomUUID();
        for(int i=0;i<2;i++) mvc.perform(post("/inventory").param("registrationRequestId",token.toString())
                .param("foodName","두부").param("quantityAmount","1").param("quantityUnit","모").param("storageType","FRIDGE"))
                .andExpect(redirectedUrl("/inventory"));
        assertThat(masters.groups(null)).hasSize(1);assertThat(inventory.findActive()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_registration_receipt WHERE food_id IS NOT NULL",Integer.class)).isEqualTo(1);
    }
    @RepeatedTest(3) void concurrentRegistrationRetryReturnsTheSameItem() throws Exception {
        UUID token=UUID.randomUUID();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Long> work=()->{gate.await();return registrations.create(form("두부"),token);};
            var a=pool.submit(work);var b=pool.submit(work);gate.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }
        assertThat(inventory.findActive()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_registration_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void sameRegistrationTokenWithDifferentContentIsRejected() throws Exception {
        UUID token=UUID.randomUUID(); registrations.create(form("두부"),token);var before=snapshot();
        assertThatThrownBy(()->registrations.create(form("다른 음식"),token)).isInstanceOf(InvalidFoodException.class);
        assertThat(snapshot()).isEqualTo(before);
    }
    @RepeatedTest(3) void concurrentDifferentContentWithSameRegistrationTokenHasOneWinner() throws Exception {
        UUID token=UUID.randomUUID();
        assertThat(race(()->registrations.create(form("A"),token),()->registrations.create(form("B"),token)))
                .containsExactlyInAnyOrder(true,false);
        assertThat(inventory.findActive()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(1);
    }
    @Test void registrationFailureRollsBackClaimAndAllowsSameTokenRetry() {
        UUID token=UUID.randomUUID();var before=snapshot();
        jdbc.execute("ALTER TABLE food_history ADD CONSTRAINT registration_failure CHECK(memo <> '음식 등록') NOT VALID");
        try {
            assertThatThrownBy(()->registrations.create(form("두부"),token)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(snapshot()).isEqualTo(before);
        } finally {jdbc.execute("ALTER TABLE food_history DROP CONSTRAINT registration_failure");}
        long id=registrations.create(form("두부"),token);
        assertThat(registrations.create(form("두부"),token)).isEqualTo(id);assertThat(inventory.findActive()).hasSize(1);
    }
    @Test void registrationCompletionFailureRollsBackFoodHistoryAndClaim() {
        UUID token=UUID.randomUUID();var before=snapshot();
        jdbc.execute("ALTER TABLE food_registration_receipt ADD CONSTRAINT registration_completion_failure CHECK(food_id IS NULL)");
        try {
            assertThatThrownBy(()->registrations.create(form("두부"),token)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(snapshot()).isEqualTo(before);
        } finally {jdbc.execute("ALTER TABLE food_registration_receipt DROP CONSTRAINT registration_completion_failure");}
    }
    @Test void registrationValidationErrorPreservesTokenForCorrectedSubmission() throws Exception {
        UUID token=UUID.randomUUID();
        mvc.perform(post("/inventory").param("registrationRequestId",token.toString()).param("foodName","두부")
                .param("quantityAmount","0").param("quantityUnit","모").param("storageType","FRIDGE"))
                .andExpect(status().isOk()).andExpect(model().attribute("registrationRequestId",token));
        assertThat(inventory.findActive()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_registration_receipt",Integer.class)).isZero();
        mvc.perform(post("/inventory").param("registrationRequestId",token.toString()).param("foodName","두부")
                .param("quantityAmount","1").param("quantityUnit","모").param("storageType","FRIDGE"))
                .andExpect(redirectedUrl("/inventory"));
        assertThat(inventory.findActive()).hasSize(1);
    }
    @Test void registrationWithoutTokenCannotWriteAndFormProvidesRecoveryToken() throws Exception {
        mvc.perform(post("/inventory").param("foodName","두부").param("quantityAmount","1")
                .param("quantityUnit","모").param("storageType","FRIDGE"))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(model().attributeExists("registrationRequestId"));
        assertThat(inventory.findActive()).isEmpty();
        mvc.perform(post("/inventory").param("registrationRequestId","invalid").param("foodName","두부"))
                .andExpect(status().isBadRequest());
    }
    @Test void newRegistrationPagesHaveIndependentTokens() throws Exception {
        var first=mvc.perform(get("/inventory/new")).andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("registrationRequestId");
        var second=mvc.perform(get("/inventory/new")).andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("registrationRequestId");
        assertThat(first).isInstanceOf(UUID.class).isNotEqualTo(second);
    }
    @Test void originalRegistrationRetryAfterEditAndMergeDoesNotRestoreOldState() {
        UUID token=UUID.randomUUID();long id=registrations.create(form("두부"),token);
        var item=inventory.findById(id);inventory.update(id,FoodCreateForm.from(item).withIdentity("변경 이름","분류"),item.updatedAt());
        long target=food("기준");merge(masters.masterId(id),target);var before=snapshot();
        assertThat(registrations.create(form("두부"),token)).isEqualTo(id);assertThat(snapshot()).isEqualTo(before);
    }
}
