package com.euiseon.friger.inventory;

import java.io.*;
import java.util.*;
import java.math.BigDecimal;
import com.euiseon.friger.inventory.bulk.*;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.common.type.StorageType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Testcontainers
class FoodCategoryBulkIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:18.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired BulkWorkbook workbook; @Autowired BulkRegistrationService bulk;
    @Autowired FoodMasterDao masters; @Autowired InventoryService inventory; @Autowired JdbcTemplate jdbc;
    final UUID owner=UUID.randomUUID();
    record Row(Long master,String major,String minor) {}
    @BeforeEach void reset() {
        for(String table:List.of("food_bulk_receipt","food_bulk_preview","food_history","food_item","food_master")) jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE food_category_major SET active=true");
    }
    long legacy() {
        long item=inventory.create(new FoodCreateForm("기존 음식",StorageType.FRIDGE,"예전 값",BigDecimal.ONE,null,null,null,null,null,null,false,null,null,null,null,"개"));
        return masters.masterIdForItem(item);
    }
    byte[] file(Row... rows) throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(masters.registrationChoices())));var out=new ByteArrayOutputStream()) {
            int fresh=4,additional=4;
            for(var row:rows) {
                boolean adding=row.master()!=null;var line=book.getSheet(adding?"추가 등록":"신규 등록").getRow(adding?additional++:fresh++);
                if(adding) {var master=masters.find(row.master());line.getCell(3).setCellValue(BulkWorkbook.choiceLabel(master.masterId(),master.foodName()));}
                else line.getCell(0).setCellValue("새 음식 "+fresh);
                line.getCell(adding?4:3).setCellValue(1);line.getCell(adding?5:4).setCellValue("개");line.getCell(adding?9:8).setCellValue("냉장실");
                if(row.major()!=null){line.getCell(1).setBlank();line.getCell(1).setCellValue(row.major());}
                if(row.minor()!=null){line.getCell(2).setBlank();line.getCell(2).setCellValue(row.minor());}
            }
            book.write(out);return out.toByteArray();
        }
    }
    @Test void newRowsRequireValidParentChildAndSaveCreateCategorySnapshot() throws Exception {
        for(var row:List.of(new Row(null,null,null),new Row(null,"콩·두부",null),new Row(null,"콩·두부","수입")))
            assertThat(bulk.preview(file(row),owner).valid()).isFalse();
        var p=bulk.preview(file(new Row(null,"콩·두부","두부")),owner);
        assertThat(p.valid()).isTrue();bulk.commit(p.requestId(),owner,false);
        assertThat(jdbc.queryForObject("SELECT category_after->>'minor_code' FROM food_history",String.class)).isEqualTo("soy_tofu");
        assertThat(jdbc.queryForObject("SELECT action_type FROM food_history",String.class)).isEqualTo("CREATE");
    }
    @Test void previewSeparatesCategoryValidationAndIgnoresInapplicableMinor() throws Exception {
        var missing=bulk.preview(file(new Row(null,"","")),owner).rows().getFirst();
        assertThat(missing.errors()).contains("누락된 필수 정보: 대분류");
        assertThat(missing.issues()).noneMatch(i->i.field().equals("categoryMinorCode"));
        var child=bulk.preview(file(new Row(null,"콩·두부","")),owner).rows().getFirst();
        assertThat(child.errors()).contains("누락된 필수 정보: 중분류");
        assertThat(child.categoryMajorLabel()).isEqualTo("콩·두부");
        assertThat(child.categoryMinorRequired()).isTrue();
        var invalid=bulk.preview(file(new Row(null,"콩·두부","수입")),owner).rows().getFirst();
        assertThat(invalid.issues()).anyMatch(i->i.field().equals("categoryMinorCode"));
        byte[] bytes=file(new Row(null,"음료","남아 있는 중분류"));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes));var out=new ByteArrayOutputStream()) {
            book.getSheet("신규 등록").getRow(4).getCell(2).setCellFormula("1+1");
            book.write(out);bytes=out.toByteArray();
        }
        var preview=bulk.preview(bytes,owner);
        assertThat(preview.valid()).isTrue();
        var row=preview.rows().getFirst();
        assertThat(row.categoryMajorLabel()).isEqualTo("음료");
        assertThat(row.categoryMinorCode()).isNull();
        assertThat(row.categoryMinorRequired()).isFalse();
        bulk.commit(preview.requestId(),owner,false);
        assertThat(jdbc.queryForObject("SELECT category_minor_code FROM food_master",String.class)).isNull();
    }
    @Test void staleTargetVersionRejectsWholeFile() throws Exception {
        long id=legacy();masters.assignCategory(id,"beverage",null,"음료");var p=bulk.preview(file(new Row(null,"음료",null),new Row(id,null,null)),owner);
        masters.touch(id);
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_bulk_receipt",Integer.class)).isZero();
    }
    @Test void categoryRetiredAfterPreviewRollsBackEarlierRowsAndReceipt() throws Exception {
        var p=bulk.preview(file(new Row(null,"음료",null),new Row(null,"주류",null)),owner);
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='alcohol'");
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_bulk_receipt",Integer.class)).isZero();
    }
    @Test void blankInheritsRetiredCategoryButEnteredLabelsAreValidated() throws Exception {
        long id=legacy();masters.assignCategory(id,"kimchi",null,"김치");
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='kimchi'");
        assertThat(bulk.preview(file(new Row(id,"없는 분류","없는 중분류")),owner).valid()).isFalse();
        var p=bulk.preview(file(new Row(id,null,null)),owner);
        assertThat(p.valid()).isTrue();bulk.commit(p.requestId(),owner,false);
        assertThat(masters.find(id).categoryMajorCode()).isEqualTo("kimchi");
    }
    @Test void unfinishedOldPreviewIsRejectedButCompletedOneStillReplays() throws Exception {
        var p=bulk.preview(file(new Row(null,"음료",null)),owner);
        assertThat(p.categoryContract()).isEqualTo("v3");
        jdbc.update("UPDATE food_bulk_preview SET payload=jsonb_set(payload::jsonb,'{categoryContract}',to_jsonb('v2'::text))::text WHERE request_id=?",p.requestId());
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).hasMessageContaining("분류 선택");
        jdbc.update("UPDATE food_bulk_preview SET payload=(payload::jsonb-'categoryContract')::text WHERE request_id=?",p.requestId());
        assertThatThrownBy(()->bulk.commit(p.requestId(),owner,false)).hasMessageContaining("분류 선택");
        var current=bulk.preview(file(new Row(null,"음료",null)),owner);bulk.commit(current.requestId(),owner,false);
        jdbc.update("UPDATE food_bulk_preview SET payload=(payload::jsonb-'categoryContract')::text WHERE request_id=?",current.requestId());
        assertThat(bulk.commit(current.requestId(),owner,false)).isEqualTo(1);
    }
    @Test void generatedTemplateKeepsInputPositionsAndProvidesCatalog() throws Exception {
        byte[] bytes=workbook.template(List.of());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheet("신규 등록").getRow(3).getCell(1).getStringCellValue()).isEqualTo("대분류");
            assertThat(book.getSheet("추가 등록").getRow(3).getCell(2).getStringCellValue()).isEqualTo("기존 중분류");
            assertThat(book.getSheet("분류 기준").getLastRowNum()).isGreaterThan(70);
        }
        var folder=java.nio.file.Path.of("build/reports/category-workbook");java.nio.file.Files.createDirectories(folder);
        java.nio.file.Files.write(folder.resolve("template.xlsx"),bytes);
    }
    @Test void existingFoodListUsesAllRowsWithUniqueGlobalNamesAndOpensFreshSheet() throws Exception {
        var choices=new ArrayList<FoodMasterDao.RegistrationChoice>();
        for(int i=1;i<=120;i++) choices.add(new FoodMasterDao.RegistrationChoice(i,"같은 음식", "유제품·대체품 › 식물성 대체품",0,"개",1,"dairy","dairy_plant"));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(choices)))) {
            assertThat(book.getSheet("기존 음식").getRow(120).getCell(0).getStringCellValue()).isEqualTo("같은 음식");
            try(var source=new org.springframework.core.io.ClassPathResource("excel/frizer-bulk-template.xlsx").getInputStream();var template=new XSSFWorkbook(source)) {
                assertThat(book.getSheet("기존 음식").getColumnWidth(0)).isEqualTo(template.getSheet("기존 음식").getColumnWidth(0));
                var original=template.getSheet("기존 음식");
                var sourceCell=original.getRow(1)==null?null:original.getRow(1).getCell(0);
                var sourceStyle=sourceCell==null?original.getColumnStyle(0):sourceCell.getCellStyle();
                assertThat(book.getSheet("기존 음식").getRow(120).getCell(0).getCellStyle().getAlignment()).isEqualTo(sourceStyle.getAlignment());
            }
            for(String key:List.of("ExistingFoodNames","ExistingFoodLookup")) {
                var names=book.getNames(key);assertThat(names).hasSize(1);
                assertThat(names.getFirst().getSheetIndex()).isEqualTo(-1);
                assertThat(names.getFirst().getRefersToFormula()).endsWith("$121");
            }
            var row=book.getSheet("추가 등록").getRow(4);row.getCell(3).setCellValue("같은 음식 [#120]");
            assertThat(book.getCreationHelper().createFormulaEvaluator().evaluate(row.getCell(0)).getStringValue()).isEqualTo("120");
            assertThat(book.getSheetName(book.getActiveSheetIndex())).isEqualTo("신규 등록");
            for(var sheet:book) assertThat(sheet.isSelected()).isEqualTo(sheet.getSheetName().equals("신규 등록"));
            assertThat(book.isSheetHidden(book.getSheetIndex("음식 선택값"))).isTrue();
        }
    }
    @Test void selectionPrefillsBothCategoryLevelsAndUploadsWithoutCachedFormulaValues() throws Exception {
        long id=legacy();masters.assignCategory(id,"soy","soy_tofu","콩·두부 › 두부");
        byte[] bytes=file(new Row(id,null,null));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var existing=book.getSheet("기존 음식");
            assertThat(existing.getRow(0).getCell(2).getStringCellValue()).isEqualTo("대분류");
            assertThat(existing.getRow(0).getCell(3).getStringCellValue()).isEqualTo("중분류");
            assertThat(existing.getRow(1).getCell(2).getStringCellValue()).isEqualTo("콩·두부");
            assertThat(existing.getRow(1).getCell(3).getStringCellValue()).isEqualTo("두부");
            var evaluator=book.getCreationHelper().createFormulaEvaluator();var row=book.getSheet("추가 등록").getRow(4);
            assertThat(evaluator.evaluate(row.getCell(1)).getStringValue()).isEqualTo("콩·두부");
            assertThat(evaluator.evaluate(row.getCell(2)).getStringValue()).isEqualTo("두부");
            // Empty input rows must not show numeric zero or become upload entries.
            assertThat(evaluator.evaluate(book.getSheet("추가 등록").getRow(5).getCell(2)).getStringValue()).isEmpty();
        }
        var preview=bulk.preview(bytes,owner);assertThat(preview.valid()).isTrue();assertThat(preview.rows()).hasSize(1);
        assertThat(preview.rows().getFirst().categoryMinorCode()).isEqualTo("soy_tofu");
        bulk.commit(preview.requestId(),owner,false);
        var changed=bulk.preview(file(new Row(id,"음료","")),owner);assertThat(changed.valid()).isFalse();
        assertThat(masters.find(id).categoryMajorCode()).isEqualTo("soy");
        assertThat(masters.find(id).categoryMinorCode()).isEqualTo("soy_tofu");
    }
    @Test void categoryFormulaDoesNotTrustEditedLookupAndRejectsArbitraryFormulas() throws Exception {
        long id=legacy();masters.assignCategory(id,"soy","soy_tofu","콩·두부 › 두부");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(file(new Row(id,null,null))));var out=new ByteArrayOutputStream()) {
            book.getSheet("음식 선택값").getRow(1).getCell(3).setCellValue("주류");book.write(out);
            var preview=bulk.preview(out.toByteArray(),owner);assertThat(preview.valid()).isTrue();assertThat(preview.rows().getFirst().categoryMajorCode()).isEqualTo("soy");
            book.getSheet("추가 등록").getRow(4).getCell(2).setCellFormula("\"주류\"");out.reset();book.write(out);
            assertThat(bulk.preview(out.toByteArray(),owner).valid()).isFalse();
        }
    }
    @Test void existingNamesSortHangulThenCaseInsensitiveLatinThenNumbersAndSymbols() throws Exception {
        var choices=new ArrayList<FoodMasterDao.RegistrationChoice>();
        String[] names={"[브랜드] 라면","banana","나물","Apple","가래떡","apple","123음식","#음식"};
        for(int i=0;i<names.length;i++)choices.add(new FoodMasterDao.RegistrationChoice(i+1,names[i],null,0,"개",1,null,null));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(choices)))) {
            var actual=new ArrayList<String>();for(int i=1;i<=names.length;i++)actual.add(book.getSheet("기존 음식").getRow(i).getCell(0).getStringCellValue());
            assertThat(actual.subList(0,6)).containsExactly("가래떡","나물","Apple","apple","banana","123음식");
            assertThat(actual.subList(6,8)).containsExactlyInAnyOrder("#음식","[브랜드] 라면");
            for(int i=1;i<=names.length;i++)assertThat(book.getSheet("음식 선택값").getRow(i).getCell(0).getStringCellValue()).startsWith(actual.get(i-1)+" [#");
        }
    }
    @Test void additionalRegistrationKeepsCategoryAndRejectsOverrides() throws Exception {
        long id=legacy();
        assertThat(bulk.preview(file(new Row(id,"음료",null)),owner).valid()).isFalse();
        masters.assignCategory(id,"beverage",null,"음료");
        jdbc.update("UPDATE food_category_major SET active=false WHERE code='beverage'");
        var original=jdbc.queryForMap("SELECT * FROM food_history");
        var preview=bulk.preview(file(new Row(id,null,null),new Row(id,null,null)),owner);
        assertThat(preview.valid()).isTrue();
        bulk.commit(preview.requestId(),owner,false);
        assertThat(masters.find(id).categoryMajorCode()).isEqualTo("beverage");
        assertThat(jdbc.queryForMap("SELECT * FROM food_history ORDER BY history_id LIMIT 1")).isEqualTo(original);
        assertThat(bulk.preview(file(new Row(id,"주류",null)),owner).valid()).isFalse();
        var leaf=bulk.preview(file(new Row(id,null,"두부")),owner);
        assertThat(leaf.valid()).isTrue();
        assertThat(leaf.rows().getFirst().categoryMinorCode()).isNull();
    }
    @Test void unclassifiedAdditionalRegistrationPreservesCategoryAndReplaysWithoutDuplicates() throws Exception {
        long id=legacy();
        var original=jdbc.queryForMap("SELECT * FROM food_history");
        byte[] bytes=file(new Row(id,null,null),new Row(id,null,null));
        var preview=bulk.preview(bytes,owner);
        assertThat(preview.valid()).isTrue();
        assertThat(preview.rows()).allSatisfy(row->assertThat(row.categoryMajorCode()).isNull());
        assertThat(bulk.commit(preview.requestId(),owner,false)).isEqualTo(2);
        assertThat(bulk.commit(preview.requestId(),owner,false)).isEqualTo(2);
        assertThat(masters.find(id).categoryMajorCode()).isNull();
        assertThat(masters.find(id).category()).isEqualTo("예전 값");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForMap("SELECT * FROM food_history ORDER BY history_id LIMIT 1")).isEqualTo(original);
        assertThat(bulk.preview(bytes,owner).duplicate()).isTrue();
    }
    @Test void additionalSelectionRefreshesProtectedCategoryCells() throws Exception {
        long a=legacy();masters.assignCategory(a,"beverage",null,"음료");
        long b=legacy();masters.assignCategory(b,"soy","soy_tofu","콩·두부 › 두부");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(masters.registrationChoices())))) {
            var sheet=book.getSheet("추가 등록");var row=sheet.getRow(4);
            assertThat(sheet.getProtect()).isTrue();
            for(int c=0;c<3;c++)assertThat(row.getCell(c).getCellStyle().getLocked()).isTrue();
            for(int c=3;c<18;c++)assertThat(row.getCell(c).getCellStyle().getLocked()).isFalse();
            assertThat(sheet.getDataValidations()).noneMatch(v->v.getRegions().getCellRangeAddress(0).getFirstColumn()<3);
            var evaluator=book.getCreationHelper().createFormulaEvaluator();
            row.getCell(3).setCellValue(BulkWorkbook.choiceLabel(a,masters.find(a).foodName()));
            assertThat(evaluator.evaluate(row.getCell(1)).getStringValue()).isEqualTo("음료");
            row.getCell(3).setCellValue(BulkWorkbook.choiceLabel(b,masters.find(b).foodName()));
            evaluator.clearAllCachedResultValues();
            assertThat(evaluator.evaluate(row.getCell(1)).getStringValue()).isEqualTo("콩·두부");
            assertThat(evaluator.evaluate(row.getCell(2)).getStringValue()).isEqualTo("두부");
        }
    }
    @Test void categoryEditCannotBypassDuplicateConfirmation() throws Exception {
        long id=legacy();masters.assignCategory(id,"beverage",null,"음료");
        byte[] bytes=file(new Row(null,"콩·두부","두부"),new Row(id,null,null));
        var first=bulk.preview(bytes,owner);bulk.commit(first.requestId(),owner,false);
        masters.assignCategory(id,"alcohol",null,"주류");masters.touch(id);
        var replay=bulk.preview(bytes,owner);
        assertThat(replay.valid()).isTrue();assertThat(replay.duplicate()).isTrue();
        assertThat(replay.rows().get(1).categoryMajorCode()).isEqualTo("alcohol");
        assertThatThrownBy(()->bulk.commit(replay.requestId(),owner,false)).hasMessageContaining("중복 등록 안내");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_item",Integer.class)).isEqualTo(3);
        assertThat(bulk.preview(file(new Row(null,"음료",null),new Row(id,null,null)),owner).duplicate()).isFalse();
        assertThat(bulk.commit(replay.requestId(),owner,true)).isEqualTo(2);
    }
    @Test void deployedReceiptStillDetectsMixedNewAndAdditionalUpload() throws Exception {
        long id=legacy();masters.assignCategory(id,"beverage",null,"음료");
        byte[] bytes=file(new Row(null,"콩·두부","두부"),new Row(id,null,null));
        var preview=bulk.preview(bytes,owner);bulk.commit(preview.requestId(),owner,false);
        // The deployed format stored no new-food category and omitted shared identity for additions.
        var oldRows=preview.rows().stream().map(r->Arrays.asList(r.masterId()==null
                ?r.form().withIdentity(r.form().foodName(),null):r.form().withIdentity("",null),r.group(),r.masterId())).toList();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String oldHash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(oldRows)));
        jdbc.update("UPDATE food_bulk_receipt SET fingerprint=? WHERE request_id=?",oldHash,preview.requestId());
        assertThat(bulk.preview(bytes,owner).duplicate()).isTrue();
    }
    @Test void dependentDropdownEvaluatesOnlySelectedParentAtFirstAndLastRows() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.template(List.of())))) {
            for(boolean adding:List.of(false)) {
                var sheet=book.getSheet(adding?"추가 등록":"신규 등록");
                int parent=1,child=parent+1;
                var rule=sheet.getDataValidations().stream().filter(v->v.getRegions().getCellRangeAddress(0).getFirstColumn()==child).findFirst().orElseThrow();
                String formula=rule.getValidationConstraint().getFormula1();
                assertThat(formula).contains("$"+(adding?"C":"B")+"5");
                assertThat(rule.getRegions().getCellRangeAddress(0).getLastRow()).isEqualTo(503);
                var evaluator=book.getCreationHelper().createFormulaEvaluator();
                for(int row:List.of(4,503)) for(String label:List.of("콩·두부","음료","","존재하지 않음")) {
                    sheet.getRow(row).getCell(parent).setBlank();
                    sheet.getRow(row).getCell(parent).setCellValue(label);
                    var check=sheet.getRow(row).createCell(25);
                    check.setCellFormula("COUNTIF("+formula.replace("$"+(adding?"C":"B")+"5","$"+(adding?"C":"B")+(row+1))+",\"두부\")");
                    evaluator.clearAllCachedResultValues();
                    assertThat(evaluator.evaluate(check).getNumberValue()).isEqualTo(label.equals("콩·두부")?1:0);
                }
            }
            assertThat(book.getName("CategoryMinorNames")).isNull();
            assertThat(book.isSheetHidden(book.getSheetIndex("분류 선택값"))).isTrue();
        }
    }
}
