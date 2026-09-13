package com.euiseon.friger.history.dto;

import java.time.OffsetDateTime;
import com.euiseon.friger.common.type.FoodActionType;
import com.euiseon.friger.common.type.StorageType;

public record HistoryEntry(Long historyId, Long foodId, String foodName,
        FoodActionType actionType, StorageType previousStorageType, StorageType newStorageType,
        String quantityText, OffsetDateTime createdAt, String changesText) {
    public String actionLabel() {
        return switch (actionType) {
            case CREATE -> "등록";
            case UPDATE -> "수정";
            case CONSUME -> "소비";
            case DISCARD -> "폐기";
            case FREEZE -> "냉동";
            case MOVE -> "이동";
        };
    }
    public String locationLabel(StorageType storage) {
        if (storage == null) return "";
        return switch (storage) { case ROOM -> "실온"; case FRIDGE -> "냉장실"; case FREEZER -> "냉동실"; };
    }
}
