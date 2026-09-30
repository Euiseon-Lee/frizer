package com.euiseon.friger.inventory;

import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.entity.FoodItem;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Transactional
@Import(InventoryIntegrationTest.FixedTime.class)
class WarningSettingsIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired InventoryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    void executeSql(String sql,Object... args) { jdbc.update(sql,args);sqlSession.clearCache(); }
    @Autowired MockMvc mvc;
    static final LocalDate TODAY=LocalDate.of(2026,9,13);
    FoodCreateForm form(boolean paused, LocalDate until, boolean forever) {
        return new FoodCreateForm("알림 테스트",StorageType.FRIDGE,null,BigDecimal.ONE,TODAY.minusDays(1),
            null,null,null,null,FreezeType.NONE,false,null,null,null,null,"개",paused,until,forever);
    }
    void snapshot(String name,String html) throws Exception {
        if (System.getenv("FRIZER_UI_SNAPSHOTS") == null) return;
        var dir=java.nio.file.Path.of("build/reports/warning-ui");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve(name+".html"),html);
    }
    @Test void renderedMobileForms() throws Exception {
        long id=service.create(form(true,TODAY,false));
        for (String view:List.of("edit","detail")) {
            String url="/inventory/"+id+(view.equals("edit")?"/edit":"");
            var response=mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse();
            assertThat(response.getContentAsString()).contains("경고 알림 잠시 끄기");
            snapshot(view,response.getContentAsString());
        }
    }
    FoodCreateForm opening(OpeningStatus state, LocalDate date) {
        return new FoodCreateForm("개봉 테스트",StorageType.FRIDGE,null,BigDecimal.ONE,null,
            null,date,null,null,FreezeType.NONE,false,null,null,null,null,"개",false,null,false,state);
    }
    @Test void newDefaultsAndUncertainOpeningRoundTrip() throws Exception {
        assertThat(FoodCreateForm.empty().openingStatus()).isEqualTo(OpeningStatus.UNOPENED);
        long id=service.create(opening(OpeningStatus.OPENED,null));
        var item=service.findById(id);
        assertThat(item.openedAt()).isNull();assertThat(item.openingConfirmedAt()).isEqualTo(TODAY);
        assertThat(item.openingElapsed(TODAY.plusDays(5))).isEqualTo("개봉 후 최소 5일 경과");
        assertThat(item.openedOverdue(TODAY.plusMonths(3))).isFalse();
        assertThat(item.openedOverdue(TODAY.plusMonths(3).plusDays(1))).isTrue();
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("개봉일 불확실")));
        var edit=mvc.perform(get("/inventory/"+id+"/edit")).andExpect(status().isOk()).andReturn().getResponse();
        snapshot("opening-edit",edit.getContentAsString());
        mvc.perform(post("/inventory").param("registrationRequestId",java.util.UUID.randomUUID().toString())
            .param("foodName","개봉 폼").param("storageType","FRIDGE").param("quantityAmount","1").param("quantityUnit","개")
            .param("openingStatus","OPENED")).andExpect(status().is3xxRedirection());
        assertThat(service.findActive()).filteredOn(x->x.foodName().equals("개봉 폼")).allMatch(x->TODAY.equals(x.openingConfirmedAt()));
    }
    @Test void confirmationSurvivesEditsAndWarningChanges() {
        long id=service.create(opening(OpeningStatus.OPENED,null));
        executeSql("UPDATE food_item SET opening_confirmed_at=? WHERE food_id=?",TODAY.minusDays(20),id);
        var before=service.findById(id);
        service.update(id,opening(OpeningStatus.OPENED,null),before.updatedAt());
        var edited=service.findById(id);
        assertThat(edited.openingConfirmedAt()).isEqualTo(TODAY.minusDays(20));
        service.changeWarning(id,null,true,false,edited.updatedAt());
        assertThat(service.findById(id).openingConfirmedAt()).isEqualTo(TODAY.minusDays(20));
    }
    @Test void exactDateAndStateTransitionsRemainConsistent() {
        long id=service.create(opening(OpeningStatus.OPENED,TODAY.minusDays(5)));
        var before=service.findById(id);
        assertThat(before.openedAt()).isEqualTo(TODAY.minusDays(5));
        service.update(id,opening(OpeningStatus.UNKNOWN,TODAY.minusDays(5)),before.updatedAt());
        var unknown=service.findById(id);
        assertThat(unknown.openedAt()).isNull();assertThat(unknown.openingConfirmedAt()).isNull();
        assertThat(unknown.openingLabel()).isEqualTo("개봉 여부 불확실");
        service.update(id,opening(OpeningStatus.UNOPENED,null),unknown.updatedAt());
        assertThat(service.findById(id).openingLabel()).isEqualTo("미개봉");
        assertThatThrownBy(()->service.create(opening(OpeningStatus.OPENED,TODAY.plusDays(1)))).isInstanceOf(InvalidFoodException.class);
    }
    @Test void pauseIsInclusiveAndPreservesRecordedDeadline() throws Exception {
        long id=service.create(form(true,TODAY,false));
        var food=service.findById(id);
        assertThat(food.warningPausedUntil()).isEqualTo(TODAY);
        assertThat(food.expiredAt()).isEqualTo(TODAY.minusDays(1));
        assertThat(food.needsReview(TODAY)).isFalse();
        assertThat(food.needsReview(TODAY.plusDays(1))).isTrue();
        var home=mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getModelAndView();
        assertThat((List<?>)home.getModel().get("overviewFoods")).isEmpty();
        var list=mvc.perform(get("/inventory").param("warning","true")).andExpect(status().isOk()).andReturn().getModelAndView();
        assertThat((List<?>)list.getModel().get("foods")).isEmpty();
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("2026-09-12")))
            .andExpect(content().string(containsString("2026-09-13까지 경고 알림 꺼짐")));
    }
    @Test void foreverAndResumeRoundTripThroughDetailEndpoint() throws Exception {
        long id=service.create(form(false,null,false));var before=service.findById(id);
        mvc.perform(post("/inventory/"+id+"/warning").param("warningForever","true")
            .param("expectedUpdatedAt",before.updatedAt().toString())).andExpect(status().is3xxRedirection());
        var paused=service.findById(id);assertThat(paused.warningPausedForever()).isTrue();
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("영구 적용")));
        mvc.perform(post("/inventory/"+id+"/warning").param("resume","true")
            .param("expectedUpdatedAt",paused.updatedAt().toString())).andExpect(status().is3xxRedirection());
        assertThat(service.findById(id).warningPausedUntil()).isNull();
        assertThat(service.findById(id).needsReview(TODAY)).isTrue();
    }
    @Test void formCheckboxesBindAndPersist() throws Exception {
        mvc.perform(post("/inventory").param("registrationRequestId",java.util.UUID.randomUUID().toString())
            .param("foodName","폼 바인딩").param("storageType","FRIDGE").param("quantityAmount","1").param("quantityUnit","개")
            .param("warningPaused","true").param("warningForever","true"))
            .andExpect(status().is3xxRedirection());
        assertThat(service.findActive()).anyMatch(FoodItem::warningPausedForever);
        mvc.perform(get("/inventory/new")).andExpect(status().isOk()).andExpect(content().string(containsString("경고 알림 잠시 끄기")));
    }
    @ParameterizedTest @ValueSource(strings={"expired_at=expired_at+1","storage_type='ROOM'","storage_type='FREEZER',freeze_type='HOME_FROZEN',frozen_at=DATE '2026-09-13'"})
    void everyStorageWritePathClearsPause(String set) {
        long id=service.create(form(true,null,true));
        executeSql("UPDATE food_item SET "+set+" WHERE food_id=?",id);
        assertThat(service.findById(id).warningPausedUntil()).isNull();
    }
    @Test void editingDeadlineClearsPauseEvenIfSubmittedAgain() {
        long id=service.create(form(true,null,true));var item=service.findById(id);var f=form(true,null,true);
        service.update(id,new FoodCreateForm(f.foodName(),f.storageType(),f.category(),f.quantityAmount(),TODAY,
            f.purchasedAt(),f.openedAt(),f.frozenAt(),f.sourceType(),f.freezeType(),f.freezeToday(),f.memo(),
            f.capacityText(),f.sourceMemo(),f.sellByAt(),f.quantityUnit(),true,null,true),item.updatedAt());
        assertThat(service.findById(id).warningPausedUntil()).isNull();
    }
    @Test void unrelatedEditPreservesPauseAndStaleSettingsAreRejected() {
        long id=service.create(form(true,null,true));var item=service.findById(id);
        executeSql("UPDATE food_item SET memo='메모 변경' WHERE food_id=?",id);
        assertThat(service.findById(id).warningPausedForever()).isTrue();
        assertThatThrownBy(()->service.changeWarning(id,null,false,true,item.updatedAt())).isInstanceOf(InvalidFoodException.class);
        assertThat(service.findById(id).warningPausedForever()).isTrue();
    }
    @Test void warningSettingPreservesLegacyQuantity() {
        long id=service.create(form(false,null,false));
        executeSql("UPDATE food_item SET quantity_amount=NULL,quantity_unit=NULL,quantity_text='반 모' WHERE food_id=?",id);
        var before=service.findById(id);
        service.changeWarning(id,null,true,false,before.updatedAt());
        var after=service.findById(id);
        assertThat(after.warningPausedForever()).isTrue();
        assertThat(after.quantityAmount()).isNull();
        assertThat(after.quantityText()).isEqualTo("반 모");
    }
    @Test void invalidMissingAndPastDatesDoNotSave() {
        assertThatThrownBy(()->service.create(form(true,null,false))).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->service.create(form(true,TODAY.minusDays(1),false))).isInstanceOf(InvalidFoodException.class);
        assertThat(service.findActive()).isEmpty();
    }
    @Test void expiredSettingDoesNotPreventUnrelatedFormEdit() throws Exception {
        long id=service.create(form(true,TODAY,false));executeSql("UPDATE food_item SET warning_paused_until=? WHERE food_id=?",TODAY.minusDays(1),id);
        var model=mvc.perform(get("/inventory/"+id+"/edit")).andExpect(status().isOk()).andReturn().getModelAndView();
        assertThat(((FoodCreateForm)model.getModel().get("foodForm")).warningPaused()).isFalse();
    }
    @Test void frozenBeforeDeadlineNoticeSurvivesPauseButLateUnknownAndCommercialDoNotSoftenWarning() throws Exception {
        long id=service.create(form(false,null,false));
        executeSql("UPDATE food_item SET storage_type='FREEZER',freeze_type='HOME_FROZEN',frozen_at=? WHERE food_id=?",TODAY.minusDays(2),id);
        var frozen=service.findById(id);assertThat(frozen.frozenBeforeUseBy()).isTrue();assertThat(frozen.needsReview(TODAY)).isFalse();
        service.changeWarning(id,null,true,false,frozen.updatedAt());
        mvc.perform(get("/inventory/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("냉동 보관 2일째")));
        executeSql("UPDATE food_item SET frozen_at=? WHERE food_id=?",TODAY,id);
        assertThat(service.findById(id).useByOverdue(TODAY)).isTrue();
        executeSql("UPDATE food_item SET frozen_at=NULL WHERE food_id=?",id);
        assertThat(service.findById(id).useByOverdue(TODAY)).isTrue();
        executeSql("UPDATE food_item SET freeze_type='COMMERCIAL_FROZEN',frozen_at=? WHERE food_id=?",TODAY.minusDays(2),id);
        assertThat(service.findById(id).useByOverdue(TODAY)).isTrue();
        executeSql("UPDATE food_item SET expired_at=NULL,sell_by_at=?,freeze_type='HOME_FROZEN' WHERE food_id=?",TODAY.minusDays(1),id);
        assertThat(service.findById(id).frozenBeforeUseBy()).isFalse();assertThat(service.findById(id).sellByOverdue(TODAY)).isTrue();
    }
}
