package com.euiseon.friger.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.service.*;
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
class FoodSplitIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired FoodSplitService splits;
    @Autowired FoodQuantityService quantities;
    @Autowired InventoryService inventory;
    @Autowired FoodMasterService masters;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM food_split_receipt");jdbc.update("DELETE FROM food_quantity_receipt");
        jdbc.update("DELETE FROM food_history WHERE reversal_of_history_id IS NOT NULL");
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");jdbc.update("DELETE FROM food_master");
    }
    long create() throws Exception {
        mvc.perform(post("/inventory").param("registrationRequestId",UUID.randomUUID().toString())
            .param("foodName","두부").param("quantityAmount","2.5").param("quantityUnit","모")
            .param("storageType","FRIDGE").param("sourceType","PURCHASE").param("memo","원래 메모")
            .param("capacityText","300g").param("purchasedAt","2026-09-01").param("expiredAt","2026-12-01").param("openedAt","2026-09-10"))
            .andExpect(status().is3xxRedirection());
        return jdbc.queryForObject("SELECT max(food_id) FROM food_item",Long.class);
    }
    FoodSplitService.Command freezerCommand(String amount) {
        return new FoodSplitService.Command(new BigDecimal(amount),StorageType.FREEZER,null,null,true);
    }
    long version(long id) {return quantities.preview(id).version();}

    @Test void splitDeductsSourceAndCreatesLinkedSibling() throws Exception {
        long id=create(),master=masters.masterId(id);
        long child=splits.split(id,freezerCommand("1"),version(id),UUID.randomUUID());
        assertThat(child).isNotEqualTo(id);
        var source=inventory.findById(id);
        assertThat(source.quantityAmount()).isEqualByComparingTo("1.5");
        assertThat(source.status()).isEqualTo(FoodStatus.ACTIVE);
        assertThat(source.storageType()).isEqualTo(StorageType.FRIDGE);
        var item=inventory.findById(child);
        assertThat(item.foodName()).isEqualTo("두부");
        assertThat(item.quantityAmount()).isEqualByComparingTo("1");
        assertThat(item.quantityUnit()).isEqualTo("모");
        assertThat(item.storageType()).isEqualTo(StorageType.FREEZER);
        assertThat(item.freezeType()).isEqualTo(FreezeType.HOME_FROZEN);
        assertThat(item.frozenAt()).isEqualTo(LocalDate.now());
        assertThat(item.memo()).isEqualTo("원래 메모");
        assertThat(item.capacityText()).isEqualTo("300g");
        assertThat(item.purchasedAt()).isEqualTo(LocalDate.of(2026,9,1));
        assertThat(item.expiredAt()).isEqualTo(LocalDate.of(2026,12,1));
        assertThat(item.openedAt()).isEqualTo(LocalDate.of(2026,9,10));
        assertThat(item.sourceType()).isEqualTo(FoodSourceType.PURCHASE);
        assertThat(masters.masterId(child)).isEqualTo(master);
        var sourceChange=quantities.history(id).getFirst();
        assertThat(sourceChange.actionLabel()).isEqualTo("분리");
        assertThat(sourceChange.changeQuantity()).isEqualTo("-1모");
        assertThat(sourceChange.remainingQuantity()).isEqualTo("1.5모");
        var childChange=quantities.history(child).getFirst();
        assertThat(childChange.actionLabel()).isEqualTo("분리");
        assertThat(childChange.changeQuantity()).isEqualTo("+1모");
        assertThat(childChange.remainingQuantity()).isEqualTo("1모");
        assertThat(jdbc.queryForObject("SELECT child_id FROM food_split_receipt WHERE source_id=?",Long.class,id)).isEqualTo(child);
    }
    @Test void splitIsIdempotentPerTokenAndRejectsChangedContent() throws Exception {
        long id=create();long version=version(id);UUID token=UUID.randomUUID();
        long child=splits.split(id,freezerCommand("1"),version,token);
        assertThat(splits.split(id,freezerCommand("1"),version,token)).isEqualTo(child);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->splits.split(id,freezerCommand("0.5"),version,token)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void splitRejectsInvalidAmountsStatesAndVersions() throws Exception {
        long id=create();
        assertThatThrownBy(()->splits.split(id,freezerCommand("2.5"),version(id),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->splits.split(id,freezerCommand("3"),version(id),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->splits.split(id,freezerCommand("0"),version(id),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->splits.split(id,freezerCommand("0.005"),version(id),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->splits.split(id,new FoodSplitService.Command(new BigDecimal("1"),null,null,null,false),version(id),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->splits.split(id,new FoodSplitService.Command(new BigDecimal("1"),StorageType.FREEZER,null,LocalDate.now().plusDays(1),false),version(id),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThatThrownBy(()->splits.split(id,freezerCommand("1"),version(id)+1,UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        quantities.apply(id,CONSUME,version(id),null,UUID.randomUUID(),null);
        assertThatThrownBy(()->splits.split(id,freezerCommand("1"),version(id),UUID.randomUUID())).isInstanceOf(InvalidFoodException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(1);
    }
    @Test void splitBlocksCancellationOfEarlierProcessing() throws Exception {
        long id=create();
        long event=quantities.apply(id,CONSUME,version(id),null,UUID.randomUUID(),new BigDecimal("0.5"));
        splits.split(id,freezerCommand("1"),version(id),UUID.randomUUID());
        assertThat(quantities.preview(id).cancellable()).isFalse();
        assertThatThrownBy(()->quantities.apply(id,CANCEL,version(id),event,UUID.randomUUID(),null)).isInstanceOf(InvalidFoodException.class);
    }
    @Test void endedSplitChildShowsItsFirstQuantity() throws Exception {
        long id=create(),master=masters.masterId(id);
        long child=splits.split(id,freezerCommand("1"),version(id),UUID.randomUUID());
        quantities.apply(child,CONSUME,version(child),null,UUID.randomUUID(),null);
        assertThat(quantities.endedSummaries(master).get(child).registrationQuantity()).isEqualTo("1모");
        mvc.perform(get("/inventory/"+child)).andExpect(status().isOk())
            .andExpect(content().string(containsString("최초 등록 수량")))
            .andExpect(content().string(containsString("1모")));
    }
    @Test void splitScreenAndSubmitFlowKeepFilters() throws Exception {
        long id=create();
        mvc.perform(get("/inventory/"+id+"/split").param("storage","FRIDGE"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("나눌 수량")))
            .andExpect(content().string(containsString("취소할 수 없어")));
        long version=version(id);
        mvc.perform(post("/inventory/"+id+"/split")
            .param("version",String.valueOf(version)).param("requestId",UUID.randomUUID().toString())
            .param("quantityAmount","1").param("newStorage","FREEZER").param("freezeToday","true")
            .param("storage","FRIDGE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/inventory/*?storage=FRIDGE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(2);
        mvc.perform(post("/inventory/"+id+"/split")
            .param("version",String.valueOf(version(id))).param("requestId",UUID.randomUUID().toString())
            .param("quantityAmount","한모").param("newStorage","FRIDGE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/inventory/"+id));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(2);
    }
    @Test void historyPageShowsOneCombinedSplitRow() throws Exception {
        long id=create();
        splits.split(id,freezerCommand("1"),version(id),UUID.randomUUID());
        var html=mvc.perform(get("/history")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("data-event=\"SPLIT_OUT\"","분리","새 항목","+1모");
        assertThat(html).doesNotContain("data-event=\"SPLIT_IN\"");
    }
}
