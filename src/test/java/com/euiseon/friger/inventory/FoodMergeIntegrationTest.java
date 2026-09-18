package com.euiseon.friger.inventory;

import java.util.*;
import java.util.concurrent.*;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.service.*;
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
import org.flywaydb.core.Flyway;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/** Whole-food merges run through the per-item move flow; legacy MERGE receipts stay visible. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FoodMergeIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired FoodMasterService masters;
    @Autowired ItemMoveService moves;
    @Autowired FoodMasterDao dao;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired com.euiseon.friger.history.dao.HistoryDao histories;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");
        jdbc.update("DELETE FROM food_master");jdbc.update("DELETE FROM food_merge_receipt");
        jdbc.update("DELETE FROM food_item_move_receipt");
    }
    private long create(String name,String unit,String storage) throws Exception {
        mvc.perform(post("/inventory").param("registrationRequestId",java.util.UUID.randomUUID().toString()).param("foodName",name).param("quantityAmount","2")
                .param("quantityUnit",unit).param("storageType",storage))
                .andExpect(status().is3xxRedirection());
        return masters.masterId(inventory.findActive().getFirst().foodId());
    }
    private List<Long> activeItems(long master) {
        return masters.items(master).stream().filter(i->i.status()==FoodStatus.ACTIVE)
                .map(i->i.foodId()).sorted().toList();
    }
    private ItemMoveService.Command command(long source,long target,long sourceVersion,long targetVersion,List<Long> items) {
        return new ItemMoveService.Command(ItemMoveService.Mode.EXISTING,target,null,null,source,sourceVersion,targetVersion,items,true);
    }
    private ItemMoveService.MoveResult wholeMove(long source,long target,UUID token) {
        return moves.move(command(source,target,dao.find(source).versionNo(),dao.find(target).versionNo(),activeItems(source)),token);
    }
    @Test void sameNamesRemainSeparateUntilExplicitMerge() throws Exception {
        create("두부","모","FRIDGE");create("두부","모","FRIDGE");
        assertThat(masters.groups(null,false)).hasSize(2);
    }
    @Test void masterListOffersSelectionMoveEntryOnly() throws Exception {
        long source=create("두부","모","FRIDGE");
        long item=inventory.findActive().getFirst().foodId();
        mvc.perform(get("/foods/"+source)).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"moveSelectForm\"")))
                .andExpect(content().string(containsString("name=\"items\" value=\""+item+"\"")))
                .andExpect(content().string(containsString(">다른 음식으로 병합</button>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/foods/"+source+"/merge"))));
        mvc.perform(get("/foods/"+source+"/move")).andExpect(redirectedUrl("/foods/"+source));
        mvc.perform(get("/foods/"+source+"/move").param("items",""+item)).andExpect(status().isOk())
                .andExpect(content().string(containsString("이 경우 병합 시 기존 음식은 사라져.")))
                .andExpect(content().string(containsString("class=\"cancel-link purchase-back-link\" href=\"/foods/"+source)));
    }
    @Test void mergePreservesItemsHistoryAndTargetDefaults() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","g","ROOM");
        jdbc.update("UPDATE food_master SET default_quantity_unit='g',default_exclude_expiry_warning=true,category='기준 분류' WHERE master_id=?",target);
        var items=jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id");
        var histories=jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        wholeMove(source,target,UUID.randomUUID());
        assertThat(dao.find(source)).isNull();
        assertThat(masters.groups(null,false)).hasSize(1);
        assertThat(masters.items(target)).hasSize(2);
        var after=jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id");
        for(int i=0;i<items.size();i++) {
            items.get(i).remove("master_id");items.get(i).remove("updated_at");items.get(i).remove("version_no");items.get(i).remove("stock_revision");
            after.get(i).remove("master_id");after.get(i).remove("updated_at");after.get(i).remove("version_no");after.get(i).remove("stock_revision");
        }
        assertThat(after).isEqualTo(items);
        assertThat(jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id")).isEqualTo(histories);
        assertThat(jdbc.queryForObject("SELECT default_exclude_expiry_warning FROM food_master WHERE master_id=?",Boolean.class,target)).isTrue();
        assertThat(dao.find(target).category()).isEqualTo("기준 분류");
        mvc.perform(get("/foods/"+target)).andExpect(status().isOk()).andExpect(content().string(containsString("두부")));
        mvc.perform(get("/inventory")).andExpect(status().isOk()).andExpect(content().string(containsString("개별 구매 2건")));
        mvc.perform(get("/history")).andExpect(status().isOk()).andExpect(content().string(containsString("듀부")));
    }
    @Test void previewDoesNotWriteAndWholePostSupportsSafeRetry() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        long item=masters.items(source).getFirst().foodId();
        mvc.perform(get("/foods/"+source+"/move").param("items",""+item)).andExpect(status().isOk());
        assertThat(dao.find(source)).isNotNull();
        UUID token=UUID.randomUUID();
        for(int i=0;i<2;i++) mvc.perform(post("/foods/"+source+"/move").param("mode","EXISTING")
                .param("targetId",""+target).param("sourceId",""+source)
                .param("sourceVersion","0").param("targetVersion","0")
                .param("itemIds",""+item).param("whole","true").param("requestId",token.toString()))
                .andExpect(redirectedUrl("/foods/"+target));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void staleConfirmationAndSelfMergeAreRejected() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        var items=activeItems(source);
        assertThatThrownBy(()->moves.move(command(source,source,0,0,items),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        dao.touch(target);
        assertThatThrownBy(()->moves.move(command(source,target,0,0,items),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(dao.countItems(source)).isEqualTo(1);
    }
    @Test void moveChoicesEscapeNamesAndStaleWholePostIsRejected() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("<b>두부</b>","모","ROOM");
        long sameName=create("<b>두부</b>","모","FREEZER");
        long item=masters.items(source).getFirst().foodId();
        mvc.perform(get("/foods/"+source+"/move").param("items",""+item)).andExpect(status().isOk())
                .andExpect(content().string(containsString("확인했어, 병합하자!")))
                .andExpect(content().string(containsString("&lt;b&gt;두부&lt;/b&gt;")))
                .andExpect(content().string(containsString("data-value=\""+sameName+"\"")))
                .andExpect(content().string(containsString("data-value=\""+target+"\"")));
        assertThat(sameName).isNotEqualTo(target);
        dao.touch(target);
        mvc.perform(post("/foods/"+source+"/move").param("mode","EXISTING")
                .param("targetId",""+target).param("sourceId",""+source)
                .param("sourceVersion","0").param("targetVersion","0")
                .param("itemIds",""+item).param("whole","true").param("requestId",UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/foods/"+source+"/move?items="+item))
                .andExpect(flash().attributeExists("errorMessage"));
        assertThat(dao.countItems(source)).isEqualTo(1);
        assertThat(dao.countItems(target)).isEqualTo(1);
    }
    @Test void staleItemEditAfterMergeIsRejected() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        var before=masters.items(source).getFirst();
        wholeMove(source,target,UUID.randomUUID());
        assertThatThrownBy(()->inventory.update(before.foodId(),com.euiseon.friger.inventory.dto.FoodCreateForm.from(before),before.updatedAt()))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(inventory.findById(before.foodId()).foodName()).isEqualTo("두부");
    }
    @Test void receiptFailureRollsBackTransferAndDeletion() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        jdbc.execute("ALTER TABLE food_item_move_receipt ADD CONSTRAINT reject_test_receipt CHECK(source_name <> '듀부')");
        try {
            assertThatThrownBy(()->wholeMove(source,target,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(dao.countItems(source)).isEqualTo(1);assertThat(dao.find(target).versionNo()).isZero();
        } finally {jdbc.execute("ALTER TABLE food_item_move_receipt DROP CONSTRAINT reject_test_receipt");}
    }
    @Test void sharedNameEditInvalidatesOtherItemFormsAndMergePreview() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        var sourceItem=masters.items(source).getFirst();
        wholeMove(source,target,UUID.randomUUID());
        var before=inventory.findById(sourceItem.foodId());
        var sibling=masters.items(target).stream().filter(i->!i.foodId().equals(before.foodId())).findFirst().orElseThrow();
        mvc.perform(post("/inventory/"+before.foodId()+"/edit").param("foodName","우리집 두부")
                .param("quantityAmount","2").param("quantityUnit","모").param("storageType","FRIDGE")
                .param("expectedUpdatedAt",before.updatedAt().toString())).andExpect(status().is3xxRedirection());
        assertThat(inventory.findById(sibling.foodId()).foodName()).isEqualTo("우리집 두부");
        assertThatThrownBy(()->inventory.update(sibling.foodId(),com.euiseon.friger.inventory.dto.FoodCreateForm.from(sibling),sibling.updatedAt()))
                .isInstanceOf(InvalidFoodException.class);
        long another=create("또 다른 두부","모","FRIDGE");
        assertThatThrownBy(()->moves.move(command(another,target,0,1,activeItems(another)),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
    }
    @Test void simultaneousRetriesApplyOnlyOnce() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        var whole=command(source,target,0,0,activeItems(source));
        UUID token=UUID.randomUUID();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Long> request=()->{gate.await();return moves.move(whole,token).targetId();};
            var a=pool.submit(request);var b=pool.submit(request);gate.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(target);
            assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo(target);
        }
        assertThat(dao.countItems(target)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void v7PreservesExistingIdsAndDoesNotCombineMatchingNames() {
        String schema="master_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("6").load().migrate();
        jdbc.update("INSERT INTO master_upgrade.food_item(food_name,storage_type,quantity_text) VALUES('두부','FRIDGE','반 모'),('두부','ROOM','1팩')");
        jdbc.update("INSERT INTO master_upgrade.food_history(food_id,action_type,new_storage_type,changes_text) SELECT food_id,'CREATE',storage_type,'snapshot-v1:{\"음식명\":\"옛 이름\"}' FROM master_upgrade.food_item");
        var before=jdbc.queryForList("SELECT * FROM master_upgrade.food_item ORDER BY food_id");
        var history=jdbc.queryForList("SELECT * FROM master_upgrade.food_history ORDER BY history_id");
        history.forEach(row->assertThat(row.remove("memo")).isNull());
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        var after=jdbc.queryForList("SELECT i.*,m.food_name,m.category FROM master_upgrade.food_item i JOIN master_upgrade.food_master m ON m.master_id=i.master_id ORDER BY food_id");
        after.forEach(row->{assertThat(row.remove("user_id")).isEqualTo(1L);row.remove("master_id");assertThat(row.remove("version_no")).isEqualTo(0L);assertThat(row.remove("stock_revision")).isEqualTo(0L);});assertThat(after).isEqualTo(before);
        var afterHistory=jdbc.queryForList("SELECT * FROM master_upgrade.food_history ORDER BY history_id");
        afterHistory.forEach(row->assertThat(row.remove("recorded_food_name")).isEqualTo("옛 이름"));
        afterHistory.forEach(row->{assertThat(row.remove("user_id")).isEqualTo(1L);
            var added=new java.util.HashSet<>(row.keySet());added.removeAll(history.getFirst().keySet());
            added.forEach(key->assertThat(row.remove(key)).isNull());
        });
        assertThat(afterHistory).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM master_upgrade.food_master",Integer.class)).isEqualTo(2);
    }
    @Test void wholeMoveRecordsOneEntryPerItemIncludingEndedWithoutRewritingItemHistory() throws Exception {
        long source=create("치킨","개","FRIDGE"),target=create("두부","모","ROOM");
        long ended=create("치킨","개","FRIDGE");
        long endedItem=masters.items(ended).getFirst().foodId();
        jdbc.update("UPDATE food_item SET master_id=? WHERE food_id=?",source,endedItem);
        dao.delete(ended);
        jdbc.update("UPDATE food_item SET status='DEPLETED',quantity_amount=0,quantity_text='0개' WHERE food_id=?",endedItem);
        long item=activeItems(source).getFirst();
        var before=jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        UUID token=UUID.randomUUID();
        wholeMove(source,target,token);
        assertThat(jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id")).isEqualTo(before);
        var entries=histories.findRecent(100);
        // 3 CREATE entries plus one MOVE entry per item: the stored one and the ended one both count.
        assertThat(entries).hasSize(5);
        var movedIds=entries.stream().filter(e->e.isMerge()).map(e->e.foodId()).toList();
        assertThat(movedIds).containsExactlyInAnyOrder(item,endedItem);
        entries.stream().filter(e->e.isMerge()).forEach(entry->{
            assertThat(entry.actionType()).isNull();
            assertThat(entry.actionLabel()).isEqualTo("병합");
            assertThat(entry.displayFoodName()).isEqualTo("치킨");
            assertThat(entry.currentMasterId()).isEqualTo(target);
            assertThat(entry.homeSummary()).contains("치킨").contains("두부");
        });
        mvc.perform(get("/history")).andExpect(status().isOk())
                .andExpect(content().string(containsString("치킨")))
                .andExpect(content().string(containsString("두부 (#"+target+")")));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("치킨")));
    }
    @Test void previousMergeReceiptsBecomeVisibleWithoutBackfill() throws Exception {
        long target=create("두부","모","FRIDGE");
        jdbc.update("INSERT INTO food_merge_receipt(user_id,request_id,source_id,target_id,source_version,target_version,source_name,target_name,item_count) VALUES(1,?,99999,?,0,0,'옛 음식','당시 두부',7)",UUID.randomUUID(),target);
        var rows=histories.findRecent(100);var merge=rows.stream().filter(e->e.isMerge()).findFirst().orElseThrow();
        assertThat(merge.displayFoodName()).isEqualTo("옛 음식");
        assertThat(merge.changesText()).isEqualTo("음식명: 옛 음식 (#99999) → 당시 두부 (#"+target+")");
        assertThat(merge.actionLabel()).isEqualTo("병합");
        assertThat(merge.mergedItemCount()).isEqualTo(7);assertThat(merge.currentFoodName()).isEqualTo("두부");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history",Integer.class)).isEqualTo(1);
    }
    @Test void legacyMergeChainsThroughNewMovesToCurrentFood() throws Exception {
        long a=create("A","개","FRIDGE"),b=create("B","팩","ROOM"),c=create("C","모","FREEZER");
        jdbc.update("INSERT INTO food_merge_receipt(user_id,request_id,source_id,target_id,source_version,target_version,source_name,target_name,item_count) VALUES(1,?,99999,?,0,0,'옛 음식','A',1)",UUID.randomUUID(),a);
        wholeMove(a,b,UUID.randomUUID());wholeMove(b,c,UUID.randomUUID());
        var rows=histories.findRecent(100);
        var legacy=rows.stream().filter(e->e.isMerge() && e.foodId()==null).findFirst().orElseThrow();
        assertThat(legacy.currentMasterId()).isEqualTo(c);
        assertThat(rows.stream().filter(e->e.isMerge() && e.foodId()!=null).toList())
                .hasSize(3).allSatisfy(e->assertThat(e.currentMasterId()).isEqualTo(c));
        mvc.perform(get("/history")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/foods/"+b+"\""))))
                .andExpect(content().string(containsString("href=\"/foods/"+c+"\"")));
    }
    @Test void renamedItemsExplainCurrentNameWithoutInventingMerge() throws Exception {
        long master=create("옛 이름","개","FRIDGE");var item=masters.items(master).getFirst();
        inventory.update(item.foodId(),com.euiseon.friger.inventory.dto.FoodCreateForm.from(item).withIdentity("새 이름",null),item.updatedAt());
        var rows=histories.findRecent(100);assertThat(rows).noneMatch(e->e.isMerge());
        var original=rows.stream().filter(e->e.actionType()==com.euiseon.friger.common.type.FoodActionType.CREATE).findFirst().orElseThrow();
        assertThat(original.displayFoodName()).isEqualTo("옛 이름");assertThat(original.currentLocationNote()).contains("새 이름");
    }
    @Test void moveHistoryUsesCombinedLimitAndStableTieOrder() throws Exception {
        long a=create("A","개","FRIDGE"),b=create("B","개","FRIDGE");wholeMove(a,b,UUID.randomUUID());
        jdbc.update("UPDATE food_history SET created_at='2026-09-14T00:00:00Z'");
        jdbc.update("UPDATE food_item_move_receipt SET created_at='2026-09-14T00:00:00Z'");
        var first=histories.findRecent(2);assertThat(first).hasSize(2);
        assertThat(first.getFirst().isMerge()).isTrue();
        assertThat(first.getFirst().actionType()).isNull();
        assertThat(histories.findRecent(2)).isEqualTo(first);
        assertThat(histories.findRecent(0)).isEmpty();
    }
    @Test void moveHistoryEscapesNamesAndLegacyMergeWithoutTargetFallsBackToList() throws Exception {
        long a=create("<b>A</b>","개","FRIDGE"),b=create("<b>B</b>","개","ROOM");
        wholeMove(a,b,UUID.randomUUID());
        mvc.perform(get("/history")).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;b&gt;A&lt;/b&gt;")));
        jdbc.update("INSERT INTO food_merge_receipt(user_id,request_id,source_id,target_id,source_version,target_version,source_name,target_name,item_count) VALUES(1,?,99999,99998,0,0,'옛 음식','사라진 두부',7)",UUID.randomUUID());
        var legacy=histories.findRecent(100).stream().filter(e->e.isMerge()).findFirst().orElseThrow();
        assertThat(legacy.currentMasterId()).isNull();
        mvc.perform(get("/history")).andExpect(status().isOk())
                .andExpect(content().string(containsString("현재 음식은 전체 목록에서 확인해줘.")));
    }
}
