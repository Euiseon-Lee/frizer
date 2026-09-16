package com.euiseon.friger.inventory.bulk;

import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import static com.euiseon.friger.inventory.bulk.BulkValidation.Type.*;

@Component
public class BulkWorkbook {
    public static final int MAX_ROWS = 500;
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final List<String> HEADERS = List.of("음식명", "수량", "단위", "보관 위치", "음식 묶음", "기존 음식 선택", "용량", "출처", "출처 메모", "분류", "구매일", "소비기한", "유통기한", "개봉일", "냉동일", "냉동 구분", "메모", "기존 음식 번호 (자동)");
    public static final List<String> NEW_HEADERS = List.of("음식명", "수량", "단위", "용량", "출처", "출처 메모", "보관 위치", "냉동일", "냉동 구분", "유통기한", "소비기한", "구매일", "개봉일", "메모");
    public static final List<String> ADD_HEADERS = List.of("기존 음식명 선택", "음식 번호", "분류", "수량", "단위", "용량", "출처", "출처 메모", "보관 위치", "냉동일", "냉동 구분", "유통기한", "소비기한", "구매일", "개봉일", "메모");
    // Normalize both layouts to the same internal field order used by validation and previews.
    private static final int[] NEW_MAP = {0,1,2,6,-1,-1,3,4,5,-1,11,10,9,12,7,8,13};
    private static final int[] ADD_MAP = {-1,3,4,8,-1,0,5,6,7,-1,13,12,11,14,9,10,15};
    private static final List<String> FIELDS = List.of("foodName", "quantityAmount", "quantityUnit", "storageType", "group", "masterId", "capacityText", "sourceType", "sourceMemo", "category", "purchasedAt", "expiredAt", "sellByAt", "openedAt", "frozenAt", "freezeType", "memo");
    public record Entry(int row, List<String> cells, FoodCreateForm form, String group, Long masterId,
                        Long version, List<BulkValidation.Issue> issues, String sheet) {
        public Entry { issues = List.copyOf(issues); }
        public List<String> errors() { return BulkValidation.errors(issues); }
        public List<String> notices() { return issues.stream().filter(i -> i.type() == AUTOMATIC).map(BulkValidation.Issue::message).toList(); }
    }

    public List<Entry> read(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("2MB 이하의 .xlsx 파일을 선택해줘.");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (book.isMacroEnabled()) throw new IllegalArgumentException("매크로가 없는 .xlsx 양식을 사용해줘.");
            if (book.getSheet("입력") != null) throw new IllegalArgumentException("양식이 변경됐어. 앱에서 신규 등록, 추가 등록 시트가 있는 양식을 다시 받아줘.");
            var entries = new ArrayList<Entry>();
            for (String sheetName : List.of("신규 등록", "추가 등록")) {
                var sheet = book.getSheet(sheetName);
                if (sheet == null) throw new IllegalArgumentException("‘" + sheetName + "’ 시트가 없어. 앱에서 양식을 다시 받아줘.");
                boolean adding = sheetName.equals("추가 등록");
                var headers = adding ? ADD_HEADERS : NEW_HEADERS;
                int[] map = adding ? ADD_MAP : NEW_MAP;
                var header = sheet.getRow(3);
                for (int c=0; c<headers.size(); c++)
                    if (header == null || !headers.get(c).equals(text(header.getCell(c))))
                        throw new IllegalArgumentException(sheetName + " 시트 4행의 열 이름과 순서를 유지해줘. 앱에서 양식을 다시 받을 수 있어.");
                if (sheet.getLastRowNum() > 10003) throw new IllegalArgumentException("불필요한 빈 행을 지우고 두 시트 합계 기준 최대 500개 항목만 업로드해줘.");
                for (int i=4; i<=sheet.getLastRowNum(); i++) {
                    var row=sheet.getRow(i);
                    if (row==null) continue;
                    boolean blank=true;
                    for(var cell:row) if (!(adding && (cell.getColumnIndex()==1 || cell.getColumnIndex()==2)) && !text(cell).isBlank()) {blank=false;break;}
                    if(blank) continue;
                    if(entries.size()>=MAX_ROWS) throw new IllegalArgumentException("신규 등록과 추가 등록 두 시트 합계 최대 500개 항목을 등록할 수 있어.");
                    var values=new ArrayList<String>();
                    var errors=new BulkValidation();
                    for(int c=0;c<map.length;c++) {
                        var cell=map[c]<0?null:row.getCell(map[c]);
                        values.add(text(cell));
                        if(cell!=null && (cell.getCellType()==CellType.FORMULA || cell.getCellType()==CellType.ERROR || cell.getCellType()==CellType.BOOLEAN))
                            errors.add(INVALID,FIELDS.get(c),HEADERS.get(c)+": 수식, 오류, 참/거짓 대신 값을 입력해줘.");
                        if(values.get(c).length()>1000) errors.add(INVALID,FIELDS.get(c),HEADERS.get(c)+": 입력 내용이 너무 길어.");
                    }
                    for(var cell:row) if(cell.getColumnIndex()>=headers.size() && !text(cell).isBlank()) errors.add(INVALID,"columns","양식에 없는 열에 값이 있어. 양식에 표시된 열만 입력해줘.");
                    BigDecimal quantity=null;
                    Long master=null;
                    if (!values.get(1).isBlank()) try {
                        String q=values.get(1);
                        if(!q.matches("[0-9]{1,9}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
                        quantity=new BigDecimal(q);
                        if (quantity.signum() <= 0) throw new IllegalArgumentException();
                    } catch(IllegalArgumentException e) {quantity=null;errors.add(INVALID,"quantityAmount","수량은 0보다 큰 숫자를 소수 둘째 자리까지 입력해야해.");}
                    if(adding) {
                        try {
                            var selected=java.util.regex.Pattern.compile("(?s)^.+ \\[#([0-9]+)\\]$").matcher(values.get(5));
                            if(!selected.matches()) throw new IllegalArgumentException();
                            master=Long.valueOf(selected.group(1));
                            if(master<=0) throw new IllegalArgumentException();
                        } catch(IllegalArgumentException e) {errors.add(INVALID,"masterId","기존 음식명 선택 드롭다운에서 음식을 선택해줘.");}
                        var number=row.getCell(1);
                        if(number!=null && number.getCellType()==CellType.FORMULA) {
                            if(!lookupFormula(i+1,2).equals(number.getCellFormula())) errors.add(INVALID,"automaticId","자동 번호 수식이 변경됐어. 새 양식에 내용을 옮겨줘.");
                        } else if(master!=null && number!=null && !text(number).isBlank() && !master.toString().equals(text(number))) errors.add(CONDITION,"automaticId","자동 번호가 선택한 음식과 달라. 기존 음식을 다시 선택해줘.");
                        var category=row.getCell(2);
                        if(category!=null && category.getCellType()==CellType.FORMULA) {
                            if(!lookupFormula(i+1,3).equals(category.getCellFormula())) errors.add(INVALID,"category","자동 분류 수식이 변경됐어. 새 양식에 내용을 복사해서 사용해야해.");
                        } else if(category!=null) {
                            if(category.getCellType()==CellType.ERROR || category.getCellType()==CellType.BOOLEAN) errors.add(INVALID,"category","분류: 자동 입력 양식을 사용해줘.");
                            values.set(9,text(category));
                        }
                    }
                    StorageType storage = choice(values.get(3), Map.of("실온", StorageType.ROOM, "냉장실", StorageType.FRIDGE, "냉동실", StorageType.FREEZER), "storageType", errors);
                    FoodSourceType source = choice(values.get(7), Map.of("장보기", FoodSourceType.PURCHASE, "배달 잔반", FoodSourceType.DELIVERY_LEFTOVER, "직접 조리", FoodSourceType.COOKED, "부모님", FoodSourceType.PARENTS, "부모님의 은혜", FoodSourceType.PARENTS, "기타", FoodSourceType.ETC), "sourceType", errors);
                    FreezeType freeze = choice(values.get(15), Map.of("직접 냉동", FreezeType.HOME_FROZEN, "시판 냉동식품", FreezeType.COMMERCIAL_FROZEN), "freezeType", errors);
                    var dates = new LocalDate[5];
                    for (int d = 0; d < 5; d++) dates[d] = date(map[d+10] < 0 ? null : row.getCell(map[d+10]), values.get(d+10), FIELDS.get(d+10), errors);
                    var form = new FoodCreateForm(values.get(0), storage, optional(values.get(9)), quantity, dates[1], dates[0], dates[3], dates[4], source, freeze, false,
                            optional(values.get(16)), optional(values.get(6)), optional(values.get(8)), dates[2], values.get(2));
                    entries.add(new Entry(i+1, values, form, "", master, null, errors.issues(), sheet.getSheetName()));
            }
            }
            if(entries.isEmpty()) throw new IllegalArgumentException("엑셀 파일 내 등록할 음식이 없어.");
            return entries;
        } catch(IOException | org.apache.poi.ooxml.POIXMLException e) {
            throw new IllegalArgumentException("파일을 읽지 못했어. .xlsx 양식으로 다시 저장해줘.");
        }
    }
    private static String text(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().strip();
            case NUMERIC -> NumberToTextConverter.toText(c.getNumericCellValue());
            case BLANK -> "";
            case FORMULA -> "수식";
            default -> c.toString().strip();
        };
    }
    private static String optional(String s) { return s.isBlank() ? null : s; }
    private static <T> T choice(String s, Map<String,T> values, String field, BulkValidation errors) {
        if (s.isEmpty()) return null;
        T v = values.get(s);
        if (v == null) errors.add(INVALID, field, BulkValidation.label(field) + ": 양식의 선택 목록을 사용해줘.");
        return v;
    }
    private static LocalDate date(Cell cell, String s, String field, BulkValidation errors) {
        if (s.isBlank()) return null;
        try {
            LocalDate date;
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) date = cell.getLocalDateTimeCellValue().toLocalDate();
            else {
                if (!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
                date = LocalDate.parse(s);
            }
            if (date.getYear() < 1900 || date.getYear() > 9999) throw new IllegalArgumentException();
            return date;
        } catch (RuntimeException e) { errors.add(INVALID, field, BulkValidation.label(field) + ": yyyy-mm-dd 형식의 날짜를 입력해줘."); return null; }
    }

    public static String choiceLabel(long id, String name, String category) {
        return name + (category == null || category.isBlank() ? "" : " · " + category) + " [#" + id + "]";
    }
    private static String lookupFormula(int row, int column) {
        return "IF(A"+row+"=\"\",\"\",IFERROR(VLOOKUP(A"+row+",ExistingFoodLookup,"+column+",FALSE),\"\"))";
    }
    public byte[] template() throws IOException { return template(List.of()); }
    public byte[] template(List<FoodMasterDao.RegistrationChoice> foods) throws IOException {
        try(var source=new org.springframework.core.io.ClassPathResource("excel/frizer-bulk-template.xlsx").getInputStream();
            var book=new XSSFWorkbook(source);var output=new ByteArrayOutputStream()) {
            var fresh=book.getSheet("신규 등록");
            var additional=book.getSheet("추가 등록");
            var existing=book.getSheet("기존 음식");
            if(fresh==null || additional==null || existing==null || book.getSheet("안내")==null)
                throw new IOException("기본 엑셀 양식에 필요한 시트가 없습니다.");
            // Keep the user's workbook formatting; replace only dynamic data and validation rules.
            var existingStyles=new CellStyle[3];
            for(int c=0;c<3;c++) existingStyles[c]=cell(existing,1,c).getCellStyle();
            for(int r=existing.getLastRowNum();r>=1;r--) if(existing.getRow(r)!=null) existing.removeRow(existing.getRow(r));
            var wrapped=book.createCellStyle();wrapped.cloneStyleFrom(existingStyles[0]);wrapped.setWrapText(true);wrapped.setVerticalAlignment(VerticalAlignment.CENTER);
            for(int i=0;i<foods.size();i++) {
                var food=foods.get(i);var row=existing.createRow(i+1);
                String label=choiceLabel(food.masterId(),food.foodName(),food.category());
                row.createCell(0).setCellValue(label);row.getCell(0).setCellStyle(wrapped);
                row.createCell(1).setCellValue(Long.toString(food.masterId()));row.getCell(1).setCellStyle(existingStyles[1]);row.createCell(2).setCellValue(food.category()==null?"":food.category());row.getCell(2).setCellStyle(existingStyles[2]);
            }
            if(foods.isEmpty()) {existing.createRow(3).createCell(0).setCellValue("아직 등록된 음식이 없어. 신규 등록 시트를 사용해줘.");}
            int last=Math.max(2,foods.size()+1);
            var names=book.getName("ExistingFoodNames");if(names==null){names=book.createName();names.setNameName("ExistingFoodNames");}names.setRefersToFormula("'기존 음식'!$A$2:$A$"+last);
            var lookup=book.getName("ExistingFoodLookup");if(lookup==null){lookup=book.createName();lookup.setNameName("ExistingFoodLookup");}lookup.setRefersToFormula("'기존 음식'!$A$2:$C$"+last);
            for(var sheet:List.of(fresh,additional)) {
                boolean adding=sheet==additional;
                var headers=adding?ADD_HEADERS:NEW_HEADERS;
                for(int c=0;c<headers.size();c++) if(!headers.get(c).equals(text(cell(sheet,3,c))))
                    throw new IOException("기본 엑셀 양식의 열 이름과 업로드 규칙이 다릅니다: "+sheet.getSheetName());
                cell(sheet,0,0).setCellValue(sheet.getSheetName());
                for(var row:sheet) if(row.getRowNum()>=4) for(var value:row) value.setBlank();
                for(int r=4;r<104;r++) {
                    for(int c=0;c<headers.size();c++) cell(sheet,r,c);
                    if(adding) for(int c=1;c<=2;c++) cell(sheet,r,c).setCellFormula(lookupFormula(r+1,c+1));
                }
                // Reset the sample's overlapping list ranges before applying per-column rules.
                if(sheet.getCTWorksheet().isSetDataValidations()) sheet.getCTWorksheet().unsetDataValidations();
                dropdown(sheet,adding?8:6,new String[]{"실온","냉장실","냉동실"});
                dropdown(sheet,adding?6:4,new String[]{"장보기","배달 잔반","직접 조리","부모님","기타"});
                dropdown(sheet,adding?10:8,new String[]{"직접 냉동","시판 냉동식품"});
                var helper=sheet.getDataValidationHelper();
                String q=adding?"D5":"B5";
                var rule=helper.createValidation(helper.createCustomConstraint("AND(ISNUMBER("+q+"),"+q+">0,"+q+"<=999999999.99,ROUND("+q+",2)="+q+")"),new CellRangeAddressList(4,103,adding?3:1,adding?3:1));
                rule.setShowErrorBox(true);rule.setErrorStyle(DataValidation.ErrorStyle.STOP);rule.createErrorBox("수량 확인","0보다 큰 숫자를 소수 둘째 자리까지 입력해줘.");sheet.addValidationData(rule);
            }
            if(!foods.isEmpty()) {
                var helper=additional.getDataValidationHelper();
                var selector=helper.createValidation(helper.createFormulaListConstraint("ExistingFoodNames"),new CellRangeAddressList(4,103,0,0));
                selector.setShowErrorBox(true);selector.setErrorStyle(DataValidation.ErrorStyle.STOP);selector.createErrorBox("음식 선택","목록에 있는 음식을 선택해줘.");additional.addValidationData(selector);
            }
            // The guide is authored in the source workbook; preserve its content and formatting.
            book.setForceFormulaRecalculation(true);book.write(output);return output.toByteArray();
        }
    }
    private static Cell cell(Sheet sheet,int row,int column) {
        var line=sheet.getRow(row);if(line==null)line=sheet.createRow(row);
        return line.getCell(column,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
    }
    private static void dropdown(Sheet sheet,int column,String[] values) {
        var helper=sheet.getDataValidationHelper();
        var validation=helper.createValidation(helper.createExplicitListConstraint(values),new CellRangeAddressList(4,103,column,column));
        validation.setShowErrorBox(true);validation.setErrorStyle(DataValidation.ErrorStyle.STOP);validation.createErrorBox("입력 확인","선택 목록의 값을 사용해줘.");sheet.addValidationData(validation);
    }
}
