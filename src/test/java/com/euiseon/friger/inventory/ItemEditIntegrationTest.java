package com.euiseon.friger.inventory;
import java.math.BigDecimal;
import java.util.*;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ItemEditIntegrationTest {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
  r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
 }
 @Autowired InventoryService inventory; @Autowired ItemEditService edits;
 @Autowired FoodMasterService masters; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
 @BeforeEach void clean() {
  jdbc.update("DELETE FROM food_item_move_receipt");jdbc.update("DELETE FROM food_split_receipt");jdbc.update("DELETE FROM food_quantity_receipt");
  jdbc.update("DELETE FROM food_history WHERE reversal_of_history_id IS NOT NULL");jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");jdbc.update("DELETE FROM food_master");
 }
 FoodCreateForm form(String amount,String unit,String memo) {
  return new FoodCreateForm("콜라",StorageType.ROOM,null,new BigDecimal(amount),null,null,null,null,FoodSourceType.PURCHASE,FreezeType.NONE,false,memo,null,null,null,unit);
 }
 long create() {return inventory.createCategorized(form("20","개","원래"),null,null,"kimchi",null);}
 long save(long id,FoodCreateForm f,String mode,Long target) {
  return edits.save(id,f,inventory.findById(id).updatedAt(),mode,target!=null,target,target==null?null:masters.find(target).versionNo(),"kimchi",null);
 }
 @Test void wholeEditKeepsItemAndCorrectsQuantity() {
  long id=create();assertThat(save(id,form("18","개","수정"),"all",null)).isEqualTo(id);
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("18");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(1);
 }
 @Test void partialEditKeepsRemainderAndDates() {
  long id=create();jdbc.update("UPDATE food_item SET opening_status='OPENED',opened_at='2026-09-01',opening_confirmed_at='2026-09-01' WHERE food_id=?",id);
  var before=inventory.findById(id);var f=FoodCreateForm.from(before);
  var changed=new FoodCreateForm(f.foodName(),StorageType.FRIDGE,f.category(),new BigDecimal("10"),f.expiredAt(),f.purchasedAt(),f.openedAt(),f.frozenAt(),f.sourceType(),f.freezeType(),false,"냉장",f.capacityText(),f.sourceMemo(),f.sellByAt(),f.quantityUnit());
  long child=save(id,changed,"partial",null);
  assertThat(child).isNotEqualTo(id);
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("10");
  assertThat(inventory.findById(id).memo()).isEqualTo("원래");
  assertThat(inventory.findById(id).storageType()).isEqualTo(StorageType.ROOM);
  assertThat(inventory.findById(child).storageType()).isEqualTo(StorageType.FRIDGE);
  assertThat(inventory.findById(child).openedAt()).isEqualTo(before.openedAt());
  assertThat(inventory.findById(child).openingConfirmedAt()).isEqualTo(before.openingConfirmedAt());
 }
 @Test void partialRejectsNoChangeWholeAmountAndDifferentUnit() {
  long id=create();
  for(var f:List.of(form("10","개","원래"),form("20","개","수정"),form("21","개","수정"),form("0","개","수정"),form("10","팩","수정")))
   assertThatThrownBy(()->save(id,f,"partial",null)).isInstanceOf(InvalidFoodException.class);
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("20");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(1);
 }
 @Test void wholeMergeMovesOnlySelectedItemAndPreservesDifferentUnits() {
  long id=create(),source=masters.masterId(id);
  long sibling=inventory.create(form("3","개","다른 구매"),source,masters.find(source).versionNo());
  long targetItem=inventory.createCategorized(form("2","팩","대상").withIdentity("대상 음식",null),null,null,"kimchi",null),target=masters.masterId(targetItem);
  save(id,form("18","개","변경"),"all",target);
  assertThat(masters.masterId(id)).isEqualTo(target);assertThat(masters.masterId(sibling)).isEqualTo(source);
  assertThat(inventory.findById(targetItem).quantityAmount()).isEqualByComparingTo("2");
  assertThat(inventory.findById(targetItem).quantityUnit()).isEqualTo("팩");
  assertThat(inventory.findById(id).quantityUnit()).isEqualTo("개");
  assertThat(inventory.findById(id).foodName()).isEqualTo("대상 음식");
 }
 @Test void partialMergeSplitsWithoutChangingIdentityOfSource() {
  long id=create(),targetItem=create(),target=masters.masterId(targetItem);
  long child=save(id,form("5","개","원래").withIdentity("변조",null),"partial",target);
  assertThat(masters.masterId(child)).isEqualTo(target);
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("15");
  assertThat(inventory.findById(id).foodName()).isEqualTo("콜라");
 }
 @Test void invalidMetadataRollsBackSplit() {
  long id=create(),targetItem=create();
  var f=form("10","개","x".repeat(501));
  assertThatThrownBy(()->save(id,f,"partial",masters.masterId(targetItem))).isInstanceOf(InvalidFoodException.class);
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("20");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM food_split_receipt",Integer.class)).isZero();
 }
 @Test void staleTargetOrRepeatedSubmitCannotSplitAgain() {
  long id=create(),targetItem=create(),target=masters.masterId(targetItem);var before=inventory.findById(id);
  assertThatThrownBy(()->edits.save(id,form("5","개","원래"),before.updatedAt(),"partial",true,target,-1L,null,null)).isInstanceOf(InvalidFoodException.class);
  save(id,form("5","개","바꿈"),"partial",null);
  assertThatThrownBy(()->edits.save(id,form("5","개","바꿈"),before.updatedAt(),"partial",false,null,null,null,null)).isInstanceOf(InvalidFoodException.class);
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("15");
 }
 @Test void actualEditRendersAndPartialPostRedirectsToNewItem() throws Exception {
  long id=create();create();
  String html=mvc.perform(get("/inventory/"+id+"/edit")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertThat(html).contains("전체 수정","일부 수정","다른 음식과 합치기","item-edit.js");
  java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/item-edit"));
  java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/item-edit/edit.html"),html);
  mvc.perform(post("/inventory/"+id+"/edit").param("expectedUpdatedAt",inventory.findById(id).updatedAt().toString()).param("editMode","partial")
   .param("foodName","콜라").param("quantityAmount","10").param("quantityUnit","개").param("storageType","FRIDGE").param("sourceType","PURCHASE").param("memo","수정"))
   .andExpect(status().is3xxRedirection());
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("10");
 }
 @Test void mergeFromOnlyActiveItemLeavesEndedSiblingBehind() {
  long id=create(),source=masters.masterId(id);
  long ended=inventory.create(form("3","개","종료"),source,masters.find(source).versionNo());
  jdbc.update("UPDATE food_item SET status='DEPLETED',quantity_amount=0 WHERE food_id=?",ended);
  long targetItem=create();save(id,form("20","개","원래"),"all",masters.masterId(targetItem));
  assertThat(masters.masterId(ended)).isEqualTo(source);
 }
 @Test void controllerMergeRequiresSelectionAndPreservesErrorDraft() throws Exception {
  long id=create();
  mvc.perform(post("/inventory/"+id+"/edit").param("expectedUpdatedAt",inventory.findById(id).updatedAt().toString()).param("editMode","partial")
    .param("mergeEnabled","true").param("foodName","콜라").param("quantityAmount","10").param("quantityUnit","개").param("storageType","ROOM").param("sourceType","PURCHASE"))
    .andExpect(status().isOk()).andExpect(model().attribute("editMode","partial")).andExpect(model().attribute("mergeEnabled",true));
  assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("20");
 }

 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"partial-merge","partial-edit","all-merge","all-edit",
 "all-name-edit","all-category-edit","all-quantity-edit","all-unit-edit",
 "partial-freezer-edit","all-freezer-edit","partial-opened-edit","all-unknown-edit",
 "partial-forever-edit","all-warningdate-edit","partial-etc-edit",
 "partial-cancel-edit","all-cancel-edit","partial-reset-edit",
 "partial-preserved-merge","all-preserved-edit","partial-stale-merge","partial-double-merge"})
 @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PLAYWRIGHT_MODULE",matches=".+")
 void browserSubmissionPersistsQuantityAndHistory(String scenario) throws Exception {
  long id=create(),source=masters.masterId(id);
  long targetItem=inventory.createCategorized(form("20","개","대상").withIdentity("합칠 대상",null),null,null,"kimchi",null);
  long target=masters.masterId(targetItem);
  if(scenario.contains("-preserved-")) jdbc.update("UPDATE food_item SET opening_status='OPENED',opened_at='2026-09-01',opening_confirmed_at='2026-09-01',purchased_at='2026-08-01',warning_paused_until='9999-12-31' WHERE food_id=?",id);
  if(scenario.contains("-uncategorized-")) jdbc.update("UPDATE food_master SET category=NULL,category_major_code=NULL,category_minor_code=NULL WHERE master_id=?",source);
  var original=inventory.findById(id);
  var directory=java.nio.file.Path.of("build/reports/item-edit/browser-submit");java.nio.file.Files.createDirectories(directory);
  var fixture=directory.resolve(scenario+".html");var payload=directory.resolve(scenario+".json");
  String rendered;
  if(scenario.contains("-retrycategory-")) {
   rendered=mvc.perform(post("/inventory/"+id+"/edit").param("expectedUpdatedAt",original.updatedAt().toString())
    .param("editMode","all").param("foodName","콜라").param("quantityAmount","20").param("quantityUnit","개")
    .param("storageType","ROOM").param("sourceType","PURCHASE").param("memo","원래")
    .param("categoryMajorCode","soy").param("categoryMinorCode","soy_tofu").param("purchasedAt","2099-01-01"))
    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("foodForm","purchasedAt"))
    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  } else rendered=mvc.perform(get("/inventory/"+id+"/edit")).andExpect(status().isOk())
    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  java.nio.file.Files.writeString(fixture,rendered);
  var process=new ProcessBuilder("node","src/test/browser/item-edit-submit.cjs",fixture.toString(),payload.toString(),scenario)
    .redirectErrorStream(true).redirectOutput(directory.resolve(scenario+".log").toFile()).start();
  boolean completed=process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS);
  if(!completed) process.destroyForcibly();
  assertThat(completed).isTrue();
  assertThat(process.exitValue()).withFailMessage(java.nio.file.Files.readString(directory.resolve(scenario+".log"))).isZero();
  var fields=new com.fasterxml.jackson.databind.ObjectMapper().readTree(java.nio.file.Files.readString(payload));
  var request=post("/inventory/"+id+"/edit");
  for(var field:fields) request.param(field.get(0).asText(),field.get(1).asText());
  if(scenario.contains("-stale-")) {
   jdbc.update("UPDATE food_master SET version_no=version_no+1 WHERE master_id=?",target);
   mvc.perform(request).andExpect(status().isOk()).andExpect(model().hasErrors());
   assertThat(inventory.findById(id)).isEqualTo(original);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM food_split_receipt",Integer.class)).isZero();
   return;
  }
  String redirect=mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
  long edited=scenario.startsWith("partial") ? jdbc.queryForObject("SELECT child_id FROM food_split_receipt WHERE source_id=?",Long.class,id) : id;
  assertThat(redirect).startsWith("/inventory/"+edited);
  if(scenario.startsWith("partial")) {
   assertThat(inventory.findById(id).quantityAmount()).isEqualByComparingTo("17");
   assertThat(inventory.findById(edited).quantityAmount()).isEqualByComparingTo("3");
   assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE food_id=? AND action_type='SPLIT_OUT'",Integer.class,id)).isEqualTo(1);
  }
  if(scenario.endsWith("merge")) {
   assertThat(masters.masterId(edited)).isEqualTo(target);
   assertThat(jdbc.queryForObject("SELECT sum(quantity_amount) FROM food_item WHERE master_id=?",BigDecimal.class,target))
     .isEqualByComparingTo(scenario.startsWith("partial")?"23":"40");
   assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item_move_receipt WHERE food_id=?",Integer.class,edited)).isEqualTo(1);
   mvc.perform(get("/history")).andExpect(status().isOk())
     .andExpect(content().string(org.hamcrest.Matchers.containsString("병합")))
     .andExpect(content().string(org.hamcrest.Matchers.containsString("합칠 대상")));
  } else {
   assertThat(masters.masterId(edited)).isEqualTo(source);
   if(scenario.startsWith("partial")) assertThat(inventory.findById(id).memo()).isEqualTo("원래");
   assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE food_id=? AND action_type='UPDATE'",Integer.class,edited)).isEqualTo(1);
  }
  var after=inventory.findById(edited);
  if(scenario.contains("-name-")) assertThat(after.foodName()).isEqualTo("새 음식명");
  if(scenario.contains("-category-") || scenario.contains("-retrycategory-") || scenario.contains("-categorymissing-")) {
   assertThat(masters.find(source).categoryMajorCode()).isEqualTo("soy");
   assertThat(masters.find(source).categoryMinorCode()).isEqualTo("soy_tofu");
  }
  if(scenario.contains("-retrycategory-")) {
   assertThat(after.purchasedAt()).isNull();
   assertThat(jdbc.queryForObject("SELECT changes_text FROM food_history WHERE food_id=? AND action_type='UPDATE'",String.class,edited)).contains("분류:");
  }
  if(scenario.contains("-quantity-")) assertThat(after.quantityAmount()).isEqualByComparingTo("18");
  if(scenario.contains("-unit-")) assertThat(after.quantityUnit()).isEqualTo("팩");
  if(scenario.contains("-freezer-")) {assertThat(after.storageType()).isEqualTo(StorageType.FREEZER);assertThat(after.frozenAt()).isEqualTo(java.time.LocalDate.of(2026,9,1));}
  if(scenario.contains("-opened-")) assertThat(after.openedAt()).isEqualTo(java.time.LocalDate.of(2026,9,1));
  if(scenario.contains("-unknown-")) {assertThat(after.openingStatus()).isEqualTo(OpeningStatus.OPENED);assertThat(after.openedAt()).isNull();}
  if(scenario.contains("-forever-")) assertThat(after.warningPausedForever()).isTrue();
  if(scenario.contains("-warningdate-")) assertThat(after.warningPausedUntil()).isEqualTo(java.time.LocalDate.of(2099,1,1));
  if(scenario.contains("-etc-")) {assertThat(after.sourceType()).isEqualTo(FoodSourceType.ETC);assertThat(after.sourceMemo()).isEqualTo("받은 음식");}
  if(scenario.contains("-preserved-")) {assertThat(after.openedAt()).isEqualTo(original.openedAt());assertThat(after.purchasedAt()).isEqualTo(original.purchasedAt());assertThat(after.openingConfirmedAt()).isEqualTo(original.openingConfirmedAt());assertThat(after.warningPausedUntil()).isEqualTo(original.warningPausedUntil());}
  if(scenario.contains("-double-")) {mvc.perform(request).andExpect(status().isOk()).andExpect(model().hasErrors());assertThat(jdbc.queryForObject("SELECT count(*) FROM food_split_receipt",Integer.class)).isEqualTo(1);}
  assertThat(jdbc.queryForObject("SELECT sum(quantity_amount) FROM food_item",BigDecimal.class)).isEqualByComparingTo(scenario.contains("-quantity-")?"38":"40");
 }

 @Test
 @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PLAYWRIGHT_MODULE",matches=".+")
 void browserValidationRetryKeepsCategoryChangeSaveable() throws Exception {
  browserSubmissionPersistsQuantityAndHistory("all-retrycategory-edit");
 }

 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"all-categorymissing-edit","partial-uncategorized-edit","partial-uncategorized-merge"})
 @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PLAYWRIGHT_MODULE",matches=".+")
 void browserCategoryEdgeCases(String scenario) throws Exception {
  browserSubmissionPersistsQuantityAndHistory(scenario);
 }

}
