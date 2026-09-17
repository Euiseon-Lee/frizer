package com.euiseon.friger.history.entity;

import java.time.OffsetDateTime;

import com.euiseon.friger.common.type.FoodActionType;
import com.euiseon.friger.common.type.StorageType;

/** Action snapshot; UPDATE stores the changed fields with their before/after values. */
public record FoodHistory(
        Long historyId,
        Long foodId,
        FoodActionType actionType,
        StorageType previousStorageType,
        StorageType newStorageType,
        String quantityText,
        OffsetDateTime createdAt, String changesText) {
}
