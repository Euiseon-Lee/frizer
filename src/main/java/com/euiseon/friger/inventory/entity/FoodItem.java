package com.euiseon.friger.inventory.entity;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.euiseon.friger.common.type.FoodSourceType;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.common.type.FreezeType;
import com.euiseon.friger.common.type.StorageType;

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
        OffsetDateTime updatedAt,
        String capacityText,
        String sourceMemo,
        LocalDate sellByAt,
        BigDecimal quantityAmount,
        String quantityUnit) {
}
