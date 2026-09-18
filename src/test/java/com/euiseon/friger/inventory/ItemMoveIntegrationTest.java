package com.euiseon.friger.inventory;

import java.util.*;
import java.util.concurrent.*;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.history.dao.HistoryDao;
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

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ItemMoveIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired ItemMoveService moves;
    @Autowired FoodMasterService masters;
    @Autowired FoodMasterDao dao;
    @Autowired InventoryService inventory;
    @Autowired HistoryDao history;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM food_item_move_receipt");jdbc.update("DELETE FROM food_merge_receipt");
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");jdbc.update("DELETE FROM food_master");
    }
    long create(String name) throws Exception {
        mvc.perform(post("/inventory").param("registrationRequestId",UUID.randomUUID().toString())
                .param("foodName",name).param("quantityAmount","2").param("quantityUnit","개")
                .param("storageType","FRIDGE").param("memo","보존할 메모"))
                .andExpect(status().is3xxRedirection());
        return jdbc.queryForObject("SELECT max(food_id) FROM food_item",Long.class);
    }
    long adopt(long item,long master) {
        long empty=masters.masterId(item);
        jdbc.update("UPDATE food_item SET master_id=? WHERE food_id=?",master,item);dao.delete(empty);
        return item;
    }
    ItemMoveService.Preview existing(List<Long> items,long targetItem) {
        return moves.preview(masters.masterId(items.getFirst()),items,ItemMoveService.Mode.EXISTING,masters.masterId(targetItem),null,null);
    }
    ItemMoveService.Preview toNew(List<Long> items,String name,String category) {
        return moves.preview(masters.masterId(items.getFirst()),items,ItemMoveService.Mode.NEW,null,name,category);
    }
    ItemMoveService.MoveResult execute(ItemMoveService.Preview p) {return moves.move(p.command(),p.requestId());}
    @Test void movesOnlySelectedItemsAndPreservesAllOtherData() throws Exception {
        long a=create("듀부"), b=create("듀부"), target=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        var before=jdbc.queryForMap("SELECT * FROM food_item WHERE food_id=?",a);
        var originalHistory=jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        var p=existing(List.of(a),target);
        assertThat(p.whole()).isFalse();
        assertThat(p.historyCount()).isEqualTo(1);assertThat(p.remainingCount()).isEqualTo(1);
        var result=execute(p);
        assertThat(result.sourceRemoved()).isFalse();
        var after=jdbc.queryForMap("SELECT * FROM food_item WHERE food_id=?",a);
        before.remove("master_id");before.remove("updated_at");before.remove("version_no");before.remove("stock_revision");after.remove("master_id");after.remove("updated_at");after.remove("version_no");after.remove("stock_revision");
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id")).isEqualTo(originalHistory);
        assertThat(masters.masterId(b)).isEqualTo(source);
        assertThat(masters.masterId(a)).isEqualTo(masters.masterId(target));
        assertThat(history.findRecent(10).stream().filter(e->e.isMerge()).findFirst().orElseThrow().foodId()).isEqualTo(a);
    }
    @Test void multiSelectionMovesTogetherWithOneRequest() throws Exception {
        long a=create("듀부"),b=create("듀부"),c=create("듀부"),target=create("두부"),source=masters.masterId(a);
        adopt(b,source);adopt(c,source);
        var p=existing(List.of(a,b),target);
        assertThat(p.whole()).isFalse();assertThat(p.remainingCount()).isEqualTo(1);
        assertThat(p.historyCount()).isEqualTo(2);
        var result=execute(p);
        assertThat(result.sourceRemoved()).isFalse();
        assertThat(masters.masterId(a)).isEqualTo(masters.masterId(target));
        assertThat(masters.masterId(b)).isEqualTo(masters.masterId(target));
        assertThat(masters.masterId(c)).isEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT request_id) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void splitCreatesNewIdentityEvenForSameNameAndIsIdempotent() throws Exception {
        long a=create("두부"),source=masters.masterId(a);
        var p=toNew(List.of(a),"두부","새 분류");
        assertThat(p.whole()).isTrue();
        execute(p);execute(p);
        assertThat(masters.masterId(a)).isNotEqualTo(source);assertThat(dao.find(source)).isNull();
        assertThat(inventory.findById(a).category()).isEqualTo("새 분류");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void completedTokenCannotBeReusedForDifferentInput() throws Exception {
        long a=create("두부");var p=toNew(List.of(a),"두부2",null);execute(p);
        var c=p.command();
        assertThatThrownBy(()->moves.move(new ItemMoveService.Command(c.mode(),null,"다른 이름",null,c.sourceId(),c.sourceVersion(),null,c.itemIds(),c.whole()),p.requestId())).isInstanceOf(InvalidFoodException.class);
        assertThat(inventory.findById(a).foodName()).isEqualTo("두부2");
    }
    @Test void staleSourceAndTargetRejectWithoutMoving() throws Exception {
        long a=create("두부"),extra=create("두부"),b=create("콩");adopt(extra,masters.masterId(a));
        var p=existing(List.of(a),b);dao.touch(p.source().masterId());
        var staleSource=p;assertThatThrownBy(()->execute(staleSource)).isInstanceOf(InvalidFoodException.class);
        p=existing(List.of(a),b);dao.touch(p.command().targetId());var stale=p;
        assertThatThrownBy(()->execute(stale)).isInstanceOf(InvalidFoodException.class);
        assertThat(masters.masterId(a)).isEqualTo(p.source().masterId());
    }
    @Test void partialSelectionLeavesEndedRecordsAndEndedItemsCanMove() throws Exception {
        long a=create("두부"),x=create("두부"),b=create("두부"),t=create("콩"),source=masters.masterId(a);
        adopt(x,source);adopt(b,source);
        jdbc.update("UPDATE food_item SET status='DEPLETED',quantity_amount=0,quantity_text='0개' WHERE food_id=?",b);
        // Selecting some of the stored items keeps the food and its ended record.
        var p=existing(List.of(a),t);
        assertThat(p.whole()).isFalse();assertThat(p.endedCount()).isEqualTo(1);
        assertThat(execute(p).sourceRemoved()).isFalse();
        assertThat(dao.find(source)).isNotNull();
        // Ended items move with the same flow; the emptied food is removed with the last one.
        var endedMove=existing(List.of(b),t);
        assertThat(endedMove.whole()).isFalse();
        assertThat(execute(endedMove).sourceRemoved()).isFalse();
        assertThat(masters.masterId(b)).isEqualTo(masters.masterId(t));
        var last=existing(List.of(x),t);
        assertThat(last.whole()).isTrue();
        // The last whole selection merges directly and retires the emptied food.
        assertThat(execute(last).sourceRemoved()).isTrue();
        assertThat(dao.find(source)).isNull();
    }
    @Test void wholeSelectionToNewFoodTakesEndedRecordsAlong() throws Exception {
        long a=create("두부"),b=create("두부"),source=masters.masterId(a);
        adopt(b,source);
        jdbc.update("UPDATE food_item SET status='DEPLETED',quantity_amount=0,quantity_text='0개' WHERE food_id=?",b);
        var p=toNew(List.of(a),"콩",null);
        assertThat(p.whole()).isTrue();assertThat(p.endedCount()).isEqualTo(1);
        var result=execute(p);
        assertThat(result.sourceRemoved()).isTrue();
        assertThat(dao.find(source)).isNull();
        assertThat(masters.masterId(a)).isEqualTo(result.targetId());
        assertThat(masters.masterId(b)).isEqualTo(result.targetId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(2);
    }
    @Test void wholeMovesRecordPerItemEntriesThatFollowTheItem() throws Exception {
        long a=create("A"),b=create("B"),c=create("C");
        execute(existing(List.of(a),b));
        execute(existing(List.of(a),c));
        execute(existing(List.of(b),c));
        var events=history.findRecent(20);
        var moved=events.stream().filter(e->e.isMerge()).toList();
        assertThat(moved).allSatisfy(e->{assertThat(e.actionType()).isNull();assertThat(e.foodId()).isNotNull();});
        assertThat(moved).hasSize(3).allSatisfy(e->assertThat(e.currentMasterId()).isEqualTo(masters.masterId(c)));
        assertThat(events.stream().filter(e->Objects.equals(e.foodId(),a)).toList()).allSatisfy(e->assertThat(e.currentMasterId()).isEqualTo(masters.masterId(c)));
        assertThat(moves.preview(masters.masterId(a),List.of(a),ItemMoveService.Mode.NEW,null,"A",null).historyCount()).isEqualTo(3);
    }
    @Test void previewAndSearchDoNotWriteAndWholePostMergesToTarget() throws Exception {
        long a=create("듀부"),b=create("두부");
        long source=masters.masterId(a),target=masters.masterId(b);
        mvc.perform(get("/foods/"+source+"/move").param("items",""+a)).andExpect(status().isOk())
            .andExpect(content().string(containsString("새로운 음식으로 변경")))
            .andExpect(content().string(containsString("이 경우 병합 시 기존 음식은 사라져.")))
            .andExpect(content().string(containsString("data-value=\""+target+"\"")));
        mvc.perform(get("/foods/"+source+"/move/preview").param("items",""+a).param("mode","EXISTING").param("targetId",""+target))
            .andExpect(status().isOk()).andExpect(jsonPath("$.whole").value(true)).andExpect(jsonPath("$.remainingCount").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isZero();
        var p=existing(List.of(a),b);var c=p.command();
        mvc.perform(post("/foods/"+source+"/move").param("mode","EXISTING").param("targetId",""+c.targetId())
            .param("sourceId",""+c.sourceId()).param("sourceVersion",""+c.sourceVersion()).param("targetVersion",""+c.targetVersion())
            .param("itemIds",""+a).param("whole","true").param("requestId",p.requestId().toString()))
            .andExpect(redirectedUrl("/foods/"+target));
        assertThat(dao.find(source)).isNull();
        mvc.perform(get("/history")).andExpect(status().isOk()).andExpect(content().string(containsString("듀부")));
    }
    @Test void partialPostRedirectsBackToSourceList() throws Exception {
        long a=create("듀부"),extra=create("듀부"),b=create("두부");
        long source=masters.masterId(a);adopt(extra,source);
        var p=existing(List.of(a),b);var c=p.command();
        mvc.perform(post("/foods/"+source+"/move").param("mode","EXISTING").param("targetId",""+c.targetId())
            .param("sourceId",""+c.sourceId()).param("sourceVersion",""+c.sourceVersion()).param("targetVersion",""+c.targetVersion())
            .param("itemIds",""+a).param("requestId",p.requestId().toString()))
            .andExpect(redirectedUrl("/foods/"+source));
        assertThat(masters.masterId(a)).isEqualTo(masters.masterId(b));
        assertThat(masters.masterId(extra)).isEqualTo(source);
    }
    @Test void validationRejectsSameTargetBlankNameAndTooLongCategory() throws Exception {
        long a=create("두부");
        assertThatThrownBy(()->existing(List.of(a),a)).isInstanceOf(InvalidFoodException.class);
        // A blank name previews (the page only informs) but is rejected on submit.
        var blank=toNew(List.of(a)," ",null);
        assertThatThrownBy(()->moves.move(blank.command(),blank.requestId())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->toNew(List.of(a),"두부","가".repeat(51))).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->moves.preview(masters.masterId(a),List.of(),ItemMoveService.Mode.NEW,null,"두부",null)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->moves.preview(masters.masterId(a),List.of(a+99),ItemMoveService.Mode.NEW,null,"두부",null)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void parallelSubmissionCreatesOneSplit() throws Exception {
        long a=create("두부");var p=toNew(List.of(a),"콩",null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Long> run=()->{gate.await();return moves.move(p.command(),p.requestId()).targetId();};
            var one=pool.submit(run);var two=pool.submit(run);gate.countDown();
            assertThat(one.get(10,TimeUnit.SECONDS)).isEqualTo(two.get(10,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void sourceChangeRejectsSplitWithoutCreatingMaster() throws Exception {
        long a=create("두부");var p=toNew(List.of(a),"콩",null);dao.touch(p.source().masterId());
        assertThatThrownBy(()->execute(p)).isInstanceOf(InvalidFoodException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
    }
    @Test void receiptFailureRollsBackNewMasterAndItemTransfer() throws Exception {
        long a=create("두부"),source=masters.masterId(a);
        var p=toNew(List.of(a),"실패 검증",null);
        jdbc.execute("ALTER TABLE food_item_move_receipt ADD CONSTRAINT test_receipt_failure CHECK(target_name <> '실패 검증')");
        try {
            assertThatThrownBy(()->execute(p)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(masters.masterId(a)).isEqualTo(source);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isZero();
        } finally {jdbc.execute("ALTER TABLE food_item_move_receipt DROP CONSTRAINT test_receipt_failure");}
    }
    @Test void itemMoveAndWholeMoveCompeteWithoutDeadlockOrLostItems() throws Exception {
        long a=create("두부"),extra=create("두부"),b=create("콩"),c=create("채소");
        adopt(extra,masters.masterId(a));
        var single=existing(List.of(a),b);var whole=existing(List.of(a,extra),c);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var one=pool.submit(()->{gate.await();try {execute(single);return true;}catch(InvalidFoodException conflict){return false;}});
            var two=pool.submit(()->{gate.await();try {execute(whole);return true;}catch(InvalidFoodException conflict){return false;}});
            gate.countDown();
            assertThat(one.get(10,TimeUnit.SECONDS)^two.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(4);
        assertThat(masters.masterId(a)).isIn(masters.masterId(b),masters.masterId(c));
    }
}
