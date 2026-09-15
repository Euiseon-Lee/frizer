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
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired FoodQuantityService quantities;
    @Autowired FoodMasterService masters;
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
    long finish(long id) {return quantities.apply(id,CONSUME,quantities.preview(id).version(),null,UUID.randomUUID());}
    long cancel(long id,long history) {return quantities.apply(id,CANCEL,quantities.preview(id).version(),history,UUID.randomUUID());}
    @Test void wholeConsumptionAndCancellationPreserveIdentityAndPurchaseSnapshot() throws Exception {
        long id=create(),master=masters.masterId(id);
        var original=jdbc.queryForMap("SELECT * FROM food_history WHERE food_id=?",id);
        long event=finish(id);
        assertThat(inventory.findById(id).quantityAmount()).isZero();
        assertThat(inventory.findById(id).status()).isEqualTo(FoodStatus.DEPLETED);
        assertThat(masters.masterId(id)).isEqualTo(master);
        assertThat(dao.find(master)).isNotNull();
        assertThat(masters.groups(null)).isEmpty();assertThat(masters.groups(null,true)).hasSize(1);
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
        quantities.apply(id,DISCARD,quantities.preview(id).version(),null,UUID.randomUUID());
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("폐기 완료")));
        mvc.perform(get("/history")).andExpect(status().isOk()).andExpect(content().string(containsString("폐기")));
    }
    @Test void duplicateTokenIsIdempotentButDifferentPayloadIsRejected() throws Exception {
        long id=create(),version=quantities.preview(id).version();UUID token=UUID.randomUUID();
        long event=quantities.apply(id,CONSUME,version,null,token);
        assertThat(quantities.apply(id,CONSUME,version,null,token)).isEqualTo(event);
        assertThatThrownBy(()->quantities.apply(id,DISCARD,version,null,token)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->quantities.apply(id,CONSUME,version,null,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_quantity_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void stalePreviewRejectsAndFreshPreviewRetainsBenignChangesOnCancellation() throws Exception {
        long id=create(),event=finish(id),version=quantities.preview(id).version();
        jdbc.update("UPDATE food_item SET memo='새 메모', expired_at='2030-01-01' WHERE food_id=?",id);
        dao.update(masters.masterId(id),"풀무원 두부","반찬");
        assertThatThrownBy(()->quantities.apply(id,CANCEL,version,event,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
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
        var p=masters.preview(masters.masterId(id),masters.masterId(target));
        masters.merge(p.source().masterId(),p.target().masterId(),p.source().versionNo(),p.target().versionNo(),UUID.randomUUID());
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
            Callable<Long> task=()->{start.await();return quantities.apply(id,CONSUME,version,null,token);};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE action_type='CONSUME'",Integer.class)).isEqualTo(1);
    }
    @Test void differentConcurrentRequestsHaveOnlyOneWinner() throws Exception {
        long id=create(),version=quantities.preview(id).version();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> task=()->{start.await();try{quantities.apply(id,CONSUME,version,null,UUID.randomUUID());return true;}catch(InvalidFoodException e){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void historyFailureRollsBackQuantityAndReceiptAndAllowsRetry() throws Exception {
        long id=create(),version=quantities.preview(id).version();var token=UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_quantity_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test'; END $$");
        jdbc.execute("CREATE TRIGGER reject_quantity_history BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION reject_quantity_history()");
        try{assertThatThrownBy(()->quantities.apply(id,CONSUME,version,null,token)).isInstanceOf(RuntimeException.class);}
        finally{jdbc.execute("DROP TRIGGER reject_quantity_history ON food_history");jdbc.execute("DROP FUNCTION reject_quantity_history()");}
        assertThat(quantities.preview(id).version()).isEqualTo(version);
        assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("2.5");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_quantity_receipt",Integer.class)).isZero();
        quantities.apply(id,CONSUME,version,null,token);
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
        assertThat(masters.groups(null)).hasSize(1);assertThat(masters.groups(null,true)).isEmpty();
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("최근 소비 · 0.5모")));
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
    @org.junit.jupiter.params.provider.ValueSource(strings={"-1","0","2.501","0.0001","1000000000"})
    void invalidSelectedAmountsNeverWrite(String value) throws Exception {
        long id=create(),version=quantities.preview(id).version();
        assertThatThrownBy(()->quantities.apply(id,CONSUME,version,null,UUID.randomUUID(),new java.math.BigDecimal(value))).isInstanceOf(InvalidFoodException.class);
        assertThat(quantities.preview(id).version()).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_quantity_receipt",Integer.class)).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"-1","0","3","1e0","한글","1,5","0.0001",""})
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
            .param("requestId",UUID.randomUUID().toString()).param("quantityAmount","0.125"))
            .andExpect(redirectedUrl("/foods/"+masters.masterId(id)));
        assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("2.375");
    }
}
