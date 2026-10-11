package com.euiseon.friger.inventory;

import java.util.*;
import java.math.BigDecimal;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.history.dao.HistoryDao;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class FoodCategoryMergeIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired InventoryService inventory; @Autowired ItemMoveService moves;
    @Autowired FoodMasterDao masters; @Autowired ItemMoveDao moveDao;
    @Autowired HistoryDao history; @Autowired FoodDeleteService deletes;
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @BeforeEach void reset() {
        jdbc.execute("ALTER TABLE food_history DROP CONSTRAINT IF EXISTS fail_merge_test");
        for(String table:List.of("food_delete_receipt","food_registration_receipt","food_item_move_receipt","food_merge_receipt","food_history","food_item","food_master")) jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE food_category_major SET active=true");
    }
    long item(String name) {return inventory.create(new FoodCreateForm(name,StorageType.FRIDGE,null,BigDecimal.ONE,null,null,null,null,null,null,false,null,null,null,null,"개"));}
    long group(long item) {return masters.masterIdForItem(item);}
    ItemMoveService.Preview preview(long item,Long target,String major) {
        return moves.previewCategorized(group(item),List.of(item),target==null?ItemMoveService.Mode.NEW:ItemMoveService.Mode.EXISTING,target,"새 음식",major,null);
    }
    @Test void classifiesOnlyTargetAndStoresOneMergePerItemWithoutChangingOldHistory() {
        long source=item("원본"),remaining=item("남을 항목"),target=item("대상");
        long empty=group(remaining),original=group(source);
        jdbc.update("UPDATE food_item SET master_id=? WHERE food_id=?",original,remaining);masters.delete(empty);
        var old=jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        var targetOld=inventory.findById(target);
        var p=preview(source,group(target),"beverage");
        assertThat(masters.find(group(target)).categoryMajorCode()).isNull();
        moves.moveCategorized(p.command(),p.requestId(),"beverage",null);
        assertThat(masters.find(original).categoryMajorCode()).isNull();
        assertThat(masters.find(group(target)).categoryMajorCode()).isEqualTo("beverage");
        assertThat(inventory.findById(target).updatedAt()).isAfter(targetOld.updatedAt());
        assertThat(jdbc.queryForList("SELECT * FROM food_history WHERE action_type<>'MERGE' ORDER BY history_id")).isEqualTo(old);
        assertThat(history.findEntries(null,"").stream().filter(e->e.isMerge()).toList()).singleElement().satisfies(e->{
            assertThat(e.actionLabel()).isEqualTo("병합");assertThat(e.foodId()).isEqualTo(source);
            assertThat(e.detailFields()).anyMatch(field->field.label().equals("분류"));
        });
        assertThat(moveDao.historyCount(source)).isEqualTo(2);
        assertThat(masters.countHistory(group(target))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT category_after->>'major_code' FROM food_history WHERE action_type='MERGE'",String.class)).isEqualTo("beverage");
        assertThat(jdbc.queryForObject("SELECT before_snapshot->'target_category_before'->>'master_id' FROM food_history WHERE action_type='MERGE'",String.class)).isEqualTo(""+group(target));
        moves.moveCategorized(p.command(),p.requestId(),"beverage",null);
        assertThat(history.findEntries(null,"")).hasSize(4);
    }
    @Test void requiresNewCategoryButInheritsRetiredTargetAndRejectsStalePreview() {
        long source=item("원본"),target=item("대상");
        assertThatThrownBy(()->preview(source,null,null)).isInstanceOf(InvalidFoodException.class);
        masters.assignCategory(group(target),"kimchi",null,"김치");
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='kimchi'");
        var p=preview(source,group(target),"invalid");
        assertThat(p.targetMajorLabel()).isEqualTo("김치");
        assertThat(p.targetMinorLabel()).isEqualTo("중분류 없음");
        masters.touch(group(target));
        assertThatThrownBy(()->moves.moveCategorized(p.command(),p.requestId(),"invalid",null)).isInstanceOf(InvalidFoodException.class);
        var current=preview(source,group(target),null);
        moves.moveCategorized(current.command(),current.requestId(),null,null);
        assertThat(masters.find(group(source)).categoryMajorCode()).isEqualTo("kimchi");
    }
    @Test void historyFailureRollsBackTargetClassificationItemsAndReceipts() {
        long source=item("원본"),target=item("대상");
        var before=inventory.findById(source);var master=masters.find(group(target));
        var p=preview(source,group(target),"beverage");
        jdbc.execute("ALTER TABLE food_history ADD CONSTRAINT fail_merge_test CHECK(action_type<>'MERGE')");
        assertThatThrownBy(()->moves.moveCategorized(p.command(),p.requestId(),"beverage",null)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(inventory.findById(source)).isEqualTo(before);
        assertThat(masters.find(group(target))).isEqualTo(master);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isZero();
    }
    @Test void newGroupRetryAndDeletionKeepSingleActionContract() {
        long source=item("원본");var p=preview(source,null,"beverage");
        var result=moves.moveCategorized(p.command(),p.requestId(),"beverage",null);
        assertThat(masters.find(result.targetId()).categoryMajorCode()).isEqualTo("beverage");
        assertThat(moves.moveCategorized(p.command(),p.requestId(),"beverage",null)).isEqualTo(result);
        assertThatThrownBy(()->moves.moveCategorized(p.command(),p.requestId(),"alcohol",null)).isInstanceOf(InvalidFoodException.class);
        var selection=deletes.selection(result.targetId(),List.of(source));
        assertThat(selection.historyCount()).isEqualTo(2);
        deletes.delete(new FoodDeleteService.Command(result.targetId(),selection.source().versionNo(),List.of(source),List.of(selection.versions().get(source)),true),UUID.randomUUID());
        assertThat(history.findEntries(null,"")).isEmpty();
        assertThat(moves.moveCategorized(p.command(),p.requestId(),"beverage",null)).isEqualTo(result);
    }
    @Test void httpRequiresNewContractAndKeepsDraftOnValidationError() throws Exception {
        long source=item("원본"),id=group(source);item("미분류 대상");
        String html=mvc.perform(get("/foods/"+id+"/move").param("items",""+source)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("categoryPicker"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var folder=java.nio.file.Path.of("build/reports/category-merge-ui");java.nio.file.Files.createDirectories(folder);java.nio.file.Files.writeString(folder.resolve("merge.html"),html);
        assertThat(html).contains("data-result=\"targetMajorLabel\"","data-result=\"targetMinorLabel\"").doesNotContain("id=\"categoryConfirm\"");
        mvc.perform(get("/foods/"+id+"/move/preview").param("items",""+source).param("mode","NEW")
                .param("categoryMajorCode","soy").param("categoryMinorCode","soy_tofu"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetMajorLabel").value("콩·두부"))
                .andExpect(jsonPath("$.targetMinorLabel").value("두부"));
        mvc.perform(post("/foods/"+id+"/move").param("mode","NEW").param("sourceId",""+id).param("sourceVersion","0")
                .param("itemIds",""+source).param("newName","입력 보존").param("categoryContract","v1").param("requestId",UUID.randomUUID().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("moveDraft","errorMessage"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isZero();
    }
    @Test void concurrentCategorizedRetriesWriteOneHistory() throws Exception {
        long source=item("원본");var p=preview(source,null,"beverage");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<ItemMoveService.MoveResult> action=()->{gate.await();return moves.moveCategorized(p.command(),p.requestId(),"beverage",null);};
            var first=pool.submit(action);var second=pool.submit(action);gate.countDown();
            assertThat(first.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE action_type='MERGE'",Integer.class)).isEqualTo(1);
    }
    @Test void legacyReplayCannotCreateNewUnclassifiedMerge() {
        long source=item("원본");var p=moves.preview(group(source),List.of(source),ItemMoveService.Mode.NEW,null,"이전 음식",null);
        assertThatThrownBy(()->moves.replayLegacy(p.command(),p.requestId())).isInstanceOf(InvalidFoodException.class);
        var result=moves.move(p.command(),p.requestId());
        assertThat(moves.replayLegacy(p.command(),p.requestId())).isEqualTo(result);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt",Integer.class)).isEqualTo(1);
    }
}
