package com.euiseon.friger.cooking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.exception.*;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.history.dao.HistoryDao;
import com.euiseon.friger.account.AccountPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
@Import(CookingIntegrationTest.CorrectionConcurrencyConfig.class)
class CookingIntegrationTest {
    static volatile CountDownLatch correctionClaims;
    @TestConfiguration static class CorrectionConcurrencyConfig {
        @Bean Interceptor correctionBarrier() { return new CorrectionBarrier(); }
    }
    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
    static class CorrectionBarrier implements Interceptor {
        public Object intercept(Invocation invocation) throws Throwable {
            Object result=invocation.proceed();
            var gate=correctionClaims;
            if(gate!=null && ((MappedStatement)invocation.getArgs()[0]).getId().endsWith("CookingDao.claimCorrection")) {
                gate.countDown();
                if(!gate.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Correction claims did not overlap");
            }
            return result;
        }
    }
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired CookingService cooking;
    @Autowired CookingDao dao;
    @Autowired InventoryService foods;
    @Autowired FoodQuantityService quantities;
    @Autowired FoodMasterDao masters;
    @Autowired FoodSplitService splits;
    @Autowired FoodDeleteService deletes;
    @Autowired HistoryDao history;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM food_quantity_receipt");
        jdbc.update("DELETE FROM food_split_receipt");
        jdbc.update("DELETE FROM food_history WHERE reversal_of_history_id IS NOT NULL");
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM cooking_correction_receipt");
        jdbc.update("DELETE FROM cooking_ingredient");jdbc.update("DELETE FROM cooking_record");
        jdbc.update("DELETE FROM food_item");jdbc.update("DELETE FROM food_master");
    }
    @AfterEach void clearUser() { SecurityContextHolder.clearContext(); }
    long item(String name,String amount,String unit) {
        return foods.createCategorized(new FoodCreateForm(name,StorageType.FRIDGE,null,new BigDecimal(amount),
            null,null,null,null,FoodSourceType.PURCHASE,null,false,null,null,null,null,unit),null,null,"kimchi",null);
    }
    CookingForm.IngredientInput use(long id,String amount) {
        return new CookingForm.IngredientInput(id,quantities.preview(id).version(),new BigDecimal(amount),true);
    }
    CookingForm form(List<CookingForm.IngredientInput> ingredients) {
        return new CookingForm(UUID.randomUUID(),"카레",null,null,"kimchi",null,new BigDecimal("4"),"인분",
            LocalDate.now().minusDays(1),StorageType.FRIDGE,null,null,null,null,ingredients);
    }
    CookingForm change(CookingForm f,UUID token,Long master,Long version,BigDecimal eaten) {
        return new CookingForm(token,f.foodName(),master,version,f.categoryMajorCode(),f.categoryMinorCode(),f.quantityAmount(),
            f.quantityUnit(),f.cookedAt(),f.storageType(),f.frozenAt(),f.memo(),f.capacityText(),eaten,f.ingredients());
    }
    void stock(long id,String amount) { assertThat(foods.findById(id).quantityAmount()).isEqualByComparingTo(amount); }
    CookingService.Correction correction(UUID id,long item,String amount,String remaining) {
        var c=cooking.correctionContext(id,item);
        return new CookingService.Correction(UUID.randomUUID(),id,item,c.record().versionNo(),c.itemVersion(),
            new BigDecimal(amount),remaining==null?null:new BigDecimal(remaining));
    }
    @Test void manyIngredientsCreateOneHistoryAndIndependentResult() {
        long beef=item("소고기","500","g"),potato=item("감자","3","개");
        var f=form(List.of(use(beef,"100"),use(potato,"2")));
        UUID id=cooking.create(f);
        var detail=cooking.detail(id);
        stock(beef,"400");stock(potato,"1");stock(detail.record().resultFoodId(),"4");
        assertThat(foods.findById(detail.record().resultFoodId()).sourceType()).isEqualTo(FoodSourceType.COOKED);
        assertThat(dao.cookedAt(detail.record().resultFoodId())).isEqualTo(f.cookedAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE action_type='CREATE'",Integer.class)).isEqualTo(2);
        assertThat(history.findRecent(20).stream().filter(h->h.actionType()==FoodActionType.COOK_IN)).hasSize(1);
        assertThat(quantities.history(beef).getFirst().cookingDescription()).isEqualTo("카레 - 100g 사용 (500g → 400g)");
        assertThat(quantities.history(detail.record().resultFoodId()).getFirst().cookingDescription()).isEqualTo("수량 4인분");
    }
    @Test void immediateConsumptionIsSeparateAndReplayNeverDeductsAgain() {
        long beef=item("소고기","500","g");
        var base=form(List.of(use(beef,"100")));
        var f=change(base,base.requestId(),null,null,new BigDecimal("1"));
        var id=cooking.create(f);var result=cooking.detail(id).record().resultFoodId();
        stock(result,"3");assertThat(cooking.create(f)).isEqualTo(id);stock(beef,"400");stock(result,"3");
        assertThat(quantities.history(result)).extracting(QuantityChange::actionType).containsExactly("CONSUME","COOK_IN");
        assertThat(quantities.history(result).get(1).cookingDescription()).isEqualTo("수량 4인분");
        assertThatThrownBy(()->cooking.create(change(f,f.requestId(),null,null,new BigDecimal("2")))).isInstanceOf(InvalidFoodException.class);
    }
    @Test void existingGroupGetsNewItemIncludingWhenItIsAnIngredient() {
        long id=item("카레","5","인분"),masterId=masters.masterIdForItem(id);
        var f=form(List.of(use(id,"1")));
        var record=cooking.detail(cooking.create(change(f,f.requestId(),masterId,masters.find(masterId).versionNo(),null))).record();
        assertThat(record.resultFoodId()).isNotEqualTo(id);
        assertThat(masters.masterIdForItem(record.resultFoodId())).isEqualTo(masterId);
        stock(id,"4");stock(record.resultFoodId(),"4");
    }
    @Test void staleIngredientRollsBackClaimAndAllStock() {
        long a=item("감자","3","개"),b=item("소고기","500","g");
        var f=form(List.of(use(a,"2"),use(b,"100")));
        quantities.apply(b,FoodQuantityService.Action.CONSUME,quantities.preview(b).version(),null,UUID.randomUUID(),new BigDecimal("20"));
        assertThatThrownBy(()->cooking.create(f)).isInstanceOf(InvalidFoodException.class);
        stock(a,"3");stock(b,"480");assertThat(dao.find(f.requestId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(2);
    }
    @Test void unclassifiedIngredientKeepsItsGroupAndCategoryThroughCookingAndCorrection() {
        long ingredient=item("[다시다] 쇠고기 다시다","3","개"),masterId=masters.masterIdForItem(ingredient);
        jdbc.update("UPDATE food_master SET category_major_code=NULL,category_minor_code=NULL,category='기존 분류' WHERE master_id=?",masterId);
        var f=form(List.of(use(ingredient,"1")));
        assertThat(cooking.preview(f).ingredients()).hasSize(1);
        stock(ingredient,"3");
        var id=cooking.create(f);
        stock(ingredient,"2");
        assertThat(masters.masterIdForItem(ingredient)).isEqualTo(masterId);
        assertThat(masters.find(masterId).categoryMajorCode()).isNull();
        assertThat(masters.find(masterId).categoryMinorCode()).isNull();
        assertThat(masters.find(masterId).category()).isEqualTo("기존 분류");
        assertThat(cooking.detail(id).ingredients().getFirst().foodId()).isEqualTo(ingredient);
        assertThat(cooking.create(f)).isEqualTo(id);stock(ingredient,"2");
        cooking.correct(correction(id,ingredient,"0.5",null));stock(ingredient,"2.5");
        assertThat(masters.find(masterId).categoryMajorCode()).isNull();
    }
    @Test void selectedCategoryIsSavedForUnclassifiedResultThatIsAlsoAnIngredient() {
        long ingredient=item("기존 요리","5","인분"),masterId=masters.masterIdForItem(ingredient);
        jdbc.update("UPDATE food_master SET category_major_code=NULL,category_minor_code=NULL,category=NULL WHERE master_id=?",masterId);
        var base=form(List.of(use(ingredient,"1")));
        var f=change(base,base.requestId(),masterId,masters.find(masterId).versionNo(),BigDecimal.ONE);
        cooking.preview(f);
        stock(ingredient,"5");assertThat(masters.find(masterId).categoryMajorCode()).isNull();
        var id=cooking.create(f);var result=cooking.detail(id).record().resultFoodId();
        assertThat(result).isNotEqualTo(ingredient);
        assertThat(masters.masterIdForItem(ingredient)).isEqualTo(masterId);
        assertThat(masters.masterIdForItem(result)).isEqualTo(masterId);
        assertThat(masters.find(masterId).categoryMajorCode()).isEqualTo("kimchi");
        stock(ingredient,"4");stock(result,"3");
        assertThat(cooking.create(f)).isEqualTo(id);stock(ingredient,"4");stock(result,"3");
    }
    @Test void existingResultAndIngredientKeepTheirOwnStoredCategories() {
        long ingredient=foods.createCategorized(new FoodCreateForm("소고기",StorageType.FRIDGE,null,new BigDecimal("500"),
            null,null,null,null,FoodSourceType.PURCHASE,null,false,null,null,null,null,"g"),null,null,"meat","meat_beef");
        long resultItem=item("기존 요리","2","통"),resultMaster=masters.masterIdForItem(resultItem);
        var base=form(List.of(use(ingredient,"100")));
        var f=new CookingForm(base.requestId(),"무시할 이름",resultMaster,masters.find(resultMaster).versionNo(),
            "meat","meat_beef",base.quantityAmount(),base.quantityUnit(),base.cookedAt(),base.storageType(),
            null,null,null,null,base.ingredients());
        var result=cooking.detail(cooking.create(f)).record().resultFoodId();
        assertThat(masters.masterIdForItem(result)).isEqualTo(resultMaster);
        assertThat(masters.find(resultMaster).categoryMajorCode()).isEqualTo("kimchi");
        var ingredientMaster=masters.find(masters.masterIdForItem(ingredient));
        assertThat(ingredientMaster.categoryMajorCode()).isEqualTo("meat");
        assertThat(ingredientMaster.categoryMinorCode()).isEqualTo("meat_beef");
        stock(ingredient,"400");stock(resultItem,"2");stock(result,"4");
    }
    @Test void invalidAmountsAndDuplicateSelectionsCannotWrite() {
        long id=item("감자","3","개");
        for(String amount:List.of("0","-1","4","0.001")) {
            var f=form(List.of(use(id,amount)));
            assertThatThrownBy(()->cooking.create(f)).isInstanceOf(InvalidFoodException.class);
            assertThat(dao.find(f.requestId())).isNull();
        }
        assertThatThrownBy(()->cooking.create(form(List.of(use(id,"1"),use(id,"1"))))).isInstanceOf(InvalidFoodException.class);
        stock(id,"3");
    }
    @Test void fullUseEndsIngredientAndCorrectionReactivatesIt() {
        long id=item("소고기","500","g");var cook=cooking.create(form(List.of(use(id,"500"))));
        assertThat(foods.findById(id).status()).isEqualTo(FoodStatus.DEPLETED);
        assertThat(quantities.endedSummaries(masters.masterIdForItem(id)).get(id).endedAt()).isNotNull();
        cooking.correct(correction(cook,id,"100",null));stock(id,"400");
        assertThat(foods.findById(id).status()).isEqualTo(FoodStatus.ACTIVE);
        cooking.correct(correction(cook,id,"0",null));stock(id,"500");
    }
    @Test void correctionsUseLatestUsageAndPreserveLaterConsumption() {
        long id=item("소고기","500","g");var cook=cooking.create(form(List.of(use(id,"300"))));
        quantities.apply(id,FoodQuantityService.Action.CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),new BigDecimal("50"));
        var correction=correction(cook,id,"100",null);cooking.correct(correction);stock(id,"350");
        cooking.correct(correction);stock(id,"350");
        cooking.correct(correction(cook,id,"120",null));stock(id,"330");
        assertThat(quantities.preview(id).cancellable()).isFalse();
        assertThat(cooking.detail(cook).ingredients().getFirst().used()).isEqualTo("120g");
        stock(cooking.detail(cook).record().resultFoodId(),"4");
    }
    @Test void manualStockCorrectionRequiresConfirmedRemainingAndDoesNotDoubleRestore() {
        long id=item("소고기","500","g");var cook=cooking.create(form(List.of(use(id,"300"))));
        var before=foods.findById(id);
        foods.update(id,new FoodCreateForm(before.foodName(),before.storageType(),before.category(),new BigDecimal("400"),
            null,null,null,null,FoodSourceType.PURCHASE,null,false,null,null,null,null,"g"),before.updatedAt());
        assertThat(cooking.correctionContext(cook,id).needsRemaining()).isTrue();
        assertThatThrownBy(()->cooking.correct(correction(cook,id,"100",null))).isInstanceOf(InvalidFoodException.class);
        long stockRevision=jdbc.queryForObject("SELECT stock_revision FROM food_item WHERE food_id=?",Long.class,id);
        cooking.correct(correction(cook,id,"100","400"));stock(id,"400");
        assertThat(jdbc.queryForObject("SELECT stock_revision FROM food_item WHERE food_id=?",Long.class,id)).isEqualTo(stockRevision);
        assertThat(quantities.history(id).getFirst().actionType()).isEqualTo("UPDATE");
    }
    @Test void resultCorrectionDoesNotChangeIngredientsOrConsumption() {
        long id=item("소고기","500","g");var cook=cooking.create(form(List.of(use(id,"100"))));
        long result=cooking.detail(cook).record().resultFoodId();
        quantities.apply(result,FoodQuantityService.Action.CONSUME,quantities.preview(result).version(),null,UUID.randomUUID(),BigDecimal.ONE);
        cooking.correct(correction(cook,result,"5",null));stock(result,"4");stock(id,"400");
        assertThat(cooking.detail(cook).record().quantity()).isEqualTo("5인분");
        assertThat(quantities.history(result).getLast().cookingDescription()).isEqualTo("수량 4인분");
    }
    @Test void staleCorrectionCannotOverwriteNewStock() {
        long id=item("소고기","500","g");var cook=cooking.create(form(List.of(use(id,"300"))));
        var correction=correction(cook,id,"100",null);
        quantities.apply(id,FoodQuantityService.Action.CONSUME,quantities.preview(id).version(),null,UUID.randomUUID(),BigDecimal.ONE);
        assertThatThrownBy(()->cooking.correct(correction)).isInstanceOf(InvalidFoodException.class);stock(id,"199");
    }
    @Test void deletingAnItemRetainsCookingSnapshotAndDisablesItsLink() throws Exception {
        long id=item("감자","3","개");var cook=cooking.create(form(List.of(use(id,"2"))));
        long master=masters.masterIdForItem(id);
        mvc.perform(get("/foods/"+master+"/delete").param("items",Long.toString(id)))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("연결된 요리 기록은 남고")));
        deletes.delete(new FoodDeleteService.Command(master,masters.find(master).versionNo(),List.of(id),
            List.of(quantities.preview(id).version()),true),UUID.randomUUID());
        assertThat(cooking.detail(cook).ingredients().getFirst().itemExists()).isFalse();
        assertThat(cooking.detail(cook).ingredients().getFirst().used()).isEqualTo("2개");
    }
    @Test void crossAccountAccessCannotReadUseOrCorrect() {
        long id=item("감자","3","개");var cook=cooking.create(form(List.of(use(id,"2"))));
        var principal=new AccountPrincipal(999,"other","unused","USER",true,0);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,"",principal.getAuthorities()));
        assertThatThrownBy(()->cooking.detail(cook)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->cooking.preview(form(List.of(new CookingForm.IngredientInput(id,0L,BigDecimal.ONE,true))))).isInstanceOf(FoodNotFoundException.class);
        assertThatThrownBy(()->cooking.correct(new CookingService.Correction(UUID.randomUUID(),cook,id,0,0,BigDecimal.ONE,null))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void concurrentSubmissionsCannotOverspend() throws Exception {
        long id=item("감자","3","개");var f1=form(List.of(use(id,"2")));var f2=form(List.of(use(id,"2")));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var tasks=List.of(f1,f2).stream().map(f->executor.submit(()->{gate.await();try { cooking.create(f);return true; } catch(InvalidFoodException e) {return false;} })).toList();
            gate.countDown();int successes=0;for(var task:tasks) if(task.get(20,TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);stock(id,"1");
        }
    }
    @Test void ingredientSearchDoesNotRequireCompletedCookingOrChangeStock() throws Exception {
        long id=item("소고기","500","g");item("감자","4","개");
        var initial=mvc.perform(get("/cooking/new")).andExpect(status().isOk())
            .andExpect(model().attribute("visibleIngredientIds",List.of())).andReturn();
        var empty=(CookingForm)initial.getModelAndView().getModel().get("cookingForm");
        assertThat(empty.quantityAmount()).isNull();assertThat(empty.quantityUnit()).isNull();assertThat(empty.storageType()).isNull();
        mvc.perform(post("/cooking/preview").param("requestId",UUID.randomUUID().toString())
            .param("refresh","ingredients").param("ingredientQuery","소고기"))
            .andExpect(status().isOk()).andExpect(view().name("cooking/new"))
            .andExpect(model().attribute("visibleIngredientIds",List.of(id)));
        stock(id,"500");assertThat(jdbc.queryForObject("SELECT count(*) FROM cooking_record",Integer.class)).isZero();
    }
    @Test void blankIngredientSearchShowsAllAndSurvivesValidationWithoutChangingStock() throws Exception {
        long beef=item("소고기","500","g"),potato=item("감자","4","개");
        for(boolean refresh:List.of(true,false)) {
            var request=post("/cooking/preview").param("requestId",UUID.randomUUID().toString())
                .param("ingredientQuery","   ");
            if(refresh) request.param("refresh","ingredients");
            else request.param("ingredientSearchPerformed","true");
            var result=mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("cooking/new"))
                .andExpect(model().attribute("ingredientSearchPerformed",true)).andReturn();
            assertThat((List<Long>)result.getModelAndView().getModel().get("visibleIngredientIds"))
                .containsExactlyInAnyOrder(beef,potato);
        }
        stock(beef,"500");stock(potato,"4");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cooking_record",Integer.class)).isZero();
    }
    @Test void bothPickersUseTheSameCategoryCodesAndOnlyIngredientsShowQuantity() throws Exception {
        long beef=foods.createCategorized(new FoodCreateForm("소고기",StorageType.FRIDGE,null,new BigDecimal("3"),
            null,null,null,null,FoodSourceType.PURCHASE,null,false,null,null,null,null,"그릇"),null,null,"meat","meat_beef");
        long kimchi=item("김치","2","통"),unknown=item("미분류 재료","1","개");
        long beefMaster=masters.masterIdForItem(beef),kimchiMaster=masters.masterIdForItem(kimchi),unknownMaster=masters.masterIdForItem(unknown);
        jdbc.update("UPDATE food_master SET category='이전 분류 이름' WHERE master_id=?",beefMaster);
        jdbc.update("UPDATE food_master SET category_major_code=NULL,category_minor_code=NULL,category='옛 직접 입력 분류' WHERE master_id=?",unknownMaster);
        var html=mvc.perform(get("/cooking/new")).andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("육류 &gt; 소고기 / 3그릇 / #"+beef,"육류 &gt; 소고기 / #"+beefMaster,
            "김치 / 2통 / #"+kimchi,"김치 / #"+kimchiMaster,"미분류 / 1개 / #"+unknown,"미분류 / #"+unknownMaster);
        assertThat(html).doesNotContain("이전 분류 이름","옛 직접 입력 분류"," / 잔량 "," / 보관 ");
        stock(beef,"3");stock(kimchi,"2");stock(unknown,"1");
    }
    @Test void unclassifiedIngredientSurvivesDraftReturnAndValidationRetry() throws Exception {
        long ingredient=item("[다시다] 쇠고기 다시다","3","개"),masterId=masters.masterIdForItem(ingredient);
        jdbc.update("UPDATE food_master SET category_major_code=NULL,category_minor_code=NULL,category=NULL WHERE master_id=?",masterId);
        var session=new MockHttpSession();var token=UUID.randomUUID();
        var params=new org.springframework.util.LinkedMultiValueMap<String,String>();
        params.add("requestId",token.toString());params.add("foodName","소고기국");params.add("resultMode","new");
        params.add("categoryMajorCode","dish");params.add("categoryMinorCode","dish_soup");
        params.add("quantityAmount","4");params.add("quantityUnit","인분");params.add("storageType","FRIDGE");
        params.add("cookedAt",LocalDate.now().toString());params.add("ingredients[0].foodId",Long.toString(ingredient));
        params.add("ingredients[0].version",Long.toString(quantities.preview(ingredient).version()));
        params.add("ingredients[0].amount","1");params.add("ingredients[0].selected","true");
        mvc.perform(post("/cooking/preview").session(session).params(params))
            .andExpect(status().isOk()).andExpect(view().name("cooking/preview"));
        var returned=mvc.perform(get("/cooking/new").session(session).param("draft",token.toString()))
            .andExpect(status().isOk()).andExpect(view().name("cooking/new")).andReturn();
        var draft=(CookingForm)returned.getModelAndView().getModel().get("cookingForm");
        assertThat(draft.selected()).hasSize(1);assertThat(draft.selected().getFirst().foodId()).isEqualTo(ingredient);
        assertThat(draft.selected().getFirst().amount()).isEqualByComparingTo("1");
        assertThat(draft.categoryMajorCode()).isEqualTo("dish");assertThat(draft.categoryMinorCode()).isEqualTo("dish_soup");
        params.set("quantityAmount","0");
        var invalid=mvc.perform(post("/cooking/preview").session(session).params(params))
            .andExpect(status().isOk()).andExpect(view().name("cooking/new"))
            .andExpect(model().attributeHasFieldErrors("cookingForm","quantityAmount")).andReturn();
        var retry=(CookingForm)invalid.getModelAndView().getModel().get("cookingForm");
        assertThat(retry.selected()).isEqualTo(draft.selected());
        assertThat(retry.categoryMajorCode()).isEqualTo("dish");assertThat(retry.categoryMinorCode()).isEqualTo("dish_soup");
        stock(ingredient,"3");assertThat(masters.find(masterId).categoryMajorCode()).isNull();
        params.set("quantityAmount","4");
        mvc.perform(post("/cooking/preview").session(session).params(params))
            .andExpect(status().isOk()).andExpect(view().name("cooking/preview"));
        mvc.perform(post("/cooking").session(session).param("requestId",token.toString()))
            .andExpect(redirectedUrl("/cooking/"+token));
        stock(ingredient,"2");assertThat(masters.find(masterId).categoryMajorCode()).isNull();
        var resultMaster=masters.find(masters.masterIdForItem(cooking.detail(token).record().resultFoodId()));
        assertThat(resultMaster.categoryMajorCode()).isEqualTo("dish");
        assertThat(resultMaster.categoryMinorCode()).isEqualTo("dish_soup");
    }
    @Test void completeWebFlowRendersAndSavesReviewedData() throws Exception {
        long id=item("소고기","500","g");var session=new MockHttpSession();var token=UUID.randomUUID();
        mvc.perform(get("/cooking/new")).andExpect(status().isOk()).andExpect(view().name("cooking/new"));
        mvc.perform(post("/cooking/preview").session(session).param("requestId",token.toString()).param("foodName","카레")
            .param("categoryMajorCode","kimchi").param("quantityAmount","4").param("quantityUnit","인분")
            .param("storageType","FRIDGE").param("cookedAt",LocalDate.now().minusDays(1).toString())
            .param("ingredients[0].foodId",Long.toString(id)).param("ingredients[0].version",Long.toString(quantities.preview(id).version()))
            .param("ingredients[0].amount","100").param("ingredients[0].selected","true")
            .param("ingredients[1].foodId",Long.toString(id)).param("ingredients[1].version",Long.toString(quantities.preview(id).version())))
            .andExpect(status().isOk()).andExpect(view().name("cooking/preview"));
        stock(id,"500");
        mvc.perform(post("/cooking").session(session).param("requestId",token.toString()).param("quantityAmount","999"))
            .andExpect(redirectedUrl("/cooking/"+token));stock(id,"400");
        mvc.perform(get("/cooking/"+token)).andExpect(status().isOk());
        mvc.perform(get("/history").param("q","소고기")).andExpect(status().isOk());
        mvc.perform(get("/inventory/"+id).param("cooking",token.toString()).param("quantityHistory","true"))
            .andExpect(status().isOk()).andExpect(model().attribute("showQuantityHistory",true))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("카레 - 100g 사용 (500g → 400g)")));
        mvc.perform(get("/cooking/"+token+"/correct/"+id)).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("요리했어!")));
    }
    @Test void missingStorageUsesTheSharedGroupErrorAndDoesNotDeductIngredients() throws Exception {
        long id=item("감자","4","개");
        var result=mvc.perform(post("/cooking/preview").param("requestId",UUID.randomUUID().toString())
            .param("foodName","카레").param("categoryMajorCode","kimchi").param("quantityAmount","2").param("quantityUnit","통")
            .param("cookedAt",LocalDate.now().toString())
            .param("ingredients[0].foodId",Long.toString(id)).param("ingredients[0].version",Long.toString(quantities.preview(id).version()))
            .param("ingredients[0].amount","1").param("ingredients[0].selected","true"))
            .andExpect(status().isOk()).andExpect(view().name("cooking/new"))
            .andExpect(model().attributeHasFieldErrors("cookingForm","storageType")).andReturn();
        var radios=java.util.regex.Pattern.compile("<input(?=[^>]*name=\"storageType\")[^>]*>")
            .matcher(result.getResponse().getContentAsString()).results().map(java.util.regex.MatchResult::group).toList();
        assertThat(radios).hasSize(3).allSatisfy(radio->assertThat(radio).contains("aria-invalid=\"true\"","class=\"invalid\"").doesNotContain("checked"));
        assertThat(radios.get(0)).contains("value=\"ROOM\"");assertThat(radios.get(1)).contains("value=\"FRIDGE\"");assertThat(radios.get(2)).contains("value=\"FREEZER\"");
        stock(id,"4");assertThat(jdbc.queryForObject("SELECT count(*) FROM cooking_record",Integer.class)).isZero();
    }
    @Test void commonInputValidationMatchesFoodRegistrationAndPreservesInvalidValues() throws Exception {
        var cases=List.of(Map.entry("quantityAmount",""),Map.entry("quantityAmount","0"),
            Map.entry("quantityAmount","1.234"),Map.entry("quantityAmount","1000000000"),Map.entry("quantityAmount","not-a-number"),
            Map.entry("quantityUnit",""),Map.entry("quantityUnit","   "),Map.entry("quantityUnit","가나다라마"),
            Map.entry("capacityText","용".repeat(51)),Map.entry("memo","메".repeat(501)));
        for(var invalid:cases) {
            var messages=new ArrayList<String>();
            for(var route:List.of("/inventory","/cooking/preview")) {
                var params=new org.springframework.util.LinkedMultiValueMap<String,String>();
                params.add("foodName","필드 검증");params.add("categoryMajorCode","kimchi");
                params.add("quantityAmount","2");params.add("quantityUnit","통");params.add("storageType","FRIDGE");
                params.add("cookedAt",LocalDate.now().toString());params.add("requestId",UUID.randomUUID().toString());
                params.add("registrationRequestId",UUID.randomUUID().toString());params.set(invalid.getKey(),invalid.getValue());
                var name=route.equals("/inventory")?"foodForm":"cookingForm";
                var result=mvc.perform(post(route).params(params)).andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors(name,invalid.getKey())).andReturn();
                var binding=(org.springframework.validation.BindingResult)result.getModelAndView().getModel()
                    .get(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX+name);
                assertThat(binding.getFieldErrors(invalid.getKey())).hasSize(1);
                var html=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                var error=java.util.regex.Pattern.compile("<p[^>]*id=\""+invalid.getKey()+"-error\"[^>]*>([^<]+)</p>").matcher(html);
                assertThat(error.find()).as(route+" "+invalid.getKey()).isTrue();messages.add(error.group(1));
                assertThat(html).contains("novalidate");
                var field=java.util.regex.Pattern.compile("<(?:input|textarea)(?=[^>]*id=\""+invalid.getKey()+"\")[^>]*>").matcher(html);
                assertThat(field.find()).isTrue();assertThat(field.group()).contains("aria-invalid=\"true\"","class=\"invalid\"");
                if(invalid.getValue().equals("not-a-number")) assertThat(field.group()).contains("value=\"not-a-number\"");
                if(invalid.getKey().equals("capacityText") || invalid.getKey().equals("memo")) assertThat(html).contains(invalid.getValue());
            }
            assertThat(messages.get(1)).as(invalid.getKey()+" messages").isEqualTo(messages.get(0));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isZero();
    }
    @Test void sharedOptionalFieldsAllowEmptyValuesAndTheSameMaximumLengths() throws Exception {
        long id=item("감자","4","개");
        for(boolean atLimit:List.of(false,true)) {
            var result=mvc.perform(post("/cooking/preview").param("requestId",UUID.randomUUID().toString())
                .param("foodName","카레").param("categoryMajorCode","kimchi").param("quantityAmount","0.5").param("quantityUnit","인분")
                .param("storageType","FRIDGE").param("cookedAt",LocalDate.now().toString())
                .param("capacityText",atLimit?"용".repeat(50):"").param("memo",atLimit?"메".repeat(500):"")
                .param("ingredients[0].foodId",Long.toString(id)).param("ingredients[0].version",Long.toString(quantities.preview(id).version()))
                .param("ingredients[0].amount","1").param("ingredients[0].selected","true"))
                .andExpect(status().isOk()).andExpect(view().name("cooking/preview")).andReturn();
            var preview=(CookingService.Preview)result.getModelAndView().getModel().get("preview");
            assertThat(preview.form().capacityText()).hasSize(atLimit?50:0);assertThat(preview.form().memo()).hasSize(atLimit?500:0);
        }
        stock(id,"4");
    }
    @Test void existingResultModeRequiresAnExplicitFoodSelection() throws Exception {
        long id=item("감자","4","개");
        mvc.perform(post("/cooking/preview").param("requestId",UUID.randomUUID().toString())
            .param("resultMode","existing").param("foodName","이전 신규 요리 초안")
            .param("categoryMajorCode","kimchi").param("quantityAmount","2").param("quantityUnit","통")
            .param("storageType","FRIDGE").param("cookedAt",LocalDate.now().toString())
            .param("ingredients[0].foodId",Long.toString(id)).param("ingredients[0].version",Long.toString(quantities.preview(id).version()))
            .param("ingredients[0].amount","1").param("ingredients[0].selected","true"))
            .andExpect(status().isOk()).andExpect(view().name("cooking/new"))
            .andExpect(model().attribute("errorMessage","만들어진 요리로 등록할 기존 음식을 검색해서 선택해줘."));
        stock(id,"4");assertThat(jdbc.queryForObject("SELECT count(*) FROM cooking_record",Integer.class)).isZero();
    }
    @Test void correctionWebFlowRequiresReviewAndRendersTheBeforeAfterAmounts() throws Exception {
        long id=item("소고기","500","g");UUID cook=cooking.create(form(List.of(use(id,"300"))));
        var command=correction(cook,id,"100",null);var session=new MockHttpSession();
        mvc.perform(post("/cooking/"+cook+"/correct/"+id+"/preview").session(session)
            .param("requestId",command.requestId().toString()).param("cookingVersion",Long.toString(command.cookingVersion()))
            .param("itemVersion",Long.toString(command.itemVersion())).param("amount","100"))
            .andExpect(status().isOk()).andExpect(view().name("cooking/correction-preview"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("200g → 400g")));
        stock(id,"200");
        mvc.perform(post("/cooking/"+cook+"/correct").session(session).param("requestId",command.requestId().toString()))
            .andExpect(redirectedUrl("/cooking/"+cook));stock(id,"400");
    }
    @Test void resultSplitKeepsCookingDateAndRequiresRemainingForLaterCorrection() {
        long id=item("감자","3","개");var f=form(List.of(use(id,"2")));UUID cook=cooking.create(f);
        long result=cooking.detail(cook).record().resultFoodId();
        long child=splits.split(result,new FoodSplitService.Command(BigDecimal.ONE,StorageType.FRIDGE,null,null,false),
            quantities.preview(result).version(),UUID.randomUUID());
        assertThat(dao.cookedAt(child)).isEqualTo(f.cookedAt());
        assertThat(cooking.correctionContext(cook,result).needsRemaining()).isTrue();
        assertThatThrownBy(()->cooking.correct(correction(cook,result,"5",null))).isInstanceOf(InvalidFoodException.class);
        cooking.correct(correction(cook,result,"5","4"));stock(child,"1");stock(result,"4");
    }
    @Test void resultHistoryFailureRollsBackEveryDeductionAndNewItem() {
        long id=item("감자","3","개");var f=form(List.of(use(id,"2")));
        jdbc.execute("CREATE FUNCTION reject_cook_result() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action_type='COOK_IN' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_cook_result BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION reject_cook_result()");
        try {
            assertThatThrownBy(()->cooking.create(f)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            stock(id,"3");assertThat(dao.find(f.requestId())).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER reject_cook_result ON food_history");jdbc.execute("DROP FUNCTION reject_cook_result()"); }
    }
    @Test void ingredientSearchReturnsOneCookingEvenForSeveralMatchingIngredients() {
        long a=item("감자 A","3","개"),b=item("감자 B","5","개");var f=form(List.of(use(a,"1"),use(b,"2")));
        UUID cook=cooking.create(f);
        var rows=history.findEntries(null,"감자").stream().filter(h->h.actionType()==FoodActionType.COOK_IN).toList();
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().cookingId()).isEqualTo(cook);
        assertThat(rows.getFirst().detailFields()).anyMatch(field->field.value().equals("감자 A 1개, 감자 B 2개"));
    }
    @Test void metadataEditDoesNotRequireRemainingForCorrection() {
        long id=item("소고기","500","g");var cook=cooking.create(form(List.of(use(id,"100"))));
        var before=foods.findById(id);
        foods.update(id,new FoodCreateForm(before.foodName(),before.storageType(),before.category(),before.quantityAmount().stripTrailingZeros(),
            before.expiredAt(),before.purchasedAt(),before.openedAt(),before.frozenAt(),before.sourceType(),before.freezeType(),false,
            "메모만 변경",before.capacityText(),before.sourceMemo(),before.sellByAt(),before.quantityUnit()),before.updatedAt());
        stock(id,"400");
        assertThat(cooking.correctionContext(cook,id).needsRemaining()).isFalse();
        cooking.correct(correction(cook,id,"80",null));stock(id,"420");
        assertThat(foods.findById(id).memo()).isEqualTo("메모만 변경");
    }
    @Test void unknownLegacyQuantityUpdateStillRequiresConfirmedRemaining() {
        long id=item("소고기","500","g");var cook=cooking.create(form(List.of(use(id,"100"))));
        jdbc.update("INSERT INTO food_history(user_id,food_id,action_type,previous_storage_type,new_storage_type,created_at) SELECT user_id,food_id,'UPDATE',storage_type,storage_type,clock_timestamp() FROM food_item WHERE food_id=?",id);
        assertThat(cooking.correctionContext(cook,id).needsRemaining()).isTrue();
        assertThatThrownBy(()->cooking.correct(correction(cook,id,"80",null))).isInstanceOf(InvalidFoodException.class);
        stock(id,"400");
    }
    @Test void largeInventorySubmitsOnlyTheSelectedIngredientWithCompactIndexes() throws Exception {
        var ids=new ArrayList<Long>();for(int i=0;i<257;i++) ids.add(item("재료 "+i,"5","개"));
        long selected=ids.getLast();UUID requestId=UUID.randomUUID();var session=new MockHttpSession();
        mvc.perform(post("/cooking/preview").session(session)
            .param("requestId",requestId.toString()).param("foodName","요리").param("categoryMajorCode","kimchi")
            .param("quantityAmount","4").param("quantityUnit","인분").param("storageType","FRIDGE").param("cookedAt",LocalDate.now().toString())
            .param("ingredients[0].foodId",Long.toString(selected)).param("ingredients[0].version",Long.toString(quantities.preview(selected).version()))
            .param("ingredients[0].selected","true").param("ingredients[0].amount","1"))
            .andExpect(status().isOk()).andExpect(view().name("cooking/preview"));
        stock(selected,"5");
        mvc.perform(post("/cooking").session(session).param("requestId",requestId.toString())).andExpect(redirectedUrl("/cooking/"+requestId));
        stock(selected,"4");stock(ids.getFirst(),"5");
        assertThat(cooking.detail(requestId).ingredients()).extracting(CookingDao.Ingredient::foodId).containsExactly(selected);
    }
    @Test void completingUnclassifiedResultRetainsCategoryAuditWithoutDuplicateRegistration() {
        long ingredient=item("재료","5","개"),target=item("기존 미분류 요리","1","인분"),master=masters.masterIdForItem(target);
        jdbc.update("UPDATE food_master SET category_major_code=NULL,category_minor_code=NULL,category='옛 분류' WHERE master_id=?",master);
        var f=form(List.of(use(ingredient,"1")));
        var cook=cooking.create(change(f,f.requestId(),master,masters.find(master).versionNo(),null));
        var result=cooking.detail(cook).record().resultFoodId();
        assertThat(masters.find(master).categoryMajorCode()).isEqualTo("kimchi");
        assertThat(jdbc.queryForObject("SELECT category_before->>'label' FROM food_history WHERE food_id=? AND action_type='COOK_IN'",String.class,result)).isEqualTo("옛 분류");
        assertThat(jdbc.queryForObject("SELECT category_after->>'major_code' FROM food_history WHERE food_id=? AND action_type='COOK_IN'",String.class,result)).isEqualTo("kimchi");
        assertThat(jdbc.queryForObject("SELECT after_snapshot->>'category_major_code' FROM food_history WHERE food_id=? AND action_type='COOK_IN'",String.class,result)).isEqualTo("kimchi");
        assertThat(quantities.history(result)).extracting(QuantityChange::actionType).containsExactly("COOK_IN");
        assertThat(masters.find(masters.masterIdForItem(ingredient)).categoryMajorCode()).isEqualTo("kimchi");
    }
    @Test void resultHistoryFailureAlsoRollsBackExistingGroupCategoryCompletion() {
        long ingredient=item("재료","5","개"),target=item("미분류 요리","1","인분"),master=masters.masterIdForItem(target);
        jdbc.update("UPDATE food_master SET category_major_code=NULL,category_minor_code=NULL,category='옛 분류' WHERE master_id=?",master);
        var before=masters.find(master);var base=form(List.of(use(ingredient,"1")));
        var f=change(base,base.requestId(),master,before.versionNo(),null);
        jdbc.execute("CREATE FUNCTION reject_cook_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action_type='COOK_IN' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_cook_audit BEFORE INSERT ON food_history FOR EACH ROW EXECUTE FUNCTION reject_cook_audit()");
        try {
            assertThatThrownBy(()->cooking.create(f)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(masters.find(master)).isEqualTo(before);stock(ingredient,"5");stock(target,"1");
            assertThat(dao.find(f.requestId())).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(2);
        } finally {jdbc.execute("DROP TRIGGER reject_cook_audit ON food_history");jdbc.execute("DROP FUNCTION reject_cook_audit()");}
    }
    @Test void concurrentCorrectionsReturnVersionConflictInsteadOfDatabaseDeadlock() throws Exception {
        long a=item("소고기","500","g"),b=item("감자","5","개");var cook=cooking.create(form(List.of(use(a,"100"),use(b,"1"))));
        var commands=List.of(correction(cook,a,"80",null),correction(cook,b,"2",null));
        correctionClaims=new CountDownLatch(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var tasks=commands.stream().map(command->executor.submit(()->{try{cooking.correct(command);return (Throwable)null;}catch(Throwable error){return error;}})).toList();
            var errors=new ArrayList<Throwable>();for(var task:tasks){var error=task.get(20,TimeUnit.SECONDS);if(error!=null)errors.add(error);}
            assertThat(errors).hasSize(1);assertThat(errors.getFirst()).isInstanceOf(InvalidFoodException.class);
        } finally {correctionClaims=null;}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cooking_correction_receipt WHERE cooking_id=?",Integer.class,cook)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_history WHERE cooking_id=? AND action_type='UPDATE'",Integer.class,cook)).isEqualTo(1);
        var detail=cooking.detail(cook);
        for(var ingredient:detail.ingredients()) {
            assertThat(foods.findById(ingredient.foodId()).quantityAmount()).isEqualByComparingTo(ingredient.beforeAmount().subtract(ingredient.usedAmount()));
        }
    }
}
