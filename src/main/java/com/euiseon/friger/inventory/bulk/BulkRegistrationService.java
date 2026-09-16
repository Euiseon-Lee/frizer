package com.euiseon.friger.inventory.bulk;

import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.euiseon.friger.inventory.bulk.BulkValidation.Type.*;
import static com.euiseon.friger.inventory.bulk.BulkValidation.label;

@Service
public class BulkRegistrationService {
    public record Preview(UUID requestId, List<BulkWorkbook.Entry> rows, boolean duplicate) {
        public boolean valid() { return !rows.isEmpty() && rows.stream().allMatch(r -> r.errors().isEmpty()); }
    }
    private record Stored(String payload, String fingerprint, OffsetDateTime expiresAt, Integer resultCount) {}
    private final BulkWorkbook workbook;
    private final InventoryService inventory;
    private final FoodMasterDao masters;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    public BulkRegistrationService(BulkWorkbook workbook, InventoryService inventory, FoodMasterDao masters,
                                   JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.workbook=workbook; this.inventory=inventory; this.masters=masters; this.jdbc=jdbc; this.json=json; this.clock=clock;
    }
    @Transactional
    public Preview preview(byte[] bytes, UUID owner) {
        var parsed = workbook.read(bytes);
        var rows = new ArrayList<BulkWorkbook.Entry>();
        for(var row:parsed) {
            var form=row.form();
            var errors=new BulkValidation(row.issues());
            Long version=null;
            if(row.masterId()!=null) {
                var master=masters.find(row.masterId());
                if(master==null) errors.add(CONDITION,"masterId","기존 음식 번호: 해당 음식을 찾을 수 없어. 최신 양식을 받아 다시 선택해줘.");
                else {
                    version=master.versionNo();
                    if (!row.cells().get(5).matches("[0-9]+") && !row.cells().get(5).equals(BulkWorkbook.choiceLabel(master.masterId(),master.foodName(),master.category())))
                        errors.add(CONDITION,"masterId","기존 음식 선택: 음식 정보가 바뀌었어. 최신 양식을 받아 다시 선택해줘.");
                    if(!form.foodName().isBlank() && !form.foodName().equals(master.foodName())) errors.add(CONDITION,"foodName","음식명: 기존 음식 번호의 이름과 달라.");
                    if(form.category()!=null && !Objects.equals(form.category(), master.category())) errors.add(CONDITION,"category","분류: 기존 음식의 분류와 달라. 최신 양식을 받아 다시 선택해줘.");
                    form=form.withIdentity(master.foodName(),master.category());
                }
            }
            var missing=new ArrayList<String>();
            boolean adding="추가 등록".equals(row.sheet());
            if(!adding && form.foodName().isBlank()) missing.add("foodName");
            if(row.cells().get(1).isBlank()) missing.add("quantityAmount");
            if(form.quantityUnit().isBlank()) missing.add("quantityUnit");
            if(row.cells().get(3).isBlank() && form.sourceType()!=FoodSourceType.DELIVERY_LEFTOVER) missing.add("storageType");
            for(var field:missing) errors.add(MISSING,field,"");
            for(var validation:inventory.validationErrors(form).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                String field=validation.getKey();
                // Quantity and nonblank storage choices are already checked by the workbook parser.
                if(missing.contains(field) || field.equals("quantityAmount") || field.equals("storageType")
                        || (adding && field.equals("foodName"))) continue;
                if(field.equals("frozenAt") && errors.invalid("storageType")) continue;
                errors.add(INVALID,field,label(field)+": "+previewMessage(validation.getValue()));
            }
            StorageType storage=form.storageType();
            if(storage==null && !errors.invalid("storageType") && form.sourceType()==FoodSourceType.DELIVERY_LEFTOVER) { storage=StorageType.FREEZER; errors.add(AUTOMATIC,"storageType","출처가 ‘배달 잔반’이라 보관 위치가 자동으로 '냉동실'로 설정되었어."); }
            FreezeType freeze=FreezeType.NONE;
            LocalDate frozen=null;
            if(storage==StorageType.FREEZER) {
                freeze=form.freezeType(); frozen=form.frozenAt();
                if(freeze==null && !errors.invalid("freezeType")) {
                    freeze=FreezeType.HOME_FROZEN; errors.add(AUTOMATIC,"freezeType","냉동 구분이 없으면 자동으로 ‘직접 냉동’으로 설정돼.");
                } else if(freeze!=null && form.sourceType()==FoodSourceType.DELIVERY_LEFTOVER) {
                    freeze=FreezeType.HOME_FROZEN; errors.add(AUTOMATIC,"freezeType","출처가 ‘배달 잔반’이면 무조건 ‘직접 냉동’으로 설정돼.");
                }
            } else if(storage!=null && (form.frozenAt()!=null || form.freezeType()!=null)) errors.add(CONDITION,"freezeInfo","냉동 정보: 냉동실이 아닌 행의 냉동일, 냉동 구분을 비워줘.");
            if(form.sourceMemo()!=null && !errors.invalid("sourceType") && form.sourceType()!=FoodSourceType.ETC) errors.add(CONDITION,"sourceMemo","출처 메모: 출처가 기타일 때만 입력해줘.");
            form=new FoodCreateForm(form.foodName(), storage,form.category(),form.quantityAmount()==null?null:form.quantityAmount().stripTrailingZeros(),form.expiredAt(),form.purchasedAt(),form.openedAt(),frozen,form.sourceType(),freeze,false,form.memo(),form.capacityText(),form.sourceMemo(),form.sellByAt(),form.quantityUnit());
            rows.add(new BulkWorkbook.Entry(row.row(),row.cells(),form,row.group(),row.masterId(),version,errors.issues(),row.sheet()));
        }
        // Versions and spreadsheet row positions are excluded: an unchanged file remains a duplicate after stock edits.
        String fingerprint=hash(write(rows.stream().map(r->Arrays.asList(r.masterId()==null?r.form():r.form().withIdentity("",null),r.group(),r.masterId())).toList()));
        boolean duplicate=jdbc.queryForObject("SELECT count(*) FROM food_bulk_receipt WHERE fingerprint=?",Integer.class,fingerprint)>0;
        var preview=new Preview(UUID.randomUUID(),rows,duplicate);
        if(preview.valid()) {
            jdbc.update("DELETE FROM food_bulk_preview WHERE expires_at<? AND result_count IS NULL",OffsetDateTime.now(clock));
            jdbc.update("INSERT INTO food_bulk_preview(request_id,owner_id,payload,fingerprint,expires_at) VALUES(?,?,?,?,?)",
                preview.requestId(),owner,write(preview),fingerprint,OffsetDateTime.now(clock).plusMinutes(30));
        }
        return preview;
    }
    // Adapt shared validation copy only for the bulk preview.
    private static String previewMessage(String message) {
        return message.replace("입력해 주세요.", "입력해줘.")
                .replace("보관 위치를 선택해 주세요.", "보관 위치를 선택해줘.")
                .replace("수량은 0보다 커야 합니다.", "수량은 0보다 커야해.")
                .replace("미래 날짜는 입력할 수 없습니다.", "미래 날짜는 입력할 수 없어.");
    }
    @Transactional
    public int commit(UUID requestId, UUID owner, boolean repeat) {
        var found=jdbc.query("SELECT payload,fingerprint,expires_at,result_count FROM food_bulk_preview WHERE request_id=? AND owner_id=? FOR UPDATE",
            (rs,n)->new Stored(rs.getString(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class),rs.getObject(4,Integer.class)),requestId,owner);
        if(found.isEmpty()) throw new IllegalArgumentException("미리보기를 다시 열어줘. 이 화면에서 확인한 요청만 등록할 수 있어.");
        var stored=found.getFirst();
        if(stored.resultCount()!=null) return stored.resultCount();
        if(!stored.expiresAt().isAfter(OffsetDateTime.now(clock))) throw new IllegalArgumentException("미리보기 시간이 지났어. 파일을 다시 올려줘.");
        var preview=read(stored.payload());
        if(preview.rows().stream().anyMatch(r->!r.group().isBlank() || r.sheet()==null)) throw new IllegalArgumentException("양식이 변경됐어. 새 양식으로 미리보기를 다시 확인해줘.");
        int claimed=jdbc.update("INSERT INTO food_bulk_receipt(fingerprint,request_id) VALUES(?,?) ON CONFLICT DO NOTHING",stored.fingerprint(),requestId);
        if(claimed==0 && !(preview.duplicate() && repeat)) throw new IllegalArgumentException("같은 내용이 이미 등록됐어. 실제로 다시 들어온 재고라면 파일을 다시 올리고 중복 등록 안내를 확인해줘.");
        var versions=new TreeMap<Long,Long>();
        for(var row:preview.rows()) if(row.masterId()!=null) versions.put(row.masterId(),row.version());
        // Lock shared targets in a stable order and verify the entire snapshot before inserting anything.
        for(var target:versions.entrySet()) {
            var master=masters.lock(target.getKey());
            if(master==null || master.versionNo()!=target.getValue()) throw new IllegalArgumentException("기존 음식이 미리보기 이후 바뀌었어. 파일을 다시 올려 최신 내용을 확인해줘.");
        }
        for(var row:preview.rows()) if(!inventory.validationErrors(row.form()).isEmpty()) throw new IllegalArgumentException(row.sheet()+" "+row.row()+"행의 입력을 다시 확인해줘. 전체 등록을 취소했어.");
        for(var row:preview.rows()) {
            Long masterId=row.masterId();
            long itemId;
            if(masterId==null) {
                itemId=inventory.create(row.form());
            } else {
                var target=masters.lock(masterId);
                inventory.create(row.form(),masterId,target.versionNo());
            }
        }
        int count=preview.rows().size();
        jdbc.update("UPDATE food_bulk_preview SET result_count=? WHERE request_id=?",count,requestId);
        return count;
    }
    private String write(Object object) { try { return json.writeValueAsString(object); } catch(Exception e) { throw new IllegalStateException("일괄 등록 요청을 저장하지 못했습니다.",e); } }
    private Preview read(String payload) { try { return json.readValue(payload,Preview.class); } catch(Exception e) { throw new IllegalArgumentException("미리보기 형식이 바뀌었어. 파일을 다시 올려 확인해줘.",e); } }
    private static String hash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
