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
    public boolean useByOverdue(LocalDate today) {
        return expiredAt != null && expiredAt.isBefore(today);
    }

    public boolean sellByOverdue(LocalDate today) {
        return expiredAt == null && sellByAt != null && sellByAt.isBefore(today);
    }

    /** Calendar-month cleanup reminder; does not change the recorded use-by date. */
    public boolean openedOverdue(LocalDate today) {
        return openedAt != null && openedAt.plusMonths(3).isBefore(today);
    }

    public boolean needsAttention(LocalDate today) {
        return useByOverdue(today) || sellByOverdue(today) || openedOverdue(today);
    }
}
