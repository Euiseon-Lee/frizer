package com.euiseon.friger.inventory;

import com.euiseon.friger.inventory.bulk.*;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BulkRegistrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.11").withDatabaseName("frizer_bulk_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword); }
    @Autowired BulkRegistrationService bulk;
    @Autowired BulkWorkbook workbook;
    @Autowired FoodMasterDao masters;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    UUID owner;
    @BeforeEach void clear() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_bulk ON food_item");
        jdbc.update("DELETE FROM food_bulk_receipt");jdbc.update("DELETE FROM food_bulk_preview");
        jdbc.update("DELETE FROM food_history");jdbc.update("DELETE FROM food_item");jdbc.update("DELETE FROM food_master");
        owner=UUID.randomUUID();
    }
    String[] row(String name,String amount,String group,String id) {
        String selected=id;
        if (!id.isBlank()) {
            var master=masters.find(Long.parseLong(id));
            selected=master==null?"없는 음식 [#"+id+"]":BulkWorkbook.choiceLabel(master.masterId(),master.foodName(),master.category());
        }
        return new String[]{name,amount,"모","냉장실",group,selected,"300g","장보기","","","","","","","","",""};
    }
    byte[] file(String[]... rows) throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(masters.registrationChoices()))); var out=new ByteArrayOutputStream()) {
            int fresh=4,additional=4;
            for(var values:rows) {
                boolean adding=!values[5].isBlank();
                var sheet=book.getSheet(adding?"추가 등록":"신규 등록");
                int index=adding?additional++:fresh++;
                var line=sheet.getRow(index);if(line==null)line=sheet.createRow(index);
                int[] map=adding?new int[]{-1,3,4,8,-1,0,5,6,7,-1,13,12,11,14,9,10,15}:new int[]{0,1,2,6,-1,-1,3,4,5,-1,11,10,9,12,7,8,13};
                for(int c=0;c<values.length;c++) if(map[c]>=0)line.getCell(map[c],org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(values[c]);
            }
            book.write(out);return out.toByteArray();
        }
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    @Test void previewDoesNotWriteStockAndEachNewRowCreatesItsOwnFood() throws Exception {
        var p=bulk.preview(file(row("두부","2","묶음A",""),row("두부","1","묶음A",""),row("두부","0.5","","")),owner);
        assertThat(p.valid()).isTrue();assertThat(count("food_item")).isZero();
        assertThat(bulk.commit(p.requestId(),owner,false)).isEqualTo(3);
        assertThat(count("food_master")).isEqualTo(3);assertThat(count("food_history")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT sum(quantity_amount) FROM food_item",java.math.BigDecimal.class)).isEqualByComparingTo("3.5");
    }
    @Test void automaticFreezeNoticesDistinguishMissingSelectionFromDeliveryOverride() throws Exception {
        var missing=row("두부","1","","");missing[3]="냉동실";
        var deliveryMissing=row("잔반","1","","");deliveryMissing[3]="";deliveryMissing[7]="배달 잔반";
        var deliverySelected=row("잔반2","1","","");deliverySelected[3]="냉동실";deliverySelected[7]="배달 잔반";deliverySelected[15]="시판 냉동식품";
        var preview=bulk.preview(file(missing,deliveryMissing,deliverySelected),owner);
        assertThat(preview.valid()).isTrue();
        assertThat(preview.rows().get(0).notices()).containsExactly("냉동 구분이 없으면 자동으로 ‘직접 냉동’으로 설정돼.");
        assertThat(preview.rows().get(1).notices()).containsExactly(
                "출처가 ‘배달 잔반’이라 보관 위치가 자동으로 '냉동실'로 설정되었어.",
                "냉동 구분이 없으면 자동으로 ‘직접 냉동’으로 설정돼.");
        assertThat(preview.rows().get(2).notices()).containsExactly("출처가 ‘배달 잔반’이면 무조건 ‘직접 냉동’으로 설정돼.");
        assertThat(preview.rows()).allSatisfy(r -> {
            assertThat(r.form().storageType()).isEqualTo(com.euiseon.friger.common.type.StorageType.FREEZER);
            assertThat(r.form().freezeType()).isEqualTo(com.euiseon.friger.common.type.FreezeType.HOME_FROZEN);
        });
    }
    @Test void blankRequiredFieldsAreGroupedWithoutQuantityFormatError() throws Exception {
        var values=row("","","","");values[2]="";values[3]="";
        var preview=bulk.preview(file(values),owner);
        assertThat(preview.rows().getFirst().errors()).containsExactly("누락된 필수 정보: 음식명, 수량, 단위, 보관 위치");
        assertThat(preview.valid()).isFalse();assertThat(count("food_bulk_preview")).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.001","1000000000","abc"})
    void invalidQuantityIsNotAlsoReportedAsMissing(String amount) throws Exception {
        var values=row("",amount,"","");values[2]="";values[3]="";
        var preview=bulk.preview(file(values),owner);
        assertThat(preview.rows().getFirst().errors()).containsExactly(
                "수량은 0보다 큰 숫자를 소수 둘째 자리까지 입력해야해.","누락된 필수 정보: 음식명, 단위, 보관 위치");
    }
    @Test void missingAdditionalSelectionDoesNotReportFoodNameAsMissing() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template()));var out=new ByteArrayOutputStream()) {
            book.getSheet("추가 등록").getRow(4).getCell(3).setCellValue(-1);
            book.write(out);var preview=bulk.preview(out.toByteArray(),owner);
            assertThat(preview.rows().getFirst().errors()).containsExactly(
                    "수량은 0보다 큰 숫자를 소수 둘째 자리까지 입력해야해.",
                    "기존 음식명 선택 드롭다운에서 음식을 선택해줘.","누락된 필수 정보: 단위, 보관 위치");
        }
    }
    @Test void invalidStorageIsNotReportedAsMissing() throws Exception {
        var values=row("두부","1","","");values[3]="알수없음";
        var preview=bulk.preview(file(values),owner);
        assertThat(preview.rows().getFirst().errors()).containsExactly("보관 위치: 양식의 선택 목록을 사용해줘.");
    }
    @Test void invalidChoicesDoNotProduceDependentErrorsOrAutomaticNotices() throws Exception {
        var storage=row("잔반","1","","");storage[3]="오타";storage[7]="배달 잔반";storage[15]="직접 냉동";
        var source=row("두부","1","","");source[7]="오타";source[8]="가게";
        var freeze=row("냉동 두부","1","","");freeze[3]="냉동실";freeze[15]="오타";
        var preview=bulk.preview(file(storage,source,freeze),owner);
        assertThat(preview.rows().get(0).errors()).containsExactly("보관 위치: 양식의 선택 목록을 사용해줘.");
        assertThat(preview.rows().get(1).errors()).containsExactly("출처: 양식의 선택 목록을 사용해줘.");
        assertThat(preview.rows().get(2).errors()).containsExactly("냉동 구분: 양식의 선택 목록을 사용해줘.");
        assertThat(preview.rows()).allSatisfy(r -> assertThat(r.notices()).isEmpty());
        assertThat(preview.valid()).isFalse();
        assertThat(count("food_bulk_preview")).isZero();
    }
    @Test void missingStorageDoesNotClaimThatFoodIsOutsideFreezer() throws Exception {
        var values=row("두부","1","","");values[3]="";values[15]="직접 냉동";
        var preview=bulk.preview(file(values),owner);
        assertThat(preview.rows().getFirst().errors()).containsExactly("누락된 필수 정보: 보관 위치");
        assertThat(preview.rows().getFirst().issues()).anySatisfy(i -> {
            assertThat(i.type()).isEqualTo(BulkValidation.Type.MISSING);
            assertThat(i.field()).isEqualTo("storageType");
        });
    }
    @Test void formulaQuantityHasOneActionableErrorAndStillBlocksRegistration() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(file(row("두부","1","",""))));var out=new ByteArrayOutputStream()) {
            book.getSheet("신규 등록").getRow(4).getCell(1).setCellFormula("1+1");
            book.write(out);
            var preview=bulk.preview(out.toByteArray(),owner);
            assertThat(preview.rows().getFirst().errors()).containsExactly("수량: 수식, 오류, 참/거짓 대신 값을 입력해줘.");
            assertThat(preview.valid()).isFalse();
            assertThat(count("food_bulk_preview")).isZero();
        }
    }
    @Test void independentConditionErrorsRemainExplanationsAndMissingFieldsComeLast() throws Exception {
        var values=row("","1","","");values[15]="직접 냉동";values[8]="가게";
        var preview=bulk.preview(file(values),owner);
        assertThat(preview.rows().getFirst().errors()).containsExactly(
                "냉동 정보: 냉동실이 아닌 행의 냉동일, 냉동 구분을 비워줘.",
                "출처 메모: 출처가 기타일 때만 입력해줘.", "누락된 필수 정보: 음식명");
        assertThat(preview.rows().getFirst().issues()).filteredOn(i -> i.type()==BulkValidation.Type.CONDITION).hasSize(2);
    }
    @Test void previewFromPreviousFormatRequestsUploadAgainWithoutWritingStock() throws Exception {
        var preview=bulk.preview(file(row("두부","1","","")),owner);
        jdbc.update("UPDATE food_bulk_preview SET payload=replace(payload, '\"issues\":', '\"oldIssues\":') WHERE request_id=?",preview.requestId());
        assertThatThrownBy(() -> bulk.commit(preview.requestId(),owner,false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("미리보기 형식이 바뀌었어. 파일을 다시 올려 확인해줘.");
        assertThat(count("food_item")).isZero();
        assertThat(count("food_bulk_receipt")).isZero();
    }
    @Test void oneInvalidRowBlocksWholeFileAndHasNoUsableToken() throws Exception {
        var p=bulk.preview(file(row("두부","1","",""),row("두부","-1","","")),owner);
        assertThat(p.valid()).isFalse();assertThat(p.rows().get(1).errors()).isNotEmpty();
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("food_item")).isZero();assertThat(count("food_bulk_preview")).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.001","1000000000","1e2","abc"})
    void rejectsInvalidQuantity(String amount) throws Exception { assertThat(bulk.preview(file(row("두부",amount,"","")),owner).valid()).isFalse(); }
    @Test void preservesNumericDecimalAndExcelDate() throws Exception {
        byte[] bytes;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(file(row("두부","1","",""))));var out=new ByteArrayOutputStream()) {
            var row=book.getSheet("신규 등록").getRow(4);row.getCell(1).setCellValue(0.75);
            row.getCell(11).setCellValue(java.time.LocalDate.of(2026,1,1));book.write(out);bytes=out.toByteArray();
        }
        var p=bulk.preview(bytes,owner);assertThat(p.valid()).isTrue();
        assertThat(p.rows().getFirst().form().quantityAmount()).isEqualByComparingTo("0.75");
        assertThat(p.rows().getFirst().form().purchasedAt()).isEqualTo(java.time.LocalDate.of(2026,1,1));
    }
    @Test void rejectsFormulaEvenWhenCachedValueLooksValid() throws Exception {
        byte[] bytes;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(file(row("두부","1","",""))));var out=new ByteArrayOutputStream()) {
            book.getSheet("신규 등록").getRow(4).getCell(1).setCellFormula("1+1");book.write(out);bytes=out.toByteArray();
        }
        assertThat(bulk.preview(bytes,owner).valid()).isFalse();
    }
    @Test void oldCombinedSheetRequiresFreshTemplate() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(file(row("두부","1","",""))));var out=new ByteArrayOutputStream()) {
            book.setSheetName(0,"입력");book.write(out);
            assertThatThrownBy(()->workbook.read(out.toByteArray())).hasMessageContaining("양식이 변경됐어");
        }
    }
    @Test void duplicatePreviewRendersUncheckedConfirmationAndDisabledCommit() throws Exception {
        var bytes=file(row("두부","1","",""));
        var first=bulk.preview(bytes,owner);bulk.commit(first.requestId(),owner,false);
        var session=new MockHttpSession();mvc.perform(get("/inventory/bulk").session(session));
        var html=mvc.perform(multipart("/inventory/bulk/preview")
                .file(new MockMultipartFile("file","duplicate.xlsx","application/octet-stream",bytes))
                .session(session).param("formToken",session.getAttribute("bulkOwner").toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("같은 내용이 이미 등록된 적 있어서 확인이 필요해.","새로 구매한 거라서 새 항목으로 추가하는 게 맞아!")
                .doesNotContain("확인 중인 파일:","확인 필요 항목:","확인한 내용을 저장하려면 등록 완료를 눌러줘.")
                .containsPattern("id=\"bulkComplete\"[^>]*disabled")
                .containsPattern("id=\"bulkRepeat\"[^>]*required");
    }
    @Test void repeatedExistingMasterRowsCreateItemsWithoutIncrementingOldBalance() throws Exception {
        var first=bulk.preview(file(row("두부","1","","")),owner);bulk.commit(first.requestId(),owner,false);
        long master=masters.all().getFirst().masterId();
        var p=bulk.preview(file(row("","2","",""+master),row("두부","3","",""+master)),owner);
        assertThat(p.valid()).isTrue();bulk.commit(p.requestId(),owner,false);
        assertThat(count("food_master")).isEqualTo(1);assertThat(count("food_item")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT quantity_amount FROM food_item ORDER BY food_id",java.math.BigDecimal.class)).usingComparatorForType(java.math.BigDecimal::compareTo,java.math.BigDecimal.class).containsExactly(new java.math.BigDecimal("1"),new java.math.BigDecimal("2"),new java.math.BigDecimal("3"));
    }
    @Test void staleTargetRejectsAllRowsAndReceipt() throws Exception {
        var first=bulk.preview(file(row("두부","1","","")),owner);bulk.commit(first.requestId(),owner,false);
        long master=masters.all().getFirst().masterId();
        var p=bulk.preview(file(row("새 음식","1","",""),row("두부","2","",""+master)),owner);
        masters.touch(master);
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("food_item")).isEqualTo(1);assertThat(count("food_bulk_receipt")).isEqualTo(1);
    }
    @Test void databaseFailureAfterFirstInsertRollsBackEverything() throws Exception {
        var p=bulk.preview(file(row("첫 음식","1","",""),row("실패 음식","1","","")),owner);
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_bulk() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF (SELECT food_name FROM food_master WHERE master_id=NEW.master_id)='실패 음식' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_bulk BEFORE INSERT ON food_item FOR EACH ROW EXECUTE FUNCTION reject_bulk()");
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(count("food_item")).isZero();assertThat(count("food_master")).isZero();assertThat(count("food_history")).isZero();assertThat(count("food_bulk_receipt")).isZero();
    }
    @Test void sameRequestAndSameContentAreIdempotentButExplicitRepeatCanAdd() throws Exception {
        var bytes=file(row("두부","1","",""));var p=bulk.preview(bytes,owner);
        bulk.commit(p.requestId(),owner,false);bulk.commit(p.requestId(),owner,true);
        assertThat(count("food_item")).isEqualTo(1);
        var again=bulk.preview(bytes,owner);assertThat(again.duplicate()).isTrue();
        assertThatThrownBy(()->bulk.commit(again.requestId(),owner,false)).isInstanceOf(IllegalArgumentException.class);
        bulk.commit(again.requestId(),owner,true);bulk.commit(again.requestId(),owner,true);
        assertThat(count("food_item")).isEqualTo(2);
    }
    @Test void sharedNameChangeDoesNotBypassDuplicateDetection() throws Exception {
        var first=bulk.preview(file(row("두부","1","","")),owner);bulk.commit(first.requestId(),owner,false);
        long id=masters.all().getFirst().masterId();
        byte[] bytes=file(row("","2","",""+id));
        var p=bulk.preview(bytes,owner);bulk.commit(p.requestId(),owner,false);
        masters.update(id,"이름 정정",null);
        assertThat(bulk.preview(bytes,owner).duplicate()).isTrue();
    }
    @Test void simultaneousDuplicateUploadsOnlyCommitOnce() throws Exception {
        var bytes=file(row("두부","1","",""));var a=bulk.preview(bytes,owner);var b=bulk.preview(bytes,owner);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks=new ArrayList<Future<Boolean>>();
            for(var p:List.of(a,b))tasks.add(pool.submit(()->{start.await();try{bulk.commit(p.requestId(),owner,true);return true;}catch(IllegalArgumentException e){return false;}}));
            start.countDown();int successes=0;for(var task:tasks)if(task.get(20,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(count("food_item")).isEqualTo(1);
    }
    @Test void rejectsWrongSessionAndExpiredPreview() throws Exception {
        var p=bulk.preview(file(row("두부","1","","")),owner);
        assertThatThrownBy(()->bulk.commit(p.requestId(),UUID.randomUUID(),false)).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("UPDATE food_bulk_preview SET expires_at='2000-01-01' WHERE request_id=?",p.requestId());
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("food_item")).isZero();
    }
    @Test void pagesDownloadPreviewAndCommitThroughSession() throws Exception {
        var session=new MockHttpSession();
        mvc.perform(get("/inventory/bulk").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("엑셀 양식 받기")));
        mvc.perform(get("/inventory/bulk/template")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",containsString(".xlsx")));
        var upload=new MockMultipartFile("file","foods.xlsx","application/octet-stream",file(row("<script>두부</script>","1","","")));
        var result=mvc.perform(multipart("/inventory/bulk/preview").file(upload).session(session).param("formToken",session.getAttribute("bulkOwner").toString()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;두부&lt;/script&gt;"))).andReturn();
        var preview=(BulkRegistrationService.Preview)result.getModelAndView().getModel().get("preview");
        mvc.perform(post("/inventory/bulk/commit").session(session).param("requestId",preview.requestId().toString()))
            .andExpect(redirectedUrl("/inventory")).andExpect(flash().attribute("successMessage","1개 항목을 등록했어!"));
    }
    @Test void rejectsEmptyWrongHeadersAndOversizedFiles() throws Exception {
        assertThatThrownBy(()->workbook.read(workbook.template())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->workbook.read(new byte[BulkWorkbook.MAX_BYTES+1])).isInstanceOf(IllegalArgumentException.class);
        byte[] bytes;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(file(row("두부","1","",""))));var out=new ByteArrayOutputStream()) {book.getSheet("신규 등록").getRow(3).getCell(0).setCellValue("changed");book.write(out);bytes=out.toByteArray();}
        assertThatThrownBy(()->workbook.read(bytes)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMoreThan500Rows() throws Exception {
        String[][] rows=new String[501][];for(int i=0;i<501;i++)rows[i]=row("두부","1","","");
        var bytes=file(rows);
        assertThatThrownBy(()->workbook.read(bytes)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500");
    }
    @Test void previewErrorsRenderAndCrossSessionTokenIsRejected() throws Exception {
        var session=new MockHttpSession();mvc.perform(get("/inventory/bulk").session(session));
        var upload=new MockMultipartFile("file","foods.xlsx","application/octet-stream",file(row("두부","-1","","")));
        mvc.perform(multipart("/inventory/bulk/preview").file(upload).session(session).param("formToken",session.getAttribute("bulkOwner").toString()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("오류가 있어.")));
        mvc.perform(multipart("/inventory/bulk/preview").file(upload).session(session).param("formToken",UUID.randomUUID().toString()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("파일을 다시 선택해줘")));
        assertThat(count("food_item")).isZero();
    }

    @Test void workbookListsDuplicateNamesSeparatelyAndCalculatesTheChosenId() throws Exception {
        var first=bulk.preview(file(row("두부","1","",""),row("두부","1","","")),owner);bulk.commit(first.requestId(),owner,false);
        var choices=masters.registrationChoices();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(choices)));var output=new ByteArrayOutputStream()) {
            assertThat(book.getSheet("기존 음식").getLastRowNum()).isEqualTo(2);
            assertThat(book.getSheet("기존 음식").getRow(1).getCell(0).getStringCellValue()).isNotEqualTo(book.getSheet("기존 음식").getRow(2).getCell(0).getStringCellValue());
            var sheet=book.getSheet("추가 등록");var row=sheet.getRow(4);
            row.getCell(3).setCellValue(2);row.getCell(4).setCellValue("모");row.getCell(8).setCellValue("냉장실");
            row.getCell(0).setCellValue(book.getSheet("기존 음식").getRow(2).getCell(0).getStringCellValue());
            assertThat(sheet.getDataValidations()).anySatisfy(v->assertThat(v.getValidationConstraint().getFormula1()).isEqualTo("ExistingFoodNames"));
            assertThat(book.getCreationHelper().createFormulaEvaluator().evaluate(row.getCell(1)).getStringValue()).isEqualTo(Long.toString(choices.get(1).masterId()));
            assertThat(book.getCreationHelper().createFormulaEvaluator().evaluate(row.getCell(2)).getStringValue()).isEmpty();
            book.write(output);var preview=bulk.preview(output.toByteArray(),owner);
            assertThat(preview.valid()).isTrue();assertThat(preview.rows()).hasSize(1);assertThat(preview.rows().getFirst().masterId()).isEqualTo(choices.get(1).masterId());
        }
    }
    @Test void forgedDisplayNumberAndFormulaAreRejected() throws Exception {
        var p=bulk.preview(file(row("두부","1","","")),owner);bulk.commit(p.requestId(),owner,false);
        var id=masters.all().getFirst().masterId();
        for(boolean formula:List.of(false,true)) {
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(file(row("두부","1","",""+id))));var output=new ByteArrayOutputStream()) {
                var number=book.getSheet("추가 등록").getRow(4).getCell(1);
                if(formula)number.setCellFormula("1+1");else {number.removeFormula();number.setCellValue("999999");}
                book.write(output);assertThat(bulk.preview(output.toByteArray(),owner).valid()).isFalse();
            }
        }
    }
    @Test void oneFileOnlyIsEnforcedByTheServerAndFooterIsAlwaysPresent() throws Exception {
        var session=new MockHttpSession();
        var initial=mvc.perform(get("/inventory/bulk").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(initial).contains("등록 완료","disabled").doesNotContain("기존 음식 번호 확인하기", ">미리보기</button>", "파일을 올리고 오류 없는 미리보기를 확인하면 등록할 수 있어.");
        var bytes=file(row("두부","1","",""));
        var a=new MockMultipartFile("file","a.xlsx","application/octet-stream",bytes);
        var b=new MockMultipartFile("file","b.xlsx","application/octet-stream",bytes);
        mvc.perform(multipart("/inventory/bulk/preview").file(a).file(b).session(session).param("formToken",session.getAttribute("bulkOwner").toString()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("엑셀 파일은 1개만 선택해줘.")))
            .andExpect(result->{String html=result.getResponse().getContentAsString();assertThat(html.indexOf("엑셀 파일은 1개만 선택해줘.")).isBetween(html.indexOf("id=\"bulkFile\""),html.indexOf("id=\"bulkResults\""));});
        assertThat(count("food_bulk_preview")).isZero();assertThat(count("food_item")).isZero();
        var valid=mvc.perform(multipart("/inventory/bulk/preview").file(a).session(session).param("formToken",session.getAttribute("bulkOwner").toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(valid).contains("id=\"bulkComplete\"").doesNotContain("확인 중인 파일:", "확인 필요 항목:");
    }
    @Test void combinedLimitAndRowsAfterPrepared100AreEnforced() throws Exception {
        var first=bulk.preview(file(row("기존","1","","")),owner);bulk.commit(first.requestId(),owner,false);
        long id=masters.all().getFirst().masterId();
        String[][] rows=new String[500][];
        for(int i=0;i<250;i++)rows[i]=row("신규"+i,"1","","");
        for(int i=250;i<500;i++)rows[i]=row("","1","",""+id);
        var p=bulk.preview(file(rows),owner);
        assertThat(p.valid()).isTrue();assertThat(p.rows()).hasSize(500);
        assertThat(p.rows().get(499).sheet()).isEqualTo("추가 등록");assertThat(p.rows().get(499).row()).isEqualTo(254);
        var overflow=Arrays.copyOf(rows,501);overflow[500]=row("초과","1","","");
        assertThatThrownBy(()->workbook.read(file(overflow))).hasMessageContaining("두 시트 합계 최대 500");
    }
    @Test void missingExistingSelectionNeverBecomesNewRegistration() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template()));var out=new ByteArrayOutputStream()) {
            var row=book.getSheet("추가 등록").getRow(4);row.getCell(3).setCellValue(1);row.getCell(4).setCellValue("개");row.getCell(8).setCellValue("실온");
            book.write(out);var p=bulk.preview(out.toByteArray(),owner);
            assertThat(p.valid()).isFalse();assertThat(p.rows().getFirst().errors()).anyMatch(e->e.contains("기존 음식명 선택"));assertThat(count("food_bulk_preview")).isZero();
        }
    }
    @Test void dropdownsAreLimitedToTheirColumnsAnd100Rows() throws Exception {
        var p=bulk.preview(file(row("두부","1","","")),owner);bulk.commit(p.requestId(),owner,false);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(masters.registrationChoices())))) {
            assertThat(book.getName("ExistingFoodNames").getRefersToFormula()).isEqualTo("'기존 음식'!$A$2:$A$2");
            assertThat(book.getName("ExistingFoodLookup").getRefersToFormula()).isEqualTo("'기존 음식'!$A$2:$C$2");
            var s=book.getSheet("추가 등록");
            assertThat(s.getDataValidations()).anySatisfy(v->{assertThat(v.getValidationConstraint().getFormula1()).isEqualTo("ExistingFoodNames");assertThat(v.getRegions().getCellRangeAddress(0).formatAsString()).isEqualTo("A5:A104");});
            for(var sheet:List.of(book.getSheet("신규 등록"),s)) for(var v:sheet.getDataValidations()) {
                var region=v.getRegions().getCellRangeAddress(0);
                assertThat(region.getFirstColumn()).isEqualTo(region.getLastColumn());assertThat(region.getLastRow()).isEqualTo(103);
            }
        }
    }

    @Test void downloadPreservesTemplateLayoutAndClearsSampleInputs() throws Exception {
        try(var source=new org.springframework.core.io.ClassPathResource("excel/frizer-bulk-template.xlsx").getInputStream();
            var original=new XSSFWorkbook(source);
            var result=new XSSFWorkbook(new ByteArrayInputStream(workbook.template()))) {
            for(String name:List.of("신규 등록","추가 등록")) {
                var a=original.getSheet(name);var b=result.getSheet(name);
                assertThat(b.getMergedRegions()).isEqualTo(a.getMergedRegions());
                for(int c=0;c<a.getRow(3).getLastCellNum();c++) {
                    assertThat(b.getColumnWidth(c)).isEqualTo(a.getColumnWidth(c));
                    assertThat(b.getRow(3).getCell(c).getCellStyle().getIndex()).isEqualTo(a.getRow(3).getCell(c).getCellStyle().getIndex());
                }
                assertThat(b.getRow(4).getHeight()).isEqualTo(a.getRow(4).getHeight());
                assertThat(b.getRow(4).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            }
            assertThat(result.getSheet("추가 등록").getRow(4).getCell(2).getCellFormula()).contains("VLOOKUP(A5,ExistingFoodLookup,3,FALSE)");
            assertThat((Object)result.getSheet("기존 음식").getRow(1)).isNull();
            var originalGuide=original.getSheet("안내");var guide=result.getSheet("안내");
            assertThat(guide.getMergedRegions()).isEqualTo(originalGuide.getMergedRegions());
            assertThat(guide.getColumnWidth(0)).isEqualTo(originalGuide.getColumnWidth(0));
            for(var row:originalGuide) {
                assertThat(guide.getRow(row.getRowNum()).getHeight()).isEqualTo(row.getHeight());
                for(var c:row) {
                    var actual=guide.getRow(row.getRowNum()).getCell(c.getColumnIndex());
                    assertThat(actual.toString()).isEqualTo(c.toString());
                    assertThat(actual.getCellStyle().getIndex()).isEqualTo(c.getCellStyle().getIndex());
                }
            }

        }
    }

}
