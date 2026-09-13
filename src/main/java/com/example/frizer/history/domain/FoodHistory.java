package com.example.frizer.history.domain;

import java.time.OffsetDateTime;

import com.example.frizer.common.type.FoodActionType;
import com.example.frizer.common.type.StorageType;

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
