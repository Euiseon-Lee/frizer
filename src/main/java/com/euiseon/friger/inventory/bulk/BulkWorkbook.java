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
    private final com.euiseon.friger.inventory.service.FoodCategoryService categories;
    public BulkWorkbook(com.euiseon.friger.inventory.service.FoodCategoryService categories) { this.categories=categories; }
    public static final int MAX_ROWS = 500;
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final List<String> HEADERS = List.of("음식명", "수량", "단위", "보관 위치", "음식 묶음", "기존 음식 선택", "용량", "출처", "출처 메모", "분류", "구매일", "소비기한", "유통기한", "개봉일", "냉동일", "냉동 구분", "메모", "기존 음식 번호 (자동)");
    private static final List<String> LEGACY_NEW_HEADERS = List.of("음식명", "수량", "단위", "용량", "출처", "출처 메모", "보관 위치", "냉동일", "냉동 구분", "유통기한", "소비기한", "구매일", "개봉일", "메모");
    private static final List<String> LEGACY_ADD_HEADERS = List.of("기존 음식명 선택", "음식 번호", "분류", "수량", "단위", "용량", "출처", "출처 메모", "보관 위치", "냉동일", "냉동 구분", "유통기한", "소비기한", "구매일", "개봉일", "메모");
    public static final List<String> NEW_HEADERS = List.of("음식명", "수량", "단위", "용량", "출처", "출처 메모", "보관 위치", "냉동일", "냉동 구분", "유통기한", "소비기한", "구매일", "개봉일", "개봉 상태", "메모");
    public static final List<String> ADD_HEADERS = List.of("음식 번호", "분류", "기존 음식명 선택", "수량", "단위", "용량", "출처", "출처 메모", "보관 위치", "냉동일", "냉동 구분", "유통기한", "소비기한", "구매일", "개봉일", "개봉 상태", "메모");
    public static List<String> categoryHeaders(boolean adding) {
        if(adding) return List.of("음식 번호","기존 대분류","기존 중분류","기존 음식명 선택","수량","단위","용량","출처","출처 메모","보관 위치","냉동일","냉동 구분","유통기한","소비기한","구매일","개봉일","개봉 상태","메모");
        var result=new ArrayList<>(NEW_HEADERS);
        int position=result.indexOf("음식명")+1;
        result.addAll(position,List.of("대분류","중분류"));return List.copyOf(result);
    }
    private static List<String> headers(Sheet sheet, boolean adding) {
        for (var candidate : List.of(categoryHeaders(adding), adding ? ADD_HEADERS : NEW_HEADERS, adding ? LEGACY_ADD_HEADERS : LEGACY_NEW_HEADERS)) {
            var row=sheet.getRow(3);boolean matches=row!=null;
            for(int c=0;matches && c<candidate.size();c++) matches=candidate.get(c).equals(text(row.getCell(c)));
            if(matches) return candidate;
        }
        throw new IllegalArgumentException(sheet.getSheetName()+" 시트 4행의 열 이름과 순서를 유지해줘. 앱에서 양식을 다시 받을 수 있어.");
    }
    // Normalize both layouts to the same internal field order used by validation and previews.
    private static final List<String> FIELDS = List.of("foodName", "quantityAmount", "quantityUnit", "storageType", "group", "masterId", "capacityText", "sourceType", "sourceMemo", "category", "purchasedAt", "expiredAt", "sellByAt", "openedAt", "frozenAt", "freezeType", "memo");
    public record Entry(int row, List<String> cells, FoodCreateForm form, String group, Long masterId,
                        Long version, List<BulkValidation.Issue> issues, String sheet, String categoryMajorCode, String categoryMinorCode,
                        String categoryMajorLabel, String categoryMinorLabel, boolean categoryMinorRequired) {
        public Entry(int row, List<String> cells, FoodCreateForm form, String group, Long masterId, Long version,
                     List<BulkValidation.Issue> issues, String sheet, String major, String minor) {
            this(row,cells,form,group,masterId,version,issues,sheet,major,minor,null,null,false);
        }
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
                var headers = headers(sheet,adding);
                int[] map=new int[FIELDS.size()];
                for(int c=0;c<map.length;c++) map[c]=headers.indexOf(c==5?"기존 음식명 선택":HEADERS.get(c));
                if(adding) map[9]=-1; // Automatic category formulas are validated separately.
                int selectionColumn=headers.indexOf("기존 음식명 선택"), idColumn=headers.indexOf("음식 번호"), categoryColumn=headers.indexOf("분류");
                if (sheet.getLastRowNum() > 10003) throw new IllegalArgumentException("불필요한 빈 행을 지우고 두 시트 합계 기준 최대 500개 항목만 업로드해줘.");
                for (int i=4; i<=sheet.getLastRowNum(); i++) {
                    var row=sheet.getRow(i);
                    if (row==null) continue;
                    boolean blank=true;
                    for(var cell:row) if (!(adding && (cell.getColumnIndex()==idColumn || cell.getColumnIndex()==categoryColumn
                            || automaticCategoryFormula(cell,headers,i+1,selectionColumn))) && !text(cell).isBlank()) {blank=false;break;}
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
                    } catch(IllegalArgumentException e) {quantity=null;errors.add(INVALID,"quantityAmount","수량은 0보다 큰 숫자를 소수 둘째 자리까지 입력해야 해.");}
                    if(adding) {
                        try {
                            var selected=java.util.regex.Pattern.compile("(?s)^.+ \\[#([0-9]+)\\]$").matcher(values.get(5));
                            if(!selected.matches()) throw new IllegalArgumentException();
                            master=Long.valueOf(selected.group(1));
                            if(master<=0) throw new IllegalArgumentException();
                        } catch(IllegalArgumentException e) {errors.add(INVALID,"masterId","기존 음식명 선택 드롭다운에서 음식을 선택해줘.");}
                        var number=row.getCell(idColumn);
                        if(number!=null && number.getCellType()==CellType.FORMULA) {
                            if(!lookupFormula(i+1,2,selectionColumn).equals(number.getCellFormula())) errors.add(INVALID,"automaticId","자동 번호 수식이 변경됐어. 새 양식에 내용을 옮겨줘.");
                        } else if(master!=null && number!=null && !text(number).isBlank() && !master.toString().equals(text(number))) errors.add(CONDITION,"automaticId","자동 번호가 선택한 음식과 달라. 기존 음식을 다시 선택해줘.");
                        var category=categoryColumn<0?null:row.getCell(categoryColumn);
                        if(category!=null && category.getCellType()==CellType.FORMULA) {
                            if(!lookupFormula(i+1,3,selectionColumn).equals(category.getCellFormula())) errors.add(INVALID,"category","자동 분류 수식이 변경됐어. 새 양식에 내용을 복사해서 사용해야 해.");
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
                    int openingColumn=headers.indexOf("개봉 상태");
                    Cell openingCell=openingColumn<0?null:row.getCell(openingColumn);
                    String openingText=text(openingCell);
                    OpeningStatus opening=choice(openingText,Map.of("해당 없음",OpeningStatus.NOT_APPLICABLE,"미개봉",OpeningStatus.UNOPENED,"개봉함",OpeningStatus.OPENED,"불확실",OpeningStatus.UNKNOWN),"openingStatus",errors);
                    if(opening==null) opening=dates[3]==null?OpeningStatus.UNOPENED:OpeningStatus.OPENED;
                    if(dates[3]!=null && opening!=OpeningStatus.OPENED)
                        errors.add(CONDITION,"openedAt","개봉일: 개봉 상태를 ‘개봉함’으로 바꾸거나 날짜를 비워줘.");
                    if(opening==OpeningStatus.OPENED && dates[3]==null)
                        errors.add(AUTOMATIC,"openingStatus","개봉일 불확실로 저장하고, 등록한 날을 개봉 상태 확인일로 기록할게.");
                    var form = new FoodCreateForm(values.get(0), storage, optional(values.get(9)), quantity, dates[1], dates[0], dates[3], dates[4], source, freeze, false,
                            optional(values.get(16)), optional(values.get(6)), optional(values.get(8)), dates[2], values.get(2), false, null, false, opening);
                    // Raw labels are resolved against the active catalog by the preview service.
                    String major=categoryValue(row,headers,"대분류",adding,selectionColumn,errors);
                    String minor=categoryValue(row,headers,"중분류",adding,selectionColumn,errors);
                    entries.add(new Entry(i+1, values, form, "", master, null, errors.issues(), sheet.getSheetName(),major,minor));
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
    private static boolean automaticCategoryFormula(Cell cell,List<String> headers,int row,int selectionColumn) {
        if(cell.getCellType()!=CellType.FORMULA) return false;
        for(String field:List.of("대분류","중분류","기존 대분류","기존 중분류")) if(cell.getColumnIndex()==headers.indexOf(field))
            return lookupFormula(row,field.endsWith("대분류")?4:5,selectionColumn).equals(cell.getCellFormula());
        return false;
    }
    private static String categoryValue(Row row,List<String> headers,String field,boolean adding,int selectionColumn,BulkValidation errors) {
        int column=headers.indexOf(adding && headers.contains("기존 "+field)?"기존 "+field:field);Cell value=column<0?null:row.getCell(column);
        // Null marks an untouched generated formula. Resolve from the owned master on the server;
        // never trust cached Excel values or the editable hidden lookup sheet.
        if(value!=null && adding && automaticCategoryFormula(value,headers,row.getRowNum()+1,selectionColumn)) return null;
        if(value!=null && value.getCellType()!=CellType.STRING && value.getCellType()!=CellType.BLANK)
            errors.add(INVALID,field.equals("대분류")?"categoryMajorCode":"categoryMinorCode",field+(adding?": 자동 표시 칸이 변경됐어. 새 양식을 받아줘.":": 선택 목록의 이름을 입력해줘."));
        return text(value);
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

    public static String choiceLabel(long id, String name) {
        return name + " [#" + id + "]";
    }
    public static String legacyChoiceLabel(long id, String name, String category) {
        return name + (category == null || category.isBlank() ? "" : " · " + category) + " [#" + id + "]";
    }
    private static String lookupFormula(int row, int column, int selectionColumn) {
        String selection=org.apache.poi.ss.util.CellReference.convertNumToColString(selectionColumn)+row;
        String lookup="VLOOKUP("+selection+",ExistingFoodLookup,"+column+",FALSE)";
        if(column>=4) lookup="T("+lookup+")";
        return "IF("+selection+"=\"\",\"\",IFERROR("+lookup+",\"\"))";
    }
    public byte[] template(List<FoodMasterDao.RegistrationChoice> foods) throws IOException {
        var collator=java.text.Collator.getInstance(Locale.KOREAN);collator.setStrength(java.text.Collator.PRIMARY);
        foods=foods.stream().sorted(Comparator.comparingInt((FoodMasterDao.RegistrationChoice food)->nameGroup(food.foodName()))
                .thenComparing(FoodMasterDao.RegistrationChoice::foodName,collator)
                .thenComparingLong(FoodMasterDao.RegistrationChoice::masterId)).toList();
        try(var source=new org.springframework.core.io.ClassPathResource("excel/frizer-bulk-template.xlsx").getInputStream();
            var book=new XSSFWorkbook(source);var output=new ByteArrayOutputStream()) {
            var fresh=book.getSheet("신규 등록");
            var additional=book.getSheet("추가 등록");
            var existing=book.getSheet("기존 음식");
            if(fresh==null || additional==null || existing==null || book.getSheet("안내")==null)
                throw new IOException("기본 엑셀 양식에 필요한 시트가 없습니다.");
            var catalog=categories.choices();
            var storedCatalog=categories.storedChoices();
            // Template copies can contain sheet-scoped names that shadow workbook names.
            // Recreate every generated name once, at workbook scope.
            for(var name:new ArrayList<>(book.getAllNames())) {
                String key=name.getNameName();
                if(key.equalsIgnoreCase("ExistingFoodNames") || key.equalsIgnoreCase("ExistingFoodLookup")
                        || key.startsWith("CategoryChildren_") || Set.of("CategoryEmpty","CategoryMajorNames","CategoryMajorLookup","CategoryMinorNames").contains(key))
                    book.removeName(name);
            }
            var categorySheet=book.getSheet("분류 기준");
            if(categorySheet==null) categorySheet=book.createSheet("분류 기준");
            for(var row:categorySheet) for(var value:row) value.setBlank();
            cell(categorySheet,0,0).setCellValue("대분류");cell(categorySheet,0,1).setCellValue("중분류 (해당하는 대분류에서만 선택)");
            categorySheet.setColumnWidth(0,24*256);categorySheet.setColumnWidth(1,42*256);
            int categoryRow=1;
            for(var major:catalog) {
                if(major.requiresMinor()) for(var minor:major.minors()) {
                    cell(categorySheet,categoryRow,0).setCellValue(major.label());cell(categorySheet,categoryRow++,1).setCellValue(minor.label());
                } else {cell(categorySheet,categoryRow,0).setCellValue(major.label());cell(categorySheet,categoryRow++,1).setCellValue("중분류 없음");}
            }
            // Lookup labels to safe range names; labels can contain spaces and punctuation.
            var categoryValues=book.getSheet("분류 선택값");
            if(categoryValues==null) categoryValues=book.createSheet("분류 선택값");
            for(var row:categoryValues) for(var value:row) value.setBlank();
            defineName(book,"CategoryEmpty","'분류 선택값'!$C$1");
            for(int i=0;i<catalog.size();i++) {
                var major=catalog.get(i);String rangeName="CategoryChildren_"+i;
                cell(categoryValues,i,0).setCellValue(major.label());cell(categoryValues,i,1).setCellValue(rangeName);
                int column=i+3;
                for(int r=0;r<major.minors().size();r++) cell(categoryValues,r,column).setCellValue(major.minors().get(r).label());
                String letter=org.apache.poi.ss.util.CellReference.convertNumToColString(column);
                defineName(book,rangeName,"'분류 선택값'!$"+letter+"$1:$"+letter+"$"+Math.max(1,major.minors().size()));
            }
            defineName(book,"CategoryMajorNames","'분류 선택값'!$A$1:$A$"+Math.max(1,catalog.size()));
            defineName(book,"CategoryMajorLookup","'분류 선택값'!$A$1:$B$"+Math.max(1,catalog.size()));
            if(book.getName("CategoryMinorNames")!=null) book.removeName(book.getName("CategoryMinorNames"));
            book.setSheetHidden(book.getSheetIndex(categoryValues),true);
            // Keep the user's workbook formatting; replace only dynamic data and validation rules.
            var existingStyles=new CellStyle[3];
            for(int c=0;c<3;c++) {
                var sample=existing.getRow(1)==null?null:existing.getRow(1).getCell(c);
                existingStyles[c]=sample==null?existing.getColumnStyle(c):sample.getCellStyle();
                if(existingStyles[c]==null) existingStyles[c]=book.getCellStyleAt(0);
            }
            for(int r=existing.getLastRowNum();r>=1;r--) if(existing.getRow(r)!=null) existing.removeRow(existing.getRow(r));
            var wrapped=book.createCellStyle();wrapped.cloneStyleFrom(existingStyles[0]);wrapped.setWrapText(true);wrapped.setVerticalAlignment(VerticalAlignment.CENTER);
            cell(existing,0,2).setCellValue("대분류");cell(existing,0,3).setCellValue("중분류");
            cell(existing,0,3).setCellStyle(cell(existing,0,2).getCellStyle());existing.setColumnWidth(3,existing.getColumnWidth(2));
            var foodValues=book.getSheet("음식 선택값");
            if(foodValues==null) foodValues=book.createSheet("음식 선택값");
            for(var row:foodValues) for(var value:row) value.setBlank();
            for(int i=0;i<foods.size();i++) {
                var food=foods.get(i);var row=existing.createRow(i+1);
                String label=choiceLabel(food.masterId(),food.foodName());
                var major=storedCatalog.stream().filter(c->c.code().equals(food.categoryMajorCode())).findFirst().orElse(null);
                String majorLabel=major==null?"":major.label();
                String minorLabel=major==null?"":major.minors().stream().filter(c->c.code().equals(food.categoryMinorCode())).map(c->c.label()).findFirst().orElse("");
                row.createCell(0).setCellValue(food.foodName());row.getCell(0).setCellStyle(wrapped);
                row.createCell(1).setCellValue(Long.toString(food.masterId()));row.getCell(1).setCellStyle(existingStyles[1]);row.createCell(2).setCellValue(majorLabel);row.getCell(2).setCellStyle(existingStyles[2]);
                row.createCell(3).setCellValue(minorLabel);row.getCell(3).setCellStyle(existingStyles[2]);
                cell(foodValues,i+1,0).setCellValue(label);cell(foodValues,i+1,1).setCellValue(Long.toString(food.masterId()));cell(foodValues,i+1,2).setCellValue(food.category()==null?"":food.category());
                cell(foodValues,i+1,3).setCellValue(majorLabel);cell(foodValues,i+1,4).setCellValue(minorLabel);
            }
            if(foods.isEmpty()) {existing.createRow(3).createCell(0).setCellValue("아직 등록된 음식이 없어. 신규 등록 시트를 사용해줘.");}
            int last=Math.max(2,foods.size()+1);
            defineName(book,"ExistingFoodNames","'음식 선택값'!$A$2:$A$"+last);
            defineName(book,"ExistingFoodLookup","'음식 선택값'!$A$2:$E$"+last);
            book.setSheetHidden(book.getSheetIndex(foodValues),true);
            for(var sheet:List.of(fresh,additional)) {
                boolean adding=sheet==additional;
                var headers=categoryHeaders(adding);
                for(int c=0;c<headers.size();c++) if(!headers.get(c).equals(text(cell(sheet,3,c))))
                    throw new IOException("기본 엑셀 양식의 열 이름과 업로드 규칙이 다릅니다: "+sheet.getSheetName());
                int majorColumn=headers.indexOf(adding?"기존 대분류":"대분류"),minorColumn=headers.indexOf(adding?"기존 중분류":"중분류");
                cell(sheet,0,0).setCellValue(sheet.getSheetName());
                for(var row:sheet) if(row.getRowNum()>=4) for(var value:row) value.setBlank();
                for(int r=4;r<MAX_ROWS+4;r++) {
                    for(int c=0;c<headers.size();c++) cell(sheet,r,c);
                    if(adding) cell(sheet,r,headers.indexOf("음식 번호")).setCellFormula(lookupFormula(r+1,2,headers.indexOf("기존 음식명 선택")));
                    if(adding) {
                        cell(sheet,r,majorColumn).setCellFormula(lookupFormula(r+1,4,headers.indexOf("기존 음식명 선택")));
                        cell(sheet,r,minorColumn).setCellFormula(lookupFormula(r+1,5,headers.indexOf("기존 음식명 선택")));
                    }
                }
                // Reset the sample's overlapping list ranges before applying per-column rules.
                if(sheet.getCTWorksheet().isSetDataValidations()) sheet.getCTWorksheet().unsetDataValidations();
                if(!adding) {
                var categoryHelper=sheet.getDataValidationHelper();
                var majorRule=categoryHelper.createValidation(categoryHelper.createFormulaListConstraint("CategoryMajorNames"),new CellRangeAddressList(4,MAX_ROWS+3,majorColumn,majorColumn));
                majorRule.setShowErrorBox(true);majorRule.setErrorStyle(DataValidation.ErrorStyle.STOP);sheet.addValidationData(majorRule);
                String parent="$"+org.apache.poi.ss.util.CellReference.convertNumToColString(majorColumn)+"5";
                var minorRule=categoryHelper.createValidation(categoryHelper.createFormulaListConstraint("INDIRECT(IFERROR(VLOOKUP("+parent+",CategoryMajorLookup,2,FALSE),\"CategoryEmpty\"))"),new CellRangeAddressList(4,MAX_ROWS+3,minorColumn,minorColumn));
                minorRule.setShowErrorBox(true);minorRule.setErrorStyle(DataValidation.ErrorStyle.STOP);minorRule.createErrorBox("중분류 확인","먼저 대분류를 선택하고 해당 목록에서 중분류를 골라줘. 대분류를 바꾸면 중분류도 다시 확인해줘.");sheet.addValidationData(minorRule);
                } else {
                    var styles=new HashMap<String,CellStyle>();
                    for(int r=4;r<MAX_ROWS+4;r++) for(int c=0;c<headers.size();c++) {
                        var value=cell(sheet,r,c);boolean locked=c<3;
                        String key=value.getCellStyle().getIndex()+":"+locked;
                        var style=styles.get(key);
                        if(style==null) {style=book.createCellStyle();style.cloneStyleFrom(value.getCellStyle());style.setLocked(locked);styles.put(key,style);}
                        value.setCellStyle(style);
                    }
                    sheet.protectSheet("frizer");
                }
                dropdown(sheet,headers.indexOf("보관 위치"),new String[]{"실온","냉장실","냉동실"});
                dropdown(sheet,headers.indexOf("출처"),new String[]{"장보기","배달 잔반","직접 조리","부모님","기타"});
                dropdown(sheet,headers.indexOf("냉동 구분"),new String[]{"직접 냉동","시판 냉동식품"});
                dropdown(sheet,headers.indexOf("개봉 상태"),new String[]{"해당 없음","미개봉","개봉함","불확실"});
                var helper=sheet.getDataValidationHelper();
                int quantityColumn=headers.indexOf("수량");String q=org.apache.poi.ss.util.CellReference.convertNumToColString(quantityColumn)+"5";
                var rule=helper.createValidation(helper.createCustomConstraint("AND(ISNUMBER("+q+"),"+q+">0,"+q+"<=999999999.99,ROUND("+q+",2)="+q+")"),new CellRangeAddressList(4,MAX_ROWS+3,quantityColumn,quantityColumn));
                rule.setShowErrorBox(true);rule.setErrorStyle(DataValidation.ErrorStyle.STOP);rule.createErrorBox("수량 확인","0보다 큰 숫자를 소수 둘째 자리까지 입력해줘.");sheet.addValidationData(rule);
            }
            if(!foods.isEmpty()) {
                var helper=additional.getDataValidationHelper();
                int selectionColumn=categoryHeaders(true).indexOf("기존 음식명 선택");
                var selector=helper.createValidation(helper.createFormulaListConstraint("ExistingFoodNames"),new CellRangeAddressList(4,MAX_ROWS+3,selectionColumn,selectionColumn));
                selector.setShowErrorBox(true);selector.setErrorStyle(DataValidation.ErrorStyle.STOP);selector.createErrorBox("음식 선택","목록에 있는 음식을 선택해줘.");additional.addValidationData(selector);
            }
            // Keep the source layout; update obsolete instructions in the downloaded copy only.
            var guide=book.getSheet("안내");
            cell(guide,4,0).setCellValue("4. 신규 등록은 한 행마다 새 음식을 만들어. 같은 이름도 자동으로 합치지 않아.");
            cell(guide,5,0).setCellValue("5. 개봉 상태는 해당 없음·미개봉·개봉함·불확실 중 선택해. 비우면 개봉일이 있을 때 개봉함, 없을 때 미개봉으로 저장돼.");
            cell(guide,6,0).setCellValue("6. 추가 등록은 D열에서 기존 음식을 선택해. A~C열의 번호·기존 대분류·기존 중분류는 자동 표시되며 변경할 수 없어.");
            cell(guide,7,0).setCellValue("7. 개봉함만 개봉일을 입력할 수 있어. 날짜가 없으면 개봉일 불확실로 저장하고 등록일을 확인일로 기록해.");
            cell(guide,11,0).setCellValue("11. 이름 100자, 단위 4자, 용량 50자, 출처 메모 200자, 메모 500자까지 입력할 수 있어.");
            cell(guide,12,0).setCellValue("12. 새 항목 추가용 양식이야. 경고 알림 제외는 앱 화면에서 설정해줘. 기존 항목 수정·소비·폐기는 지원하지 않아.");
            int noteRow=guide.getLastRowNum()+1;
            var noteStyle=book.createCellStyle();noteStyle.cloneStyleFrom(cell(guide,12,0).getCellStyle());noteStyle.setWrapText(true);
            for(String note:List.of("신규 등록은 대분류와 해당 중분류를 입력해줘. 중분류가 없는 대분류는 중분류를 비워줘.",
                    "추가 등록은 기존 음식의 분류를 그대로 사용해. 분류 변경은 앱의 수정하자에서 해줘.",
                    "파일을 올려 미리보기를 확인한 뒤 전체 등록돼.")) {
                cell(guide,noteRow,0).setCellValue(note);cell(guide,noteRow,0).setCellStyle(noteStyle);guide.getRow(noteRow).setHeightInPoints(42);
                var originalRegion=guide.getMergedRegions().stream().filter(region->region.getFirstRow()==12).findFirst();
                if(originalRegion.isPresent()) guide.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(noteRow,noteRow,originalRegion.get().getFirstColumn(),originalRegion.get().getLastColumn()));
                noteRow++;
            }
            int firstSheet=book.getSheetIndex(fresh);
            for(var sheet:book) sheet.setSelected(sheet==fresh);
            book.setActiveSheet(firstSheet);book.setSelectedTab(firstSheet);book.setFirstVisibleTab(firstSheet);
            book.setForceFormulaRecalculation(true);book.write(output);return output.toByteArray();
        }
    }
    private static Cell cell(Sheet sheet,int row,int column) {
        var line=sheet.getRow(row);if(line==null)line=sheet.createRow(row);
        return line.getCell(column,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
    }
    private static void defineName(XSSFWorkbook book,String name,String formula) {
        var value=book.getName(name);if(value==null){value=book.createName();value.setNameName(name);}value.setRefersToFormula(formula);
    }
    private static int nameGroup(String name) {
        if(name.isBlank()) return 3;
        int first=name.strip().codePointAt(0);
        if(Character.UnicodeScript.of(first)==Character.UnicodeScript.HANGUL) return 0;
        if((first>='a' && first<='z') || (first>='A' && first<='Z')) return 1;
        return Character.isDigit(first)?2:3;
    }
    private static void dropdown(Sheet sheet,int column,String[] values) {
        var helper=sheet.getDataValidationHelper();
        var validation=helper.createValidation(helper.createExplicitListConstraint(values),new CellRangeAddressList(4,MAX_ROWS+3,column,column));
        validation.setShowErrorBox(true);validation.setErrorStyle(DataValidation.ErrorStyle.STOP);validation.createErrorBox("입력 확인","선택 목록의 값을 사용해줘.");sheet.addValidationData(validation);
    }
}
