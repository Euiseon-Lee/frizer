package com.euiseon.friger.inventory;

import java.util.*;
import java.util.concurrent.*;
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
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11");
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
    ItemMoveService.Preview existing(long item,long targetItem) {
        return moves.preview(item,ItemMoveService.Mode.EXISTING,masters.masterId(targetItem),null,null);
    }
    void execute(ItemMoveService.Preview p) {moves.move(p.item().foodId(),p.command(),p.requestId());}
    @Test void movesOnlySelectedItemAndPreservesAllOtherData() throws Exception {
        long a=create("듀부"), b=create("듀부"), target=create("두부"),source=masters.masterId(a);
        long empty=masters.masterId(b);
        jdbc.update("UPDATE food_item SET master_id=? WHERE food_id=?",source,b);dao.delete(empty);
        var before=jdbc.queryForMap("SELECT * FROM food_item WHERE food_id=?",a);
        var originalHistory=jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        var p=existing(a,target);
        assertThat(p.historyCount()).isEqualTo(1);assertThat(p.remainingCount()).isEqualTo(1);
        execute(p);
        var after=jdbc.queryForMap("SELECT * FROM food_item WHERE food_id=?",a);
        before.remove("master_id");before.remove("updated_at");before.remove("version_no");before.remove("stock_revision");after.remove("master_id");after.remove("updated_at");after.remove("version_no");after.remove("stock_revision");
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id")).isEqualTo(originalHistory);
        assertThat(masters.masterId(b)).isEqualTo(source);
        assertThat(masters.masterId(a)).isEqualTo(masters.masterId(target));
        assertThat(history.findRecent(10).stream().filter(e->e.actionType()==com.euiseon.friger.common.type.FoodActionType.MOVE).findFirst().orElseThrow().foodId()).isEqualTo(a);
    }
    @Test void splitCreatesNewIdentityEvenForSameNameAndIsIdempotent() throws Exception {
        long a=create("두부"),source=masters.masterId(a);
        var p=moves.preview(a,ItemMoveService.Mode.NEW,null,"두부","새 분류");execute(p);execute(p);
        assertThat(masters.masterId(a)).isNotEqualTo(source);assertThat(dao.find(source)).isNull();
        assertThat(inventory.findById(a).category()).isEqualTo("새 분류");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void completedTokenCannotBeReusedForDifferentInput() throws Exception {
        long a=create("두부");var p=moves.preview(a,ItemMoveService.Mode.NEW,null,"두부2",null);execute(p);
        var c=p.command();
        assertThatThrownBy(()->moves.move(a,new ItemMoveService.Command(c.mode(),null,"다른 이름",null,c.sourceId(),c.sourceVersion(),null),p.requestId())).isInstanceOf(InvalidFoodException.class);
        assertThat(inventory.findById(a).foodName()).isEqualTo("두부2");
    }
    @Test void staleSourceAndTargetRejectWithoutMoving() throws Exception {
        long a=create("두부"),b=create("콩");var p=existing(a,b);dao.touch(p.source().masterId());
        var staleSource=p;assertThatThrownBy(()->execute(staleSource)).isInstanceOf(InvalidFoodException.class);
        p=existing(a,b);dao.touch(p.command().targetId());var stale=p;
        assertThatThrownBy(()->execute(stale)).isInstanceOf(InvalidFoodException.class);
        assertThat(masters.masterId(a)).isEqualTo(p.source().masterId());
    }
    @Test void inactiveRemainingItemPreventsDeletingSource() throws Exception {
        long a=create("두부"),b=create("두부"),t=create("콩"),source=masters.masterId(a),old=masters.masterId(b);
        jdbc.update("UPDATE food_item SET master_id=?,status='DEPLETED',quantity_amount=0,quantity_unit=COALESCE(quantity_unit,'개'),quantity_text='0개' WHERE food_id=?",source,b);dao.delete(old);
        execute(existing(a,t));assertThat(dao.find(source)).isNotNull();
        assertThatThrownBy(()->existing(b,t)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void wholeMoveHistoryFollowsVacatedGroupAndItemHistoryFollowsItem() throws Exception {
        long a=create("A"),b=create("B"),c=create("C");
        var whole=masters.preview(masters.masterId(a),masters.masterId(b));
        masters.merge(whole.source().masterId(),whole.target().masterId(),whole.source().versionNo(),whole.target().versionNo(),UUID.randomUUID());
        execute(existing(a,c));execute(existing(b,c));
        var events=history.findRecent(20);
        assertThat(events.stream().filter(e->e.isMerge()).toList()).hasSize(1).allSatisfy(e->assertThat(e.currentMasterId()).isEqualTo(masters.masterId(c)));
        assertThat(events.stream().filter(e->Objects.equals(e.foodId(),a)).toList()).allSatisfy(e->assertThat(e.currentMasterId()).isEqualTo(masters.masterId(c)));
        assertThat(moves.preview(a,ItemMoveService.Mode.NEW,null,"A",null).historyCount()).isEqualTo(2);
        assertThat(dao.countHistory(masters.masterId(c))).isEqualTo(5);
    }
    @Test void previewAndSearchDoNotWriteAndPostRedirectsToItem() throws Exception {
        long a=create("듀부"),b=create("두부");
        mvc.perform(get("/inventory/"+a+"/move")).andExpect(status().isOk()).andExpect(content().string(containsString("새로운 음식으로 변경")));
        mvc.perform(get("/inventory/"+a+"/move/choices").param("q","두부")).andExpect(status().isOk()).andExpect(jsonPath("$[0].masterId").value(masters.masterId(b)));
        mvc.perform(get("/inventory/"+a+"/move/preview").param("mode","EXISTING").param("targetId",""+masters.masterId(b))).andExpect(status().isOk()).andExpect(jsonPath("$.remainingCount").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isZero();
        var p=existing(a,b);var c=p.command();
        mvc.perform(post("/inventory/"+a+"/move").param("mode","EXISTING").param("targetId",""+c.targetId()).param("sourceId",""+c.sourceId()).param("sourceVersion",""+c.sourceVersion()).param("targetVersion",""+c.targetVersion()).param("requestId",p.requestId().toString())).andExpect(redirectedUrl("/inventory/"+a));
        mvc.perform(get("/history")).andExpect(status().isOk()).andExpect(content().string(containsString("듀부")));
    }
    @Test void validationRejectsSameTargetBlankNameAndTooLongCategory() throws Exception {
        long a=create("두부");
        assertThatThrownBy(()->existing(a,a)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->moves.preview(a,ItemMoveService.Mode.NEW,null," ",null)).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->moves.preview(a,ItemMoveService.Mode.NEW,null,"두부","가".repeat(51))).isInstanceOf(InvalidFoodException.class);
    }
    @Test void parallelSubmissionCreatesOneSplit() throws Exception {
        long a=create("두부");var p=moves.preview(a,ItemMoveService.Mode.NEW,null,"콩",null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Long> run=()->{gate.await();return moves.move(a,p.command(),p.requestId());};
            var one=pool.submit(run);var two=pool.submit(run);gate.countDown();
            assertThat(one.get(10,TimeUnit.SECONDS)).isEqualTo(a);assertThat(two.get(10,TimeUnit.SECONDS)).isEqualTo(a);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void sourceChangeRejectsSplitWithoutCreatingMaster() throws Exception {
        long a=create("두부");var p=moves.preview(a,ItemMoveService.Mode.NEW,null,"콩",null);dao.touch(p.source().masterId());
        assertThatThrownBy(()->execute(p)).isInstanceOf(InvalidFoodException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
    }
    @Test void receiptFailureRollsBackNewMasterAndItemTransfer() throws Exception {
        long a=create("두부"),source=masters.masterId(a);
        var p=moves.preview(a,ItemMoveService.Mode.NEW,null,"실패 검증",null);
        jdbc.execute("ALTER TABLE food_item_move_receipt ADD CONSTRAINT test_receipt_failure CHECK(target_name <> '실패 검증')");
        try {
            assertThatThrownBy(()->execute(p)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(masters.masterId(a)).isEqualTo(source);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_master",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isZero();
        } finally {jdbc.execute("ALTER TABLE food_item_move_receipt DROP CONSTRAINT test_receipt_failure");}
    }
    @Test void itemMoveAndWholeMoveCompeteWithoutDeadlockOrLostItems() throws Exception {
        long a=create("두부"),b=create("콩"),c=create("채소");
        var single=existing(a,b);var whole=masters.preview(masters.masterId(a),masters.masterId(c));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var one=pool.submit(()->{gate.await();try {execute(single);return true;}catch(InvalidFoodException conflict){return false;}});
            var two=pool.submit(()->{gate.await();try {masters.merge(whole.source().masterId(),whole.target().masterId(),whole.source().versionNo(),whole.target().versionNo(),UUID.randomUUID());return true;}catch(InvalidFoodException conflict){return false;}});
            gate.countDown();
            assertThat(one.get(10,TimeUnit.SECONDS)^two.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(3);
        assertThat(masters.masterId(a)).isIn(masters.masterId(b),masters.masterId(c));
    }
}
