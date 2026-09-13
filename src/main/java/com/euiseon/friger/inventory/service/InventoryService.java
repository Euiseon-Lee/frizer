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

    public InventoryService(InventoryDao inventory, HistoryDao history, Clock clock, Validator validator) {
        this.inventory = inventory;
        this.history = history;
        this.clock = clock;
        this.validator = validator;
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
        Map<String, String> errors = new LinkedHashMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        FoodSourceType source = form.sourceType() == null ? FoodSourceType.ETC : form.sourceType();
        StorageType storage = form.storageType();
        if (storage == null && source == FoodSourceType.DELIVERY_LEFTOVER) storage = StorageType.FREEZER;
        if (storage == null) errors.put("storageType", "보관 위치를 선택해 주세요.");
        LocalDate today = LocalDate.now(clock);
        rejectFuture(errors, "purchasedAt", form.purchasedAt(), today);
        rejectFuture(errors, "openedAt", form.openedAt(), today);
        if (storage == StorageType.FREEZER && !Boolean.TRUE.equals(form.freezeToday())) {
            rejectFuture(errors, "frozenAt", form.frozenAt(), today);
        }
        if (!errors.isEmpty()) throw new InvalidFoodException(errors);

        FreezeType freeze = FreezeType.NONE;
        LocalDate frozenAt = null;
        if (storage == StorageType.FREEZER) {
            freeze = source == FoodSourceType.DELIVERY_LEFTOVER || form.freezeType() == null
                    || form.freezeType() == FreezeType.NONE ? FreezeType.HOME_FROZEN : form.freezeType();
            frozenAt = Boolean.TRUE.equals(form.freezeToday()) ? today : form.frozenAt();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        FoodItem food = new FoodItem(null, form.foodName().strip(), storage, optional(form.category()),
                form.quantityAmount().stripTrailingZeros().toPlainString() + form.quantityUnit(), form.expiredAt(), form.purchasedAt(), form.openedAt(),
                frozenAt, source, freeze, FoodStatus.ACTIVE, optional(form.memo()), now, now,
                optional(form.capacityText()),
                form.sourceType() == FoodSourceType.ETC ? optional(form.sourceMemo()) : null, form.sellByAt(), form.quantityAmount(), form.quantityUnit());
        long id = inventory.insert(food);
        int inserted = history.insert(new FoodHistory(null, id, FoodActionType.CREATE, null,
                storage, food.quantityText(), "음식 등록", now));
        if (inserted != 1) throw new IllegalStateException("등록 이력을 저장하지 못했습니다.");
        return id;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void rejectFuture(Map<String, String> errors, String field, LocalDate date, LocalDate today) {
        if (date != null && date.isAfter(today)) errors.put(field, "미래 날짜는 입력할 수 없습니다.");
    }
}
