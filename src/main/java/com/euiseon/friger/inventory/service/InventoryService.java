package com.euiseon.friger.inventory.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.history.entity.FoodHistory;
import com.euiseon.friger.history.dao.HistoryDao;
import com.euiseon.friger.inventory.entity.FoodItem;
import com.euiseon.friger.inventory.dao.InventoryDao;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.exception.FoodNotFoundException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryDao inventory;
    private final HistoryDao history;
    private final Clock clock;
    private final Validator validator;
    private final com.euiseon.friger.inventory.dao.FoodMasterDao masters;

    public InventoryService(InventoryDao inventory, HistoryDao history, Clock clock, Validator validator,
                            com.euiseon.friger.inventory.dao.FoodMasterDao masters) {
        this.inventory = inventory;
        this.history = history;
        this.clock = clock;
        this.validator = validator;
        this.masters = masters;
    }

    @Transactional(readOnly = true)
    public List<FoodItem> findActive() { return inventory.findActive(); }

    @Transactional(readOnly = true)
    public FoodItem findById(long id) {
        FoodItem food = inventory.findById(id);
        if (food == null) throw new FoodNotFoundException(id);
        return food;
    }

    @Transactional
    public long create(FoodCreateForm form) {
        return create(form, null, null);
    }

    @Transactional
    public long create(FoodCreateForm form, Long masterId, Long expectedVersion) {
        if (masterId != null) {
            var master = masters.lock(masterId);
            if (master == null || expectedVersion == null || master.versionNo() != expectedVersion)
                throw new InvalidFoodException(Map.of("", "선택한 음식이 변경되었어. 기존 음식을 다시 선택해줘."));
            form = form.withIdentity(master.foodName(), master.category());
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        FoodItem food = normalized(form, null, now, now);
        long id = masterId == null ? inventory.insert(food) : inventory.insertForMaster(food, masterId);
        if (masterId != null) masters.touch(masterId);
        int inserted = history.insert(new FoodHistory(null, id, FoodActionType.CREATE, null,
                food.storageType(), food.quantityText(), "음식 등록", now, registrationSnapshot(food)));
        if (inserted != 1) throw new IllegalStateException("등록 이력을 저장하지 못했습니다.");
        return id;
    }

    @Transactional
    public boolean update(long id, FoodCreateForm form, OffsetDateTime expectedUpdatedAt) {
        Long masterId = masters.masterIdForItem(id);
        if (masterId == null) throw new FoodNotFoundException(id);
        var master = masters.lock(masterId);
        if (master == null || !masterId.equals(masters.masterIdForItem(id)))
            throw new InvalidFoodException(Map.of("", "음식이 다른 음식에 합쳐졌어. 상세를 다시 열어줘."));
        FoodItem before = inventory.findByIdForUpdate(id);
        if (before == null) throw new FoodNotFoundException(id);
        if (before.status() != FoodStatus.ACTIVE) throw new InvalidFoodException(Map.of("", "보관 중인 음식만 수정할 수 있어."));
        if (expectedUpdatedAt == null || !before.updatedAt().isEqual(expectedUpdatedAt)) {
            throw new InvalidFoodException(Map.of("", "다른 화면에서 음식 정보가 바뀌었어. 상세를 다시 열어 최신 내용을 확인해줘."));
        }
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (!now.isAfter(before.updatedAt())) now = before.updatedAt().plusNanos(1000);
        FoodItem after = normalized(form, id, before.createdAt(), now);
        Map<String, String> oldValues = values(before);
        Map<String, String> newValues = values(after);
        String changes = newValues.entrySet().stream()
                .filter(entry -> !java.util.Objects.equals(oldValues.get(entry.getKey()), entry.getValue()))
                .map(entry -> entry.getKey() + ": " + display(oldValues.get(entry.getKey())) + " → " + display(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("\n"));
        if (changes.isEmpty()) return false;
        if (!java.util.Objects.equals(before.foodName(), after.foodName())
                || !java.util.Objects.equals(before.category(), after.category())) {
            masters.update(masterId, after.foodName(), after.category());
            masters.invalidateOtherItems(masterId, id);
        } else masters.touch(masterId);
        if (inventory.update(after) != 1) throw new IllegalStateException("수정 내용을 저장하지 못했습니다.");
        if (history.insertUpdate(new FoodHistory(null, id, FoodActionType.UPDATE, before.storageType(), after.storageType(),
                after.quantityText(), "음식 수정", now, changes), before, after) != 1) {
            throw new IllegalStateException("수정 이력을 저장하지 못했습니다.");
        }
        return true;
    }

    private static String registrationSnapshot(FoodItem food) {
        try {
            var snapshot = values(food);
            snapshot.put("수량", food.quantityText());
            snapshot.remove("단위");
            return "snapshot-v1:" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(snapshot);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("등록 내용을 기록하지 못했습니다.", e);
        }
    }

    private static Map<String, String> values(FoodItem food) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("음식명", raw(food.foodName()));
        values.put("수량", food.quantityAmount() == null ? raw(food.quantityText())
                : food.quantityAmount().stripTrailingZeros().toPlainString());
        values.put("단위", food.quantityUnit());
        values.put("용량", raw(food.capacityText()));
        values.put("출처", switch (food.sourceType()) {
            case null -> null;
            case PURCHASE -> "장보기"; case DELIVERY_LEFTOVER -> "배달 잔반"; case COOKED -> "직접 조리";
            case PARENTS -> "부모님의 은혜"; case ETC -> "기타";
        });
        values.put("출처 메모", raw(food.sourceMemo()));
        values.put("분류", raw(food.category()));
        values.put("보관 위치", switch (food.storageType()) { case ROOM -> "실온"; case FRIDGE -> "냉장실"; case FREEZER -> "냉동실"; });
        values.put("냉동 유형", switch (food.freezeType()) { case NONE -> "-"; case HOME_FROZEN -> "직접 냉동"; case COMMERCIAL_FROZEN -> "시판 냉동식품"; });
        values.put("냉동 보관 시작일", raw(food.frozenAt()));
        values.put("유통기한", raw(food.sellByAt()));
        values.put("소비기한", raw(food.expiredAt()));
        values.put("구매일", raw(food.purchasedAt()));
        values.put("개봉일", raw(food.openedAt()));
        values.put("메모", raw(food.memo()));
        return values;
    }

    private static String raw(Object value) { return value == null ? null : value.toString(); }


    private static String display(Object value) { return value == null ? "-" : value.toString(); }

    public Map<String, String> validationErrors(FoodCreateForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        FoodSourceType source = form.sourceType();
        StorageType storage = form.storageType();
        if (storage == null && source == FoodSourceType.DELIVERY_LEFTOVER) storage = StorageType.FREEZER;
        if (storage == null) errors.put("storageType", "보관 위치를 선택해 주세요.");
        LocalDate today = LocalDate.now(clock);
        rejectFuture(errors, "purchasedAt", form.purchasedAt(), today);
        rejectFuture(errors, "openedAt", form.openedAt(), today);
        if (storage == StorageType.FREEZER && !Boolean.TRUE.equals(form.freezeToday())) {
            rejectFuture(errors, "frozenAt", form.frozenAt(), today);
        }
        return errors;
    }

    private FoodItem normalized(FoodCreateForm form, Long id, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        Map<String, String> errors = validationErrors(form);
        if (!errors.isEmpty()) throw new InvalidFoodException(errors);
        FoodSourceType source = form.sourceType();
        StorageType storage = form.storageType();
        if (storage == null && source == FoodSourceType.DELIVERY_LEFTOVER) storage = StorageType.FREEZER;
        LocalDate today = LocalDate.now(clock);

        FreezeType freeze = FreezeType.NONE;
        LocalDate frozenAt = null;
        if (storage == StorageType.FREEZER) {
            freeze = source == FoodSourceType.DELIVERY_LEFTOVER || form.freezeType() == null
                    || form.freezeType() == FreezeType.NONE ? FreezeType.HOME_FROZEN : form.freezeType();
            frozenAt = Boolean.TRUE.equals(form.freezeToday()) ? today : form.frozenAt();
        }
        return new FoodItem(id, form.foodName().strip(), storage, optional(form.category()),
                form.quantityAmount().stripTrailingZeros().toPlainString() + form.quantityUnit(), form.expiredAt(), form.purchasedAt(), form.openedAt(),
                frozenAt, source, freeze, FoodStatus.ACTIVE, optional(form.memo()), createdAt, updatedAt,
                optional(form.capacityText()),
                form.sourceType() == FoodSourceType.ETC ? optional(form.sourceMemo()) : null, form.sellByAt(), form.quantityAmount(), form.quantityUnit());
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void rejectFuture(Map<String, String> errors, String field, LocalDate date, LocalDate today) {
        if (date != null && date.isAfter(today)) errors.put(field, "미래 날짜는 입력할 수 없습니다.");
    }
}
