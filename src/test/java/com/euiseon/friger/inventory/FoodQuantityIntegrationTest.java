package com.euiseon.friger.inventory;

import java.util.*;
import java.util.concurrent.*;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.common.type.FoodStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static com.euiseon.friger.inventory.service.FoodQuantityService.Action.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class FoodQuantityIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired FoodQuantityService quantities;
    @Autowired FoodMasterService masters;
    @Autowired ItemMoveService moves;
    @Autowired FoodMasterDao dao;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM food_quantity_receipt");jdbc.update("DELETE FROM food_item_move_receipt");jdbc.update("DELETE FROM food_merge_receipt");
        jdbc.update("DELETE FROM food_history WHERE reversal_of_history_id IS NOT NULL");
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");jdbc.update("DELETE FROM food_master");
    }
    long create() throws Exception {
        mvc.perform(post("/inventory").param("registrationRequestId",UUID.randomUUID().toString())
            .param("foodName","두부").param("quantityAmount","2.5").param("quantityUnit","모")
            .param("storageType","FRIDGE").param("sourceType","PURCHASE").param("memo","원래 메모"))
            .andExpect(status().is3xxRedirection());
        return jdbc.queryForObject("SELECT max(food_id) FROM food_item",Long.class);
    }
    long finish(long id) {return quantities.apply(id,CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),null);}
    String registrationQuantity(long id) {return jdbc.queryForObject("SELECT quantity_text FROM food_history WHERE food_id=? AND action_type='CREATE' ORDER BY history_id LIMIT 1",String.class,id);}
    long cancel(long id,long history) {return quantities.apply(id,CANCEL,quantities.preview(id).version(),history,UUID.randomUUID(),null);}
    @Test void wholeConsumptionAndCancellationPreserveIdentityAndPurchaseSnapshot() throws Exception {
        long id=create(),master=masters.masterId(id);
        var original=jdbc.queryForMap("SELECT * FROM food_history WHERE food_id=?",id);
        long event=finish(id);
        assertThat(inventory.findById(id).quantityAmount()).isZero();
        assertThat(inventory.findById(id).status()).isEqualTo(FoodStatus.DEPLETED);
        assertThat(masters.masterId(id)).isEqualTo(master);
        assertThat(dao.find(master)).isNotNull();
        assertThat(masters.groups(null,false)).isEmpty();assertThat(masters.groups(null,true)).hasSize(1);
        assertThat(quantities.preview(id).event().quantity()).isEqualTo("2.5모");
        cancel(id,event);
        assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("2.5");
        assertThat(inventory.findById(id).status()).isEqualTo(FoodStatus.ACTIVE);
        assertThat(jdbc.queryForMap("SELECT * FROM food_history WHERE history_id=?",original.get("history_id"))).isEqualTo(original);
        assertThat(original.get("processed_quantity_amount")).isEqualTo(new java.math.BigDecimal("2.500"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(3);
        assertThatThrownBy(()->cancel(id,event)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void discardedEventAndConfirmPageUseChosenWording() throws Exception {
        long id=create();
        mvc.perform(get("/inventory/"+id+"/quantity").param("action","DISCARD"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("응, 버리자!"))).andExpect(content().string(containsString("2.5모")));
        quantities.apply(id,DISCARD,quantities.preview(id).version(),null,UUID.randomUUID(),null);
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("<dt>상태</dt>"))));
        mvc.perform(get("/history")).andExpect(status().isOk()).andExpect(content().string(containsString("폐기")));
    }
    @Test void duplicateTokenIsIdempotentButDifferentPayloadIsRejected() throws Exception {
        long id=create(),version=quantities.preview(id).version();UUID token=UUID.randomUUID();
        long event=quantities.apply(id,CONSUME,version,null,token,null);
        assertThat(quantities.apply(id,CONSUME,version,null,token,null)).isEqualTo(event);
        assertThatThrownBy(()->quantities.apply(id,DISCARD,version,null,token,null)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->quantities.apply(id,CONSUME,version,null,UUID.randomUUID(),null)).isInstanceOf(InvalidFoodException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_quantity_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void stalePreviewRejectsAndFreshPreviewRetainsBenignChangesOnCancellation() throws Exception {
        long id=create(),event=finish(id),version=quantities.preview(id).version();
        jdbc.update("UPDATE food_item SET memo='새 메모', expired_at='2030-01-01' WHERE food_id=?",id);
        dao.update(masters.masterId(id),"풀무원 두부","반찬");
        assertThatThrownBy(()->quantities.apply(id,CANCEL,version,event,UUID.randomUUID(),null)).isInstanceOf(InvalidFoodException.class);
        assertThat(quantities.preview(id).cancellable()).isTrue();cancel(id,event);
        assertThat(inventory.findById(id).memo()).isEqualTo("새 메모");
        assertThat(inventory.findById(id).foodName()).isEqualTo("풀무원 두부");
        assertThat(inventory.findById(id).expiredAt()).isEqualTo(java.time.LocalDate.of(2030,1,1));
    }
    @Test void changingAndRestoringSensitiveFieldStillBlocksCancellation() throws Exception {
        long id=create(),event=finish(id);
        jdbc.update("UPDATE food_item SET opened_at='2026-09-01' WHERE food_id=?",id);
        jdbc.update("UPDATE food_item SET opened_at=NULL WHERE food_id=?",id);
        assertThat(quantities.preview(id).cancellable()).isFalse();
        assertThatThrownBy(()->cancel(id,event)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void wholeFoodMovePreservesEndStateButBlocksCancellation() throws Exception {
        long id=create(),target=create(),event=finish(id);
        var p=moves.preview(masters.masterId(id),List.of(id),ItemMoveService.Mode.EXISTING,masters.masterId(target),null,null);
        moves.move(p.command(),p.requestId());
        assertThat(inventory.findById(id).status()).isEqualTo(FoodStatus.DEPLETED);
        assertThatThrownBy(()->cancel(id,event)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void oldConsumedThenCancelledEventCannotCancelANewerProcessing() throws Exception {
        long id=create(),old=finish(id);cancel(id,old);long latest=finish(id);
        assertThatThrownBy(()->cancel(id,old)).isInstanceOf(InvalidFoodException.class);cancel(id,latest);
    }
    @Test void concurrentDuplicateRequestsWriteOnce() throws Exception {
        long id=create(),version=quantities.preview(id).version();var token=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Long> task=()->{start.await();return quantities.apply(id,CONSUME,version,null,token,null);};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE action_type='CONSUME'",Integer.class)).isEqualTo(1);
    }
    @Test void differentConcurrentRequestsHaveOnlyOneWinner() throws Exception {
        long id=create(),version=quantities.preview(id).version();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> task=()->{start.await();try{quantities.apply(id,CONSUME,version,null,UUID.randomUUID(),null);return true;}catch(InvalidFoodException e){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void historyFailureRollsBackQuantityAndReceiptAndAllowsRetry() throws Exception {
        long id=create(),version=quantities.preview(id).version();var token=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_quantity_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test'; END $$");
        jdbc.execute("CREATE TRIGGER reject_quantity_history BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION reject_quantity_history()");
        try{assertThatThrownBy(()->quantities.apply(id,CONSUME,version,null,token,null)).isInstanceOf(RuntimeException.class);}
        finally{jdbc.execute("DROP TRIGGER reject_quantity_history ON food_history");jdbc.execute("DROP FUNCTION reject_quantity_history()");}
        assertThat(quantities.preview(id).version()).isEqualTo(version);
        assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("2.5");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_quantity_receipt",Integer.class)).isZero();
        quantities.apply(id,CONSUME,version,null,token,null);
    }
    @Test void mixedGroupFiltersAndEndedWarningSuppressionRenderCorrectly() throws Exception {
        long id=create(),active=create(),master=masters.masterId(id);
        jdbc.update("UPDATE food_item SET master_id=? WHERE food_id=?",master,active);finish(id);
        var current=mvc.perform(get("/foods/"+master)).andExpect(status().isOk()).andReturn().getModelAndView();
        assertThat((List<?>)current.getModel().get("items")).hasSize(1);
        mvc.perform(get("/inventory").param("ended","true").param("warning","true").param("storage","FREEZER"))
            .andExpect(status().isOk()).andExpect(model().attribute("warningOnly",false)).andExpect(model().attribute("savedWarning",true))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("type=\"checkbox\""))))
            .andExpect(content().string(containsString("ended=true")));
        mvc.perform(get("/foods/"+master).param("ended","true")).andExpect(status().isOk()).andExpect(content().string(containsString("보관 종료")));
    }
    @Test void exhaustedGroupCanReceiveNewStockWithoutRevivingOldItem() throws Exception {
        long id=create(),master=masters.masterId(id);finish(id);
        assertThat(masters.registrationChoices()).extracting(FoodMasterDao.RegistrationChoice::masterId).contains(master);
        mvc.perform(post("/inventory").param("registrationMode","existing").param("masterId",Long.toString(master))
            .param("masterVersion",Long.toString(dao.find(master).versionNo())).param("quantityAmount","3").param("quantityUnit","모").param("storageType","FRIDGE"))
            .andExpect(status().is3xxRedirection());
        assertThat(masters.items(master)).hasSize(2);assertThat(inventory.findById(id).status()).isEqualTo(FoodStatus.DEPLETED);
    }
    @Test void unstructuredQuantityRequiresUserInputBeforeProcessing() throws Exception {
        long id=create();jdbc.update("UPDATE food_item SET quantity_amount=NULL,quantity_unit=NULL,quantity_text='몇 개' WHERE food_id=?",id);
        assertThatThrownBy(()->finish(id)).isInstanceOf(InvalidFoodException.class);
        mvc.perform(get("/inventory/"+id+"/quantity").param("action","CONSUME"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("수량과 단위")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-quantity-form"))));
    }
    @Test void postPreservesReturnFiltersAndHistoryOffersCancellation() throws Exception {
        long id=create();
        mvc.perform(post("/inventory/"+id+"/quantity").param("action","CONSUME").param("version",Long.toString(quantities.preview(id).version()))
            .param("requestId",UUID.randomUUID().toString()).param("quantityAmount","2.5").param("storage","FRIDGE").param("warning","true"))
            .andExpect(redirectedUrl("/foods/"+masters.masterId(id)+"?storage=FRIDGE&warning=true"));
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("처리 취소")));
    }
    @Test void partialConsumptionKeepsRemainingStockAndCancellationRestoresOnlyProcessedQuantity() throws Exception {
        long id=create();
        long event=quantities.apply(id,CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("0.5"));
        assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("2");
        assertThat(inventory.findById(id).status()).isEqualTo(FoodStatus.ACTIVE);
        assertThat(quantities.preview(id).cancellable()).isTrue();
        assertThat(masters.groups(null,false)).hasSize(1);assertThat(masters.groups(null,true)).isEmpty();
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("수량 변경 이력")));
        cancel(id,event);assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("2.5");
        assertThat(jdbc.queryForObject("SELECT after_quantity_amount-before_quantity_amount FROM food_history WHERE reversal_of_history_id=?",java.math.BigDecimal.class,event)).isEqualByComparingTo("0.5");
    }
    @Test void partialThenFullDiscardAndCancelDoesNotAllowEarlierPartialUndo() throws Exception {
        long id=create();
        long first=quantities.apply(id,DISCARD,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("1"));
        long last=quantities.apply(id,DISCARD,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("1.5"));
        assertThat(inventory.findById(id).status()).isEqualTo(FoodStatus.DEPLETED);
        cancel(id,last);assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("1.5");
        assertThatThrownBy(()->cancel(id,first)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void selectedAmountIsPartOfIdempotencyKeyAndCanonicalDecimalsReplay() throws Exception {
        long id=create(),version=quantities.preview(id).version();UUID token=UUID.randomUUID();
        long event=quantities.apply(id,CONSUME,version,null,token,new java.math.BigDecimal("1.0"));
        assertThat(quantities.apply(id,CONSUME,version,null,token,new java.math.BigDecimal("1.00"))).isEqualTo(event);
        assertThatThrownBy(()->quantities.apply(id,CONSUME,version,null,token,new java.math.BigDecimal("2"))).isInstanceOf(InvalidFoodException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"-1","0","2.501","0.001","0.0001","1000000000"})
    void invalidSelectedAmountsNeverWrite(String value) throws Exception {
        long id=create(),version=quantities.preview(id).version();
        assertThatThrownBy(()->quantities.apply(id,CONSUME,version,null,UUID.randomUUID(),new java.math.BigDecimal(value))).isInstanceOf(InvalidFoodException.class);
        assertThat(quantities.preview(id).version()).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_quantity_receipt",Integer.class)).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"-1","0","3","1e0","한글","1,5","0.001","0.0001",""})
    void forgedFormValuesAreRejectedByServer(String value) throws Exception {
        long id=create(),version=quantities.preview(id).version();
        mvc.perform(post("/inventory/"+id+"/quantity").param("action","CONSUME").param("version",Long.toString(version))
            .param("requestId",UUID.randomUUID().toString()).param("quantityAmount",value))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        assertThat(quantities.preview(id).version()).isEqualTo(version);
    }
    @Test void partialAmountFormUpdatesQuantityAndRendersFieldsAndButtons() throws Exception {
        long id=create(),version=quantities.preview(id).version();
        mvc.perform(get("/inventory/"+id+"/quantity").param("action","CONSUME"))
            .andExpect(content().string(containsString("기준 수량"))).andExpect(content().string(containsString("먹은 수량")))
            .andExpect(content().string(containsString("응, 좋아!"))).andExpect(content().string(containsString("quantity-consume")));
        mvc.perform(post("/inventory/"+id+"/quantity").param("action","CONSUME").param("version",Long.toString(version))
            .param("requestId",UUID.randomUUID().toString()).param("quantityAmount","0.12"))
            .andExpect(redirectedUrl("/foods/"+masters.masterId(id)));
        assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("2.38");
    }

    @Test void purchaseHistoryShowsQuantityChangesAndCancellationsNewestFirst() throws Exception {
        long id=create(),other=create();
        long registration=quantities.history(id).getFirst().historyId();
        quantities.apply(other,CONSUME,quantities.preview(other).version(),null,UUID.randomUUID(),null);
        long first=quantities.apply(id,CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("0.5"));
        long last=quantities.apply(id,DISCARD,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("1"));
        assertThat(quantities.history(id)).extracting(QuantityChange::historyId).containsExactly(last,first,registration);
        assertThat(quantities.history(id)).extracting(QuantityChange::remainingQuantity).containsExactly("1모","2모","2.5모");
        mvc.perform(get("/inventory/"+id))
            .andExpect(status().isOk())
            .andExpect(htmlCount("<details[^>]* open",0))
            .andExpect(htmlCount("<strong class=\"quantity-history-amount\"",3))
            .andExpect(content().string(containsString("class=\"quantity-history-amount\">-1모</strong>")))
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",1))
            .andExpect(content().string(containsString("aria-label=\"폐기 -1모 처리 취소\"")));
        long reversal=cancel(id,last);
        assertThat(quantities.history(id)).extracting(QuantityChange::historyId).containsExactly(reversal,last,first,registration);
        assertThat(quantities.preview(id).cancellable()).isFalse();
        assertThatThrownBy(()->cancel(id,first)).isInstanceOf(InvalidFoodException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE history_id=?",Integer.class,last)).isEqualTo(1);
        jdbc.update("UPDATE food_item SET quantity_amount=5,quantity_text='5모' WHERE food_id=?",id);
        assertThat(quantities.history(id)).extracting(QuantityChange::remainingQuantity).containsExactly("2모","1모","2모","2.5모");
        assertThat(registrationQuantity(id)).isEqualTo("2.5모");
    }
    @Test void purchaseHistoryCancellationIsAbsentWithoutHistoryAndOnlyOnLatestEligibleRow() throws Exception {
        long id=create();
        String purchaseHelp="잔량이 수정되니, 새로 구매했다면 ‘추가 등록’을 이용해줘.";
        var edit=mvc.perform(get("/inventory/"+id+"/edit")).andExpect(status().isOk())
            .andExpect(content().string(containsString(purchaseHelp))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(get("/inventory/new")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(purchaseHelp))));
        if(Boolean.getBoolean("frizer.exportPurchasePreview")) {
            var dir=java.nio.file.Path.of("build/reports/purchase-detail-preview");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("edit.html"),edit);
        }
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",0))
            .andExpect(htmlCount("class=\"consumption-history-list\"",1));
        quantities.apply(id,CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("0.5"));
        var partial=mvc.perform(get("/inventory/"+id).param("warning","true").param("storage","FRIDGE"))
            .andExpect(status().isOk())
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",1))
            .andExpect(content().string(containsString(">처리 취소</a>")))
            .andExpect(htmlCount("(?s)<section class=\"form-section purchase-detail-card\">.*?<div class=\"stock-card-footer purchase-detail-footer\">.*?등록.*?수정.*?</div>\\s*</section>",1))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if(Boolean.getBoolean("frizer.exportPurchasePreview")) {
            var dir=java.nio.file.Path.of("build/reports/purchase-detail-preview");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("partial.html"),partial);
        }
        long event=finish(id);
        var ended=mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("<dt>최초 등록 수량</dt><dd>2.5모</dd>")))
            .andExpect(content().string(containsString("(잔량: <span>0모)</span>")))
            .andExpect(htmlCount("class=\"button quantity-(consume|discard)\"",0))
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",1))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if(Boolean.getBoolean("frizer.exportPurchasePreview"))java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/purchase-detail-preview/ended.html"),ended);
        jdbc.update("UPDATE food_item SET opened_at='2026-09-01' WHERE food_id=?",id);
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",0));
        assertThatThrownBy(()->cancel(id,event)).isInstanceOf(InvalidFoodException.class);
    }

    @Test void cancelledProcessingRemainsInQuantityTimelineWithoutCancelButton() throws Exception {
        long id=create(),event=finish(id);
        mvc.perform(get("/foods/"+masters.masterId(id)).param("ended","true")).andExpect(status().isOk())
            .andExpect(content().string(containsString("다른 음식으로 병합")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("추가 등록"))))
            .andExpect(content().string(containsString("전체 목록으로")));
        cancel(id,event);
        mvc.perform(get("/foods/"+masters.masterId(id))).andExpect(status().isOk())
            .andExpect(content().string(containsString("추가 등록")))
            .andExpect(content().string(containsString("다른 음식으로 병합")));
        assertThat(quantities.history(id)).hasSize(3);
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(htmlCount("class=\"consumption-history-list\"",1))
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",0))
            .andExpect(content().string(containsString("data-event=\"CANCEL\">취소</span>")))
            .andExpect(content().string(containsString("+2.5모")));
        mvc.perform(get("/history")).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-event=\"CANCEL\">취소</span>")));
    }

    @Test void endedCardsUseOriginalQuantityAndLatestUncancelledZeroBalanceTime() throws Exception {
        long id=create(),master=masters.masterId(id);
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<dt>상태</dt>"))));
        long first=finish(id);
        jdbc.update("UPDATE food_history SET created_at='2026-09-14T01:00:00Z' WHERE history_id=?",first);
        assertThat(quantities.endedSummaries(master).get(id).endedAt().toInstant())
            .isEqualTo(java.time.Instant.parse("2026-09-14T01:00:00Z"));
        cancel(id,first);
        assertThat(quantities.endedSummaries(master)).isEmpty();
        long last=quantities.apply(id,DISCARD,quantities.preview(id).version(),null,UUID.randomUUID(),null);
        jdbc.update("UPDATE food_history SET created_at='2026-09-15T13:41:00Z' WHERE history_id=?",last);
        jdbc.update("UPDATE food_item SET updated_at='2026-09-16T01:00:00Z' WHERE food_id=?",id);
        assertThat(quantities.endedSummaries(master).get(id).registrationQuantity()).isEqualTo("2.5모");
        var card=mvc.perform(get("/foods/"+master).param("ended","true")).andExpect(status().isOk())
            .andExpect(content().string(containsString("(2026-09-15 22:41)")))
            .andExpect(content().string(containsString("최초 등록 수량 <strong>2.5모</strong>")))
            .andExpect(htmlCount("class=\"stock-dates\"",0))
            .andExpect(htmlCount("class=\"stock-card-footer\"",0))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(get("/inventory/"+id).param("ended","true")).andExpect(status().isOk())
            .andExpect(content().string(containsString("2026-09-15 22:41")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<dt>상태</dt>"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<dt>구매일</dt>"))))
            .andExpect(htmlCount("class=\"stock-card-footer purchase-detail-footer\"",0))
            .andExpect(htmlCount("<details[^>]*open=\"open\"",1));
        if(Boolean.getBoolean("frizer.exportPurchasePreview")) {
            var dir=java.nio.file.Path.of("build/reports/purchase-detail-preview");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("ended-list.html"),card);
        }
    }

    @Test void purchaseUndoBelongsToDismissibleNoticeOnly() throws Exception {
        long id=create(),master=masters.masterId(id);
        var html=mvc.perform(get("/foods/"+master).flashAttr("successMessage","먹은 것으로 기록했어.")
                .flashAttr("processedItemId",id)).andExpect(status().isOk())
            .andExpect(htmlCount("(?s)<div class=\"notice purchase-notice\"[^>]*>.*?<a class=\"purchase-undo\".*?</div>\\s*</div>",1))
            .andExpect(htmlCount("class=\"purchase-undo\"",1))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(get("/foods/"+master)).andExpect(status().isOk()).andExpect(htmlCount("class=\"purchase-undo\"",0));
        mvc.perform(get("/foods/"+master).flashAttr("successMessage","처리를 취소했어. 다시 보관 중이야."))
            .andExpect(status().isOk()).andExpect(htmlCount("class=\"purchase-undo\"",0));
        if(Boolean.getBoolean("frizer.exportPurchasePreview")) {
            var dir=java.nio.file.Path.of("build/reports/purchase-detail-preview");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("purchase-notice.html"),html);
        }
    }

    private void correct(long id,String amount,String unit,String memo) throws Exception {
        mvc.perform(post("/inventory/"+id+"/edit").param("foodName","두부").param("storageType","FRIDGE")
            .param("quantityAmount",amount).param("quantityUnit",unit).param("sourceType","PURCHASE").param("memo",memo)
            .param("expectedUpdatedAt",inventory.findById(id).updatedAt().toString()))
            .andExpect(redirectedUrl("/foods/"+masters.masterId(id)));
    }
    @Test void quantityCorrectionsUseStoredDeltasAndDoNotBecomePurchases() throws Exception {
        long id=create();
        quantities.apply(id,CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("0.5"));
        correct(id,"5","모","원래 메모");
        quantities.apply(id,DISCARD,quantities.preview(id).version(),null,UUID.randomUUID(),new java.math.BigDecimal("1"));
        assertThat(quantities.history(id)).extracting(QuantityChange::changeQuantity).containsExactly("-1모","+3모","-0.5모","+2.5모");
        assertThat(quantities.history(id)).extracting(QuantityChange::remainingQuantity).containsExactly("4모","5모","2모","2.5모");
        assertThat(registrationQuantity(id)).isEqualTo("2.5모");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE food_id=? AND action_type='CREATE'",Integer.class,id)).isEqualTo(1);
        var page=mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("수량 변경 이력")))
            .andExpect(content().string(containsString("data-event=\"UPDATE\">수정</span>")))
            .andExpect(content().string(containsString("+3모")))
            .andExpect(content().string(containsString("(잔량: <span>5모)</span>")))
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",1))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if(Boolean.getBoolean("frizer.exportPurchasePreview")) {
            var dir=java.nio.file.Path.of("build/reports/purchase-detail-preview");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("changes.html"),page);
            for (var route : Map.of("history-tags.html", "/history", "home-tags.html", "/").entrySet()) {
                var html=mvc.perform(get(route.getValue())).andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-event=\"UPDATE\">수정</span>")))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                java.nio.file.Files.writeString(dir.resolve(route.getKey()),html);
            }
        }
        correct(id,"4","모","메모만 바꿔");
        assertThat(quantities.history(id)).hasSize(4);
        assertThat(quantities.preview(id).cancellable()).isTrue();
        correct(id,"3.5","모","메모만 바꿔");
        assertThat(quantities.history(id).getFirst().changeQuantity()).isEqualTo("-0.5모");
        assertThat(quantities.preview(id).cancellable()).isFalse();
    }
    @Test void unitCorrectionPreservesBeforeUnitWithoutConvertingQuantities() throws Exception {
        long id=create();correct(id,"2.5","봉지","원래 메모");
        var change=quantities.history(id).getFirst();
        assertThat(change.actionLabel()).isEqualTo("수정");
        assertThat(change.changeQuantity()).isEqualTo("2.5모 → 2.5봉지");
        assertThat(change.remainingQuantity()).isEqualTo("2.5봉지");
        assertThat(registrationQuantity(id)).isEqualTo("2.5모");
        correct(id,"2.5","봉지","원래 메모");
        assertThat(quantities.history(id)).hasSize(2);
    }
    @Test void registrationAppearsWithoutCancelAndPreservesHistoricalQuantity() throws Exception {
        long id=create();
        var registration=quantities.history(id).getFirst();
        assertThat(registration.actionLabel()).isEqualTo("등록");
        assertThat(registration.changeQuantity()).isEqualTo("+2.5모");
        assertThat(registration.remainingQuantity()).isEqualTo("2.5모");
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-event=\"CREATE\">등록</span>")))
            .andExpect(content().string(containsString("class=\"quantity-history-amount\">+2.5모</strong>")))
            .andExpect(htmlCount("class=\"quantity-history-cancel\"",0));
        jdbc.update("UPDATE food_history SET after_quantity_amount=NULL,quantity_unit=NULL,quantity_text='두 묶음' WHERE history_id=?",registration.historyId());
        correct(id,"4","모","현재 수량 정정");
        var original=quantities.history(id).getLast();
        assertThat(original.actionLabel()).isEqualTo("등록");
        assertThat(original.changeQuantity()).isEqualTo("두 묶음");
        assertThat(original.remainingQuantity()).isEqualTo("두 묶음");
    }
    private static org.springframework.test.web.servlet.ResultMatcher htmlCount(String pattern,long count) {
        return result -> assertThat(java.util.regex.Pattern.compile(pattern).matcher(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).results().count()).isEqualTo(count);
    }
}