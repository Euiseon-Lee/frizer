package com.euiseon.friger.inventory.entity;

import java.time.LocalDate;
import com.euiseon.friger.common.type.OpeningStatus;
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
        String quantityUnit,
        LocalDate warningPausedUntil, OpeningStatus openingStatus, LocalDate openingConfirmedAt) {
    @org.apache.ibatis.annotations.AutomapConstructor
    public FoodItem {}
    public FoodItem(Long foodId, String foodName, StorageType storageType, String category,
            String quantityText, LocalDate expiredAt, LocalDate purchasedAt, LocalDate openedAt,
            LocalDate frozenAt, FoodSourceType sourceType, FreezeType freezeType, FoodStatus status,
            String memo, OffsetDateTime createdAt, OffsetDateTime updatedAt, String capacityText,
            String sourceMemo, LocalDate sellByAt, BigDecimal quantityAmount, String quantityUnit, LocalDate warningPausedUntil) {
        this(foodId,foodName,storageType,category,quantityText,expiredAt,purchasedAt,openedAt,frozenAt,
            sourceType,freezeType,status,memo,createdAt,updatedAt,capacityText,sourceMemo,sellByAt,
            quantityAmount,quantityUnit,warningPausedUntil,openedAt==null?OpeningStatus.UNKNOWN:OpeningStatus.OPENED,null);
    }
    public FoodItem(Long foodId, String foodName, StorageType storageType, String category,
            String quantityText, LocalDate expiredAt, LocalDate purchasedAt, LocalDate openedAt,
            LocalDate frozenAt, FoodSourceType sourceType, FreezeType freezeType, FoodStatus status,
            String memo, OffsetDateTime createdAt, OffsetDateTime updatedAt, String capacityText,
            String sourceMemo, LocalDate sellByAt, BigDecimal quantityAmount, String quantityUnit) {
        this(foodId,foodName,storageType,category,quantityText,expiredAt,purchasedAt,openedAt,frozenAt,
            sourceType,freezeType,status,memo,createdAt,updatedAt,capacityText,sourceMemo,sellByAt,
            quantityAmount,quantityUnit,null);
    }
    public FoodItem withWarningPausedUntil(LocalDate until) {
        return withWarningPausedUntil(until, updatedAt);
    }
    public FoodItem withWarningPausedUntil(LocalDate until, OffsetDateTime modifiedAt) {
        return new FoodItem(foodId,foodName,storageType,category,quantityText,expiredAt,purchasedAt,openedAt,
            frozenAt,sourceType,freezeType,status,memo,createdAt,modifiedAt,capacityText,sourceMemo,sellByAt,
            quantityAmount,quantityUnit,until,openingStatus,openingConfirmedAt);
    }
    public FoodItem withOpening(OpeningStatus state, LocalDate confirmed) {
        return new FoodItem(foodId,foodName,storageType,category,quantityText,expiredAt,purchasedAt,openedAt,
            frozenAt,sourceType,freezeType,status,memo,createdAt,updatedAt,capacityText,sourceMemo,sellByAt,
            quantityAmount,quantityUnit,warningPausedUntil,state,confirmed);
    }
    public String openingLabel() {
        return switch(openingStatus) { case UNOPENED -> "미개봉"; case UNKNOWN -> "개봉 여부 불확실";
            case OPENED -> openedAt == null ? "개봉함 · 개봉일 불확실" : "개봉함"; };
    }
    public LocalDate openingReference() { return openingStatus == OpeningStatus.OPENED ? (openedAt != null ? openedAt : openingConfirmedAt) : null; }
    public String openingElapsed(LocalDate today) {
        return openingStatus == OpeningStatus.OPENED && openedAt == null && openingConfirmedAt != null
            ? "개봉 후 최소 " + java.time.temporal.ChronoUnit.DAYS.between(openingConfirmedAt,today) + "일 경과" : null;
    }
    public String openingWarning() { return openedAt == null ? "개봉 후 최소 3개월이 지났어. 확인이 필요해!" : "개봉 후 3개월이 지났어. 확인이 필요해!"; }
    public boolean warningPaused(LocalDate today) {
        return warningPausedUntil != null && !warningPausedUntil.isBefore(today);
    }
    public boolean warningPausedForever() { return LocalDate.of(9999,12,31).equals(warningPausedUntil); }
    public boolean frozenBeforeUseBy() {
        return storageType == StorageType.FREEZER && freezeType == FreezeType.HOME_FROZEN
            && expiredAt != null && frozenAt != null && !frozenAt.isAfter(expiredAt);
    }
    public String frozenNotice(LocalDate today) {
        if (storageType != StorageType.FREEZER || freezeType != FreezeType.HOME_FROZEN || expiredAt == null) return null;
        if (frozenAt == null) return "냉동일을 몰라 소비기한 전 냉동 여부를 확인할 수 없어.";
        if (frozenBeforeUseBy()) return "소비기한 당일 또는 이전에 냉동했어 · 냉동 보관 "
            + java.time.temporal.ChronoUnit.DAYS.between(frozenAt,today) + "일째";
        return "소비기한이 지난 뒤 냉동했어. 냉동으로 소비기한이 연장되지는 않아.";
    }
    /** Format structured quantities without parsing or rewriting legacy free text. */
    public String displayQuantity() {
        return quantityAmount == null || quantityUnit == null ? quantityText
                : quantityAmount.stripTrailingZeros().toPlainString() + quantityUnit.strip();
    }
    public boolean useByOverdue(LocalDate today) {
        return expiredAt != null && expiredAt.isBefore(today) && !frozenBeforeUseBy();
    }

    public boolean sellByOverdue(LocalDate today) {
        return expiredAt == null && sellByAt != null && sellByAt.isBefore(today);
    }

    /** Calendar-month cleanup reminder; does not change the recorded use-by date. */
    public boolean openedOverdue(LocalDate today) {
        return openingReference() != null && openingReference().plusMonths(3).isBefore(today);
    }

    public boolean needsAttention(LocalDate today) {
        return !warningPaused(today) && (useByOverdue(today) || sellByOverdue(today) || openedOverdue(today));
    }

    /** Home WARNING scope includes the effective deadline falling today. */
    public boolean needsReview(LocalDate today) {
        return !warningPaused(today) && (needsAttention(today)
            || (!frozenBeforeUseBy() && today.equals(expiredAt != null ? expiredAt : sellByAt)));
    }
}
