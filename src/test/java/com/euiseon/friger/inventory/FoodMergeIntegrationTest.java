package com.euiseon.friger.inventory;

import java.util.*;
import java.util.concurrent.*;
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
    @Autowired FoodMasterDao dao;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");
        jdbc.update("DELETE FROM food_master");jdbc.update("DELETE FROM food_merge_receipt");
    }
    private long create(String name,String unit,String storage) throws Exception {
        mvc.perform(post("/inventory").param("foodName",name).param("quantityAmount","2")
                .param("quantityUnit",unit).param("storageType",storage))
                .andExpect(status().is3xxRedirection());
        return masters.masterId(inventory.findActive().getFirst().foodId());
    }
    @Test void sameNamesRemainSeparateUntilExplicitMerge() throws Exception {
        create("두부","모","FRIDGE");create("두부","모","FRIDGE");
        assertThat(masters.groups(null)).hasSize(2);
    }
    @Test void mergeIsDisabledUntilAnotherFoodExists() throws Exception {
        long source=create("두부","모","FRIDGE");
        mvc.perform(get("/foods/"+source)).andExpect(status().isOk())
                .andExpect(model().attribute("canMerge",false))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/foods/"+source+"/merge"))));
        mvc.perform(get("/foods/"+source+"/merge")).andExpect(redirectedUrl("/foods/"+source));
        create("듀부","모","FRIDGE");
        mvc.perform(get("/foods/"+source)).andExpect(status().isOk())
                .andExpect(model().attribute("canMerge",true))
                .andExpect(content().string(containsString("/foods/"+source+"/merge")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("disabled"))));
        mvc.perform(get("/foods/"+source+"/merge")).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"merge-text-button merge-back\" href=\"/foods/"+source+"\">돌아가기</a>")));
    }
    @Test void mergePreservesItemsHistoryAndTargetDefaults() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","g","ROOM");
        jdbc.update("UPDATE food_master SET default_quantity_unit='g',default_exclude_expiry_warning=true,category='기준 분류' WHERE master_id=?",target);
        var items=jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id");
        var histories=jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id");
        var preview=masters.preview(source,target);
        masters.merge(source,target,preview.source().versionNo(),preview.target().versionNo(),UUID.randomUUID());
        assertThat(dao.find(source)).isNull();
        assertThat(masters.groups(null)).hasSize(1);
        assertThat(masters.items(target)).hasSize(2);
        var after=jdbc.queryForList("SELECT * FROM food_item ORDER BY food_id");
        for(int i=0;i<items.size();i++) {
            items.get(i).remove("master_id");items.get(i).remove("updated_at");
            after.get(i).remove("master_id");after.get(i).remove("updated_at");
        }
        assertThat(after).isEqualTo(items);
        assertThat(jdbc.queryForList("SELECT * FROM food_history ORDER BY history_id")).isEqualTo(histories);
        assertThat(jdbc.queryForObject("SELECT default_exclude_expiry_warning FROM food_master WHERE master_id=?",Boolean.class,target)).isTrue();
        assertThat(dao.find(target).category()).isEqualTo("기준 분류");
        mvc.perform(get("/foods/"+target)).andExpect(status().isOk()).andExpect(content().string(containsString("두부")));
        mvc.perform(get("/inventory")).andExpect(status().isOk()).andExpect(content().string(containsString("개별 구매 2건")));
        mvc.perform(get("/history")).andExpect(status().isOk()).andExpect(content().string(containsString("듀부")));
    }
    @Test void previewDoesNotWriteAndPostSupportsSafeRetry() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        mvc.perform(get("/foods/"+source+"/merge")).andExpect(status().isOk());
        mvc.perform(get("/foods/"+source+"/merge").param("targetId",""+target))
                .andExpect(status().isOk()).andExpect(content().string(containsString("이동할 개별 구매 총 건수</dt><dd>1건")));
        assertThat(dao.find(source)).isNotNull();
        UUID token=UUID.randomUUID();
        for(int i=0;i<2;i++) mvc.perform(post("/foods/"+source+"/merge").param("targetId",""+target)
                .param("sourceVersion","0").param("targetVersion","0").param("requestId",token.toString()))
                .andExpect(redirectedUrl("/inventory"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_merge_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void staleConfirmationAndSelfMergeAreRejected() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        assertThatThrownBy(()->masters.merge(source,source,0,0,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        dao.touch(target);
        assertThatThrownBy(()->masters.merge(source,target,0,0,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(dao.countItems(source)).isEqualTo(1);
    }
    @Test void inlinePreviewKeepsChoicesEscapesNamesAndRejectsStalePost() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("<b>두부</b>","모","ROOM");
        long sameName=create("<b>두부</b>","모","FREEZER");
        mvc.perform(get("/foods/"+source+"/merge"))
                .andExpect(content().string(containsString("옮길 음식")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"mergeSubmit\""))));
        mvc.perform(get("/foods/"+source+"/merge").param("targetId",""+target))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"targetId\"")))
                .andExpect(content().string(containsString("(#"+sameName+")")))
                .andExpect(content().string(containsString("&lt;b&gt;두부&lt;/b&gt;")))
                .andExpect(content().string(containsString("같이 이동할 이력 총 건수</dt><dd>1건")))
                .andExpect(content().string(containsString("확인했어, 합치자!")));
        dao.touch(target);
        mvc.perform(post("/foods/"+source+"/merge").param("targetId",""+target)
                .param("sourceVersion","0").param("targetVersion","0").param("requestId",UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/inventory"))
                .andExpect(flash().attributeExists("successMessage"));
        assertThat(dao.countItems(source)).isEqualTo(1);
        assertThat(dao.countItems(target)).isEqualTo(1);
    }
    @Test void staleItemEditAfterMergeIsRejected() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        var before=masters.items(source).getFirst();
        masters.merge(source,target,0,0,UUID.randomUUID());
        assertThatThrownBy(()->inventory.update(before.foodId(),com.euiseon.friger.inventory.dto.FoodCreateForm.from(before),before.updatedAt()))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(inventory.findById(before.foodId()).foodName()).isEqualTo("두부");
    }
    @Test void receiptFailureRollsBackTransferAndDeletion() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        jdbc.execute("ALTER TABLE food_merge_receipt ADD CONSTRAINT reject_test_receipt CHECK(item_count<0)");
        try {
            assertThatThrownBy(()->masters.merge(source,target,0,0,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(dao.countItems(source)).isEqualTo(1);assertThat(dao.find(target).versionNo()).isZero();
        } finally {jdbc.execute("ALTER TABLE food_merge_receipt DROP CONSTRAINT reject_test_receipt");}
    }
    @Test void sharedNameEditInvalidatesOtherItemFormsAndMergePreview() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        var sourceItem=masters.items(source).getFirst();
        masters.merge(source,target,0,0,UUID.randomUUID());
        var before=inventory.findById(sourceItem.foodId());
        var sibling=masters.items(target).stream().filter(i->!i.foodId().equals(before.foodId())).findFirst().orElseThrow();
        mvc.perform(post("/inventory/"+before.foodId()+"/edit").param("foodName","우리집 두부")
                .param("quantityAmount","2").param("quantityUnit","모").param("storageType","FRIDGE")
                .param("expectedUpdatedAt",before.updatedAt().toString())).andExpect(status().is3xxRedirection());
        assertThat(inventory.findById(sibling.foodId()).foodName()).isEqualTo("우리집 두부");
        assertThatThrownBy(()->inventory.update(sibling.foodId(),com.euiseon.friger.inventory.dto.FoodCreateForm.from(sibling),sibling.updatedAt()))
                .isInstanceOf(InvalidFoodException.class);
        long another=create("또 다른 두부","모","FRIDGE");
        assertThatThrownBy(()->masters.merge(another,target,0,1,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
    }
    @Test void simultaneousRetriesApplyOnlyOnce() throws Exception {
        long source=create("듀부","모","FRIDGE"),target=create("두부","모","FRIDGE");
        UUID token=UUID.randomUUID();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Long> request=()->{gate.await();return masters.merge(source,target,0,0,token);};
            var a=pool.submit(request);var b=pool.submit(request);gate.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(target);
            assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo(target);
        }
        assertThat(dao.countItems(target)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_merge_receipt",Integer.class)).isEqualTo(1);
    }
    @Test void v7PreservesExistingIdsAndDoesNotCombineMatchingNames() {
        String schema="master_upgrade";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("6").load().migrate();
        jdbc.update("INSERT INTO master_upgrade.food_item(food_name,storage_type,quantity_text) VALUES('두부','FRIDGE','반 모'),('두부','ROOM','1팩')");
        jdbc.update("INSERT INTO master_upgrade.food_history(food_id,action_type,new_storage_type,changes_text) SELECT food_id,'CREATE',storage_type,'snapshot-v1:{\"음식명\":\"옛 이름\"}' FROM master_upgrade.food_item");
        var before=jdbc.queryForList("SELECT * FROM master_upgrade.food_item ORDER BY food_id");
        var history=jdbc.queryForList("SELECT * FROM master_upgrade.food_history ORDER BY history_id");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load().migrate();
        var after=jdbc.queryForList("SELECT i.*,m.food_name,m.category FROM master_upgrade.food_item i JOIN master_upgrade.food_master m ON m.master_id=i.master_id ORDER BY food_id");
        after.forEach(row->row.remove("master_id"));assertThat(after).isEqualTo(before);
        var afterHistory=jdbc.queryForList("SELECT * FROM master_upgrade.food_history ORDER BY history_id");
        afterHistory.forEach(row->assertThat(row.remove("recorded_food_name")).isEqualTo("옛 이름"));
        assertThat(afterHistory).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM master_upgrade.food_master",Integer.class)).isEqualTo(2);
    }
}
