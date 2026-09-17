package com.euiseon.friger.inventory;

import java.util.*;
import java.util.concurrent.*;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
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
class FoodDeleteIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired FoodDeleteService deleteService;
    @Autowired FoodQuantityService quantities;
    @Autowired FoodMasterService masters;
    @Autowired FoodMasterDao dao;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM food_delete_receipt");jdbc.update("DELETE FROM food_quantity_receipt");
        jdbc.update("DELETE FROM food_item_move_receipt");jdbc.update("DELETE FROM food_merge_receipt");
        jdbc.update("DELETE FROM food_registration_receipt");
        jdbc.update("DELETE FROM food_history WHERE reversal_of_history_id IS NOT NULL");
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");jdbc.update("DELETE FROM food_master");
    }
    long create(String name) throws Exception {return create(name,UUID.randomUUID());}
    long create(String name,UUID registrationId) throws Exception {
        mvc.perform(post("/inventory").param("registrationRequestId",registrationId.toString())
                .param("foodName",name).param("quantityAmount","2.5").param("quantityUnit","모")
                .param("storageType","FRIDGE").param("sourceType","PURCHASE").param("memo","원래 메모"))
                .andExpect(status().is3xxRedirection());
        return jdbc.queryForObject("SELECT max(food_id) FROM food_item",Long.class);
    }
    long adopt(long item,long master) {
        long empty=masters.masterId(item);
        jdbc.update("UPDATE food_item SET master_id=? WHERE food_id=?",master,item);dao.delete(empty);
        return item;
    }
    FoodDeleteService.Command command(long master,List<Long> ids,boolean deleteMaster) {
        var selection=deleteService.selection(master,ids);
        return new FoodDeleteService.Command(master,selection.source().versionNo(),
                selection.items().stream().map(i->i.foodId()).toList(),
                selection.items().stream().map(i->selection.versions().get(i.foodId())).toList(),deleteMaster);
    }
    int historyCount(long item) {return jdbc.queryForObject("SELECT count(*) FROM food_history WHERE food_id=?",Integer.class,item);}
    @Test void deletesItemWithWholeHistoryAndKeepsSiblingAndRequestReceipts() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        long event=quantities.apply(a,CONSUME,quantities.preview(a).version(),null,UUID.randomUUID(),new java.math.BigDecimal("0.5"));
        quantities.apply(a,CANCEL,quantities.preview(a).version(),event,UUID.randomUUID(),null);
        assertThat(historyCount(a)).isEqualTo(3);
        long sourceVersionBefore=dao.find(source).versionNo();
        var siblingHistory=jdbc.queryForList("SELECT * FROM food_history WHERE food_id=? ORDER BY history_id",b);
        var result=deleteService.delete(command(source,List.of(a),false),UUID.randomUUID());
        assertThat(result.itemCount()).isEqualTo(1);assertThat(result.historyCount()).isEqualTo(3);
        assertThat(result.masterRemoved()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item WHERE food_id=?",Integer.class,a)).isZero();
        assertThat(historyCount(a)).isZero();
        assertThat(inventory.findById(b)).isNotNull();
        assertThat(jdbc.queryForList("SELECT * FROM food_history WHERE food_id=? ORDER BY history_id",b)).isEqualTo(siblingHistory);
        // The completed request records survive without FK so replays stay blocked.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_registration_receipt",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_quantity_receipt",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_delete_receipt",Integer.class)).isEqualTo(1);
        assertThat(dao.find(source).versionNo()).isGreaterThan(sourceVersionBefore);
    }
    @Test void wholeSelectionKeepsEmptyGroupUnlessAskedToRemoveIt() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        quantities.apply(b,CONSUME,quantities.preview(b).version(),null,UUID.randomUUID(),null);
        var selection=deleteService.selection(source,List.of(a,b));
        assertThat(selection.whole()).isTrue();
        var result=deleteService.delete(command(source,List.of(a,b),false),UUID.randomUUID());
        assertThat(result.itemCount()).isEqualTo(2);assertThat(result.masterRemoved()).isFalse();
        assertThat(dao.find(source)).isNotNull();
        assertThat(dao.countItems(source)).isZero();
    }
    @Test void deleteMasterRemovesEmptiedGroupAndPartialRequestIsRejected() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        assertThatThrownBy(()->deleteService.delete(command(source,List.of(a),true),UUID.randomUUID()))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(inventory.findById(a)).isNotNull();
        var result=deleteService.delete(command(source,List.of(a,b),true),UUID.randomUUID());
        assertThat(result.masterRemoved()).isTrue();
        assertThat(dao.find(source)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isZero();
    }
    @Test void sameTokenReplaysCompletedResultAndDifferentContentIsRejected() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        var c=command(source,List.of(a),false);UUID token=UUID.randomUUID();
        var first=deleteService.delete(c,token);
        var replay=deleteService.delete(c,token);
        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_delete_receipt",Integer.class)).isEqualTo(1);
        assertThat(inventory.findById(b)).isNotNull();
        var different=command(source,List.of(b),false);
        assertThatThrownBy(()->deleteService.delete(different,token)).isInstanceOf(InvalidFoodException.class);
        assertThat(inventory.findById(b)).isNotNull();
    }
    @Test void staleMasterOrItemVersionRejectsWithoutDeleting() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        var staleSource=command(source,List.of(a),false);
        dao.touch(source);
        assertThatThrownBy(()->deleteService.delete(staleSource,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        var staleItem=command(source,List.of(a),false);
        quantities.apply(a,CONSUME,quantities.preview(a).version(),null,UUID.randomUUID(),new java.math.BigDecimal("0.5"));
        assertThatThrownBy(()->deleteService.delete(staleItem,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(inventory.findById(a)).isNotNull();
        assertThat(historyCount(a)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_delete_receipt",Integer.class)).isZero();
    }
    @Test void receiptFailureRollsBackDeletedRowsEntirely() throws Exception {
        long a=create("실패 검증"),source=masters.masterId(a);
        var c=command(source,List.of(a),false);
        jdbc.execute("ALTER TABLE food_delete_receipt ADD CONSTRAINT test_delete_receipt_failure CHECK(source_name <> '실패 검증')");
        try {
            assertThatThrownBy(()->deleteService.delete(c,UUID.randomUUID()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(inventory.findById(a)).isNotNull();
            assertThat(historyCount(a)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_delete_receipt",Integer.class)).isZero();
        } finally {jdbc.execute("ALTER TABLE food_delete_receipt DROP CONSTRAINT test_delete_receipt_failure");}
    }
    @Test void deletedRegistrationRequestReplayDoesNotRecreateFood() throws Exception {
        UUID registrationId=UUID.randomUUID();
        long a=create("두부",registrationId);long source=masters.masterId(a);
        deleteService.delete(command(source,List.of(a),true),UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isZero();
        mvc.perform(post("/inventory").param("registrationRequestId",registrationId.toString())
                .param("foodName","두부").param("quantityAmount","2.5").param("quantityUnit","모")
                .param("storageType","FRIDGE").param("sourceType","PURCHASE").param("memo","원래 메모"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isZero();
    }
    @Test void endedItemsCanBeDeletedFromEndedTab() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        quantities.apply(a,CONSUME,quantities.preview(a).version(),null,UUID.randomUUID(),null);
        assertThat(inventory.findById(a).status()).isEqualTo(FoodStatus.DEPLETED);
        var result=deleteService.delete(command(source,List.of(a),false),UUID.randomUUID());
        assertThat(result.itemCount()).isEqualTo(1);assertThat(result.historyCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item WHERE food_id=?",Integer.class,a)).isZero();
        assertThat(inventory.findById(b)).isNotNull();
    }
    @Test void selectionRejectsItemsOfOtherFoodsAndEmptySelection() throws Exception {
        long a=create("두부"),other=create("콩");
        long source=masters.masterId(a);
        assertThatThrownBy(()->deleteService.selection(source,List.of(other))).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->deleteService.selection(source,List.of())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->deleteService.selection(source,null)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void confirmPageRendersFactsAndPostDeletesThenRedirects() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        mvc.perform(get("/foods/"+source)).andExpect(status().isOk())
                .andExpect(content().string(containsString("잘못 등록했어")));
        mvc.perform(get("/foods/"+source+"/delete").param("items",""+a)).andExpect(status().isOk())
                .andExpect(content().string(containsString("오등록한 항목 지우기")))
                .andExpect(content().string(containsString("이렇게 삭제할 거야")))
                .andExpect(content().string(containsString("확인했어, 지울게")));
        var selection=deleteService.selection(source,List.of(a));
        mvc.perform(post("/foods/"+source+"/delete").param("sourceId",""+source)
                .param("sourceVersion",""+selection.source().versionNo())
                .param("itemIds",""+a).param("itemVersions",""+selection.versions().get(a))
                .param("requestId",UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/foods/"+source))
                .andExpect(flash().attribute("successMessage",containsString("완전히 삭제했어")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item WHERE food_id=?",Integer.class,a)).isZero();
        // Deleting the rest with the group returns to the whole list.
        var rest=deleteService.selection(source,List.of(b));
        assertThat(rest.whole()).isTrue();
        mvc.perform(get("/foods/"+source+"/delete").param("items",""+b)).andExpect(status().isOk())
                .andExpect(content().string(containsString("그룹도 함께 삭제할게")));
        mvc.perform(post("/foods/"+source+"/delete").param("sourceId",""+source)
                .param("sourceVersion",""+rest.source().versionNo())
                .param("itemIds",""+b).param("itemVersions",""+rest.versions().get(b))
                .param("deleteMaster","true").param("requestId",UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/inventory"));
        assertThat(dao.find(source)).isNull();
    }
    @Test void confirmPageWithoutSelectionRedirectsBackToList() throws Exception {
        long a=create("두부");long source=masters.masterId(a);
        mvc.perform(get("/foods/"+source+"/delete")).andExpect(redirectedUrl("/foods/"+source));
        mvc.perform(get("/foods/"+source+"/delete").param("ended","true"))
                .andExpect(redirectedUrl("/foods/"+source+"?ended=true"));
    }
    @Test void parallelSameTokenDeletesOnce() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        var c=command(source,List.of(a),false);UUID token=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<FoodDeleteService.DeleteResult> run=()->{gate.await();return deleteService.delete(c,token);};
            var one=pool.submit(run);var two=pool.submit(run);gate.countDown();
            assertThat(one.get(10,TimeUnit.SECONDS)).isEqualTo(two.get(10,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_delete_receipt",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(1);
    }
}
