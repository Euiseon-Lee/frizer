package com.example.frizer.inventory.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.example.frizer.common.type.FoodSourceType;
import com.example.frizer.common.type.FoodStatus;
import com.example.frizer.common.type.FreezeType;
import com.example.frizer.common.type.StorageType;

/** Current persisted state. Unknown freezer storage start dates remain null. */
public record FoodItem(
        Long foodId,
        String foodName,
        StorageType storageType,
        String category,
        String quantityText,
        LocalDate expiredAt,
        LocalDate purchasedAt,
        LocalDate openedAt,
        LocalDate frozenAt,
        FoodSourceType sourceType,
        FreezeType freezeType,
        FoodStatus status,
        String memo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
