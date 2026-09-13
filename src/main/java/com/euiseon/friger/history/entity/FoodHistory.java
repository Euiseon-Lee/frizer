package com.euiseon.friger.history.entity;

import java.time.OffsetDateTime;

import com.euiseon.friger.common.type.FoodActionType;
import com.euiseon.friger.common.type.StorageType;

/** Action snapshot; does not preserve old food names or old frozen dates. */
public record FoodHistory(
        Long historyId,
        Long foodId,
        FoodActionType actionType,
        StorageType previousStorageType,
        StorageType newStorageType,
        String quantityText,
        String memo,
        OffsetDateTime createdAt) {
}
