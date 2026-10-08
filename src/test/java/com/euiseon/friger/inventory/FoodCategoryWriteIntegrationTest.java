package com.euiseon.friger.inventory;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import com.euiseon.friger.common.type.StorageType;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/** No test transaction: assertions observe committed writes and real service rollback. */
@SpringBootTest
@Testcontainers
class FoodCategoryWriteIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("frizer_category_writes");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired InventoryService inventory;
    @Autowired FoodRegistrationService registrations;
    @Autowired FoodMasterDao masters;
    @Autowired FoodQuantityService quantities;
    @Autowired FoodSplitService splits;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.euiseon.friger.history.dao.HistoryDao history;

    @BeforeEach void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_category_test ON food_history");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_category_audit_test ON food_category_history");
        jdbc.update("DELETE FROM food_category_history");
        jdbc.update("DELETE FROM food_registration_receipt");
        jdbc.update("DELETE FROM food_quantity_receipt");
        jdbc.update("DELETE FROM food_split_receipt");
        jdbc.update("DELETE FROM food_history");
        jdbc.update("DELETE FROM food_item");
        jdbc.update("DELETE FROM food_master");
        jdbc.update("UPDATE food_category_major SET active=true");
        jdbc.update("UPDATE food_category_minor SET active=true");
    }
    private FoodCreateForm form() {
        return new FoodCreateForm("두부", StorageType.FRIDGE, "옛 분류", BigDecimal.ONE,
                null,null,null,null,null,null,false,null,null,null,null,"팩");
    }
    private long create(UUID token) {
        return registrations.createCategorized(form(),null,null,"soy","soy_tofu",token);
    }
    private long add(long master, long version, UUID token) {
        return registrations.createCategorized(form(),master,version,"soy","soy_tofu",token);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table,Integer.class); }

    @Test void newFoodStoresCodesAndRegistrationSnapshot() {
        long id=create(UUID.randomUUID());
        var master=masters.find(masters.masterIdForItem(id));
        assertThat(master.categoryMajorCode()).isEqualTo("soy");
        assertThat(master.categoryMinorCode()).isEqualTo("soy_tofu");
        assertThat(master.category()).isEqualTo("콩·두부 › 두부");
        assertThat(jdbc.queryForObject("SELECT after_snapshot->>'category_minor_code' FROM food_history WHERE food_id=?",String.class,id)).isEqualTo("soy_tofu");
        assertThat(count("food_category_history")).isZero();
    }
    @Test void requiredSelectionFailureLeavesNoReceiptOrFood() {
        assertThatThrownBy(() -> registrations.createCategorized(form(),null,null,null,null,UUID.randomUUID()))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(count("food_registration_receipt")).isZero();
        assertThat(count("food_master")).isZero();
    }
    @Test void addClassifiesGroupAndPreservesEarlierSnapshot() {
        long original=inventory.create(form()); long master=masters.masterIdForItem(original);
        var before=inventory.findById(original);
        String snapshot=jdbc.queryForObject("SELECT after_snapshot::text FROM food_history WHERE food_id=?",String.class,original);
        long added=add(master,0,UUID.randomUUID());
        assertThat(masters.find(master).versionNo()).isEqualTo(1);
        assertThat(inventory.findById(original).updatedAt()).isAfter(before.updatedAt());
        assertThat(inventory.findById(original).quantityAmount()).isEqualByComparingTo(before.quantityAmount());
        assertThat(inventory.findById(added).category()).isEqualTo("콩·두부 › 두부");
        assertThat(jdbc.queryForObject("SELECT after_snapshot::text FROM food_history WHERE food_id=?",String.class,original)).isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT before_category FROM food_category_history WHERE master_id=?",String.class,master)).isEqualTo("옛 분류");
        assertThat(masters.registrationChoices()).filteredOn(c -> c.masterId()==master)
                .singleElement().satisfies(c -> assertThat(c.categoryMinorCode()).isEqualTo("soy_tofu"));
        assertThatThrownBy(() -> inventory.updateCategorized(original,form(),before.updatedAt(),"kimchi",null))
                .isInstanceOf(InvalidFoodException.class);
    }
    @Test void emptyGroupCanBeClassifiedDuringAdd() {
        long master=jdbc.queryForObject("INSERT INTO food_master(user_id,food_name) VALUES (1,'빈 음식') RETURNING master_id",Long.class);
        long id=add(master,0,UUID.randomUUID());
        assertThat(inventory.findById(id).foodName()).isEqualTo("빈 음식");
        assertThat(masters.find(master).categoryMinorCode()).isEqualTo("soy_tofu");
        assertThat(count("food_category_history")).isEqualTo(1);
    }
    @Test void endedOnlyGroupCanBeClassifiedWithoutReopeningOldItem() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        quantities.apply(id,FoodQuantityService.Action.CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),null);
        add(master,masters.find(master).versionNo(),UUID.randomUUID());
        assertThat(inventory.findById(id).status().name()).isEqualTo("DEPLETED");
        assertThat(inventory.findById(id).quantityAmount()).isZero();
        assertThat(quantities.preview(id).cancellable()).isTrue();
    }
    @Test void categorizedAddInheritsRetiredChoiceAndIgnoresPostedCodes() {
        long id=create(UUID.randomUUID()); long master=masters.masterIdForItem(id);
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='soy'");
        jdbc.update("UPDATE food_category_minor SET active=false WHERE code='soy_tofu'");
        long added=registrations.createCategorized(form(),master,masters.find(master).versionNo(),"invalid","invalid",UUID.randomUUID());
        assertThat(inventory.findById(added).category()).isEqualTo("콩·두부 › 두부");
        assertThat(masters.find(master).categoryMinorCode()).isEqualTo("soy_tofu");
        assertThat(count("food_category_history")).isZero();
    }
    @Test void staleVersionCannotOverwriteClassification() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        add(master,0,UUID.randomUUID());
        assertThatThrownBy(() -> registrations.createCategorized(form(),master,0L,"kimchi",null,UUID.randomUUID()))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(count("food_item")).isEqualTo(2);
        assertThat(count("food_category_history")).isEqualTo(1);
        assertThat(count("food_registration_receipt")).isEqualTo(1);
    }
    @Test void invalidQuantityDoesNotClassifyGroup() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        assertThatThrownBy(() -> registrations.createCategorized(FoodCreateForm.empty(),master,0L,"soy","soy_tofu",UUID.randomUUID()))
                .isInstanceOf(InvalidFoodException.class);
        assertThat(masters.find(master).categoryMajorCode()).isNull();
        assertThat(masters.find(master).versionNo()).isZero();
        assertThat(count("food_registration_receipt")).isZero();
    }
    private void failHistory() {
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_category_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced history failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_category_test BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION fail_category_write()");
    }
    @Test void historyFailureRollsBackGroupItemsAuditAndReceipt() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        var before=inventory.findById(id); failHistory();
        assertThatThrownBy(() -> add(master,0,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(masters.find(master).categoryMajorCode()).isNull();
        assertThat(masters.find(master).versionNo()).isZero();
        assertThat(inventory.findById(id)).isEqualTo(before);
        assertThat(count("food_item")).isEqualTo(1);
        assertThat(count("food_category_history")).isZero();
        assertThat(count("food_registration_receipt")).isZero();
    }
    @Test void auditFailureAlsoRollsBackNewItem() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_category_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_category_audit_test BEFORE INSERT ON food_category_history FOR EACH ROW EXECUTE FUNCTION fail_category_write()");
        assertThatThrownBy(() -> add(master,0,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(masters.find(master).categoryMajorCode()).isNull();
        assertThat(count("food_item")).isEqualTo(1);
        assertThat(count("food_registration_receipt")).isZero();
    }
    @Test void identicalRetriesReturnOriginalItemAfterGroupVersionChanged() {
        long original=inventory.create(form()); long master=masters.masterIdForItem(original);
        UUID token=UUID.randomUUID(); long added=add(master,0,token);
        assertThat(add(master,0,token)).isEqualTo(added);
        assertThat(count("food_item")).isEqualTo(2);
        assertThat(count("food_category_history")).isEqualTo(1);
        assertThatThrownBy(() -> registrations.createCategorized(form(),master,0L,"kimchi",null,token))
                .isInstanceOf(InvalidFoodException.class);
    }
    @Test void oldReceiptFormatStillReplaysButCannotBeReusedForNewContract() {
        UUID token=UUID.randomUUID(); long id=registrations.create(form(),token);
        assertThat(registrations.create(form(),token)).isEqualTo(id);
        assertThatThrownBy(() -> create(token)).isInstanceOf(InvalidFoodException.class);
        assertThat(count("food_item")).isEqualTo(1);
    }
    @Test void editClassifiesWholeGroupAndRecordsBeforeAndAfterCodes() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        long other=inventory.create(form(),master,0L);
        var beforeOther=inventory.findById(other);
        assertThat(inventory.updateCategorized(id,form(),inventory.findById(id).updatedAt(),"soy","soy_tofu")).isTrue();
        assertThat(inventory.findById(other).category()).isEqualTo("콩·두부 › 두부");
        assertThat(inventory.findById(other).updatedAt()).isAfter(beforeOther.updatedAt());
        assertThat(inventory.updateCategorized(id,form(),inventory.findById(id).updatedAt(),"kimchi",null)).isTrue();
        assertThat(masters.find(master).categoryMinorCode()).isNull();
        assertThat(jdbc.queryForList("SELECT before_major_code FROM food_category_history WHERE master_id=? ORDER BY category_history_id",String.class,master))
                .containsExactly(null,"soy");
        assertThat(masters.find(master).versionNo()).isEqualTo(3);
    }
    @Test void unclassifiedEditRequiresSelectionButWarningStillWorks() {
        long id=inventory.create(form()); var before=inventory.findById(id);
        assertThatThrownBy(() -> inventory.updateCategorized(id,form(),before.updatedAt(),null,null)).isInstanceOf(InvalidFoodException.class);
        inventory.changeWarning(id,null,true,false,before.updatedAt());
        assertThat(inventory.findById(id).warningPausedForever()).isTrue();
        assertThat(masters.find(masters.masterIdForItem(id)).categoryMajorCode()).isNull();
    }
    @Test void existingRetiredChoiceCanBeKeptButNotNewlySelected() {
        long id=create(UUID.randomUUID());
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='soy'");
        assertThat(inventory.updateCategorized(id,FoodCreateForm.from(inventory.findById(id)),inventory.findById(id).updatedAt(),"soy","soy_tofu")).isFalse();
        long legacy=inventory.create(form());
        assertThatThrownBy(() -> inventory.updateCategorized(legacy,form(),inventory.findById(legacy).updatedAt(),"soy","soy_tofu"))
                .isInstanceOf(InvalidFoodException.class);
    }
    @Test void legacyEditCannotDesynchronizeLabelAndCodes() {
        long id=create(UUID.randomUUID());
        inventory.update(id,form(),inventory.findById(id).updatedAt());
        assertThat(inventory.findById(id).category()).isEqualTo("콩·두부 › 두부");
    }
    @Test void editFailureRollsBackBothItemAndGroupChanges() {
        long id=inventory.create(form()); var before=inventory.findById(id); failHistory();
        assertThatThrownBy(() -> inventory.updateCategorized(id,form(),before.updatedAt(),"kimchi",null))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(inventory.findById(id)).isEqualTo(before);
        assertThat(masters.find(masters.masterIdForItem(id)).categoryMajorCode()).isNull();
        assertThat(count("food_category_history")).isZero();
    }
    @Test void concurrentDifferentRequestsCannotBothClassifySameVersion() throws Exception {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt=() -> { ready.countDown(); if (!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                try { add(master,0,UUID.randomUUID()); return true; } catch (InvalidFoodException conflict) { return false; } };
            var first=pool.submit(attempt); var second=pool.submit(attempt);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(java.util.List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count("food_item")).isEqualTo(2);
        assertThat(count("food_category_history")).isEqualTo(1);
    }
    @Test void foreignGroupIsRejectedBeforeAnyCategoryWrite() {
        long user=jdbc.queryForObject("INSERT INTO app_user(role) VALUES ('USER') RETURNING user_id",Long.class);
        long master=jdbc.queryForObject("INSERT INTO food_master(user_id,food_name) VALUES (?,'다른 사용자') RETURNING master_id",Long.class,user);
        assertThatThrownBy(() -> add(master,0,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(count("food_item")).isZero();
        assertThat(count("food_category_history")).isZero();
        assertThat(count("food_registration_receipt")).isZero();
    }

    @Test void concurrentIdenticalRequestsCreateOnlyOneItemAndAudit() throws Exception {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id); UUID token=UUID.randomUUID();
        var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            Callable<Long> attempt=() -> { ready.countDown(); if (!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                return add(master,0,token); };
            var first=pool.submit(attempt); var second=pool.submit(attempt);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
        }
        assertThat(count("food_item")).isEqualTo(2);
        assertThat(count("food_category_history")).isEqualTo(1);
        assertThat(count("food_registration_receipt")).isEqualTo(1);
    }

    @Test void cancellationAfterClassificationPreservesNewCategoryAndOldSnapshot() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        long event=quantities.apply(id,FoodQuantityService.Action.DISCARD,quantities.preview(id).version(),null,UUID.randomUUID(),null);
        String snapshot=jdbc.queryForObject("SELECT after_snapshot::text FROM food_history WHERE history_id=?",String.class,event);
        add(master,masters.find(master).versionNo(),UUID.randomUUID());
        quantities.apply(id,FoodQuantityService.Action.CANCEL,quantities.preview(id).version(),event,UUID.randomUUID(),null);
        assertThat(masters.find(master).categoryMinorCode()).isEqualTo("soy_tofu");
        assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(jdbc.queryForObject("SELECT after_snapshot::text FROM food_history WHERE history_id=?",String.class,event)).isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT after_snapshot->>'category_minor_code' FROM food_history WHERE food_id=? AND action_type='CANCEL'",String.class,id)).isEqualTo("soy_tofu");
    }

    @Test void splitKeepsCategoryAndSnapshotsItsCodesWithoutNewCategoryAudit() {
        long id=create(UUID.randomUUID());
        long child=splits.split(id,new FoodSplitService.Command(new BigDecimal("0.5"),StorageType.FRIDGE,null,null,false),
                quantities.preview(id).version(),UUID.randomUUID());
        assertThat(masters.masterIdForItem(child)).isEqualTo(masters.masterIdForItem(id));
        assertThat(jdbc.queryForObject("SELECT after_snapshot->>'category_minor_code' FROM food_history WHERE food_id=? AND action_type='SPLIT_IN'",String.class,child)).isEqualTo("soy_tofu");
        assertThat(count("food_category_history")).isZero();
    }

    @Test void auditSurvivesRemovalOfAllGroupItemsAndGroup() {
        long id=inventory.create(form()); long master=masters.masterIdForItem(id);
        add(master,0,UUID.randomUUID());
        jdbc.update("DELETE FROM food_history");
        jdbc.update("DELETE FROM food_item");
        jdbc.update("DELETE FROM food_master");
        assertThat(jdbc.queryForObject("SELECT before_category FROM food_category_history WHERE user_id=1 AND master_id=?",String.class,master)).isEqualTo("옛 분류");
    }

    @Test void categoryHistoryDoesNotAppendScopeExplanationToMemo() {
        long id=inventory.create(form());
        var changed=new FoodCreateForm("두부",StorageType.FRIDGE,null,BigDecimal.ONE,
                null,null,null,null,null,null,false,"밀폐 용기로 옮김",null,null,null,"팩");
        inventory.updateCategorized(id,changed,inventory.findById(id).updatedAt(),"soy","soy_tofu");
        var entry=history.findRecent(10).stream().filter(h -> h.foodId()==id && h.actionType()==com.euiseon.friger.common.type.FoodActionType.UPDATE).findFirst().orElseThrow();
        assertThat(entry.detailFields()).filteredOn(f -> f.label().equals("메모")).singleElement()
                .satisfies(f -> assertThat(f.value()).isEqualTo("- → 밀폐 용기로 옮김"));
        assertThat(entry.changesText()).doesNotContain("분류 적용 범위");
    }

    @Test void equalLegacyLabelStillRecordsActualClassificationRatherThanGenericContent() {
        long id=inventory.create(form().withIdentity("두부","콩·두부 › 두부"));
        var before=inventory.findById(id);
        assertThat(inventory.updateCategorized(id,FoodCreateForm.from(before),before.updatedAt(),"soy","soy_tofu")).isTrue();
        var entry=history.findRecent(10).stream().filter(h -> h.foodId()==id && h.actionType()==com.euiseon.friger.common.type.FoodActionType.UPDATE).findFirst().orElseThrow();
        assertThat(entry.detailFields()).singleElement().satisfies(f -> {
            assertThat(f.label()).isEqualTo("분류");
            assertThat(f.value()).isEqualTo("콩·두부 › 두부 (직접 입력) → 콩·두부 › 두부");
        });
        assertThat(count("food_category_history")).isEqualTo(1);
    }
}
