package com.euiseon.friger.inventory.dao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Quantity changes use recorded values, never today's remaining quantity. */
public record QuantityChange(long historyId, String actionType, BigDecimal beforeQuantityAmount,
        BigDecimal afterQuantityAmount, String beforeQuantityUnit, String quantityUnit,
        OffsetDateTime createdAt) {
    public String actionLabel() {
        return com.euiseon.friger.common.type.FoodActionType.valueOf(actionType).label();
    }
    public String changeQuantity() {
        if (beforeQuantityAmount != null && afterQuantityAmount != null && quantityUnit != null
                && (!actionType.equals("UPDATE") || Objects.equals(beforeQuantityUnit, quantityUnit))) {
            var delta = afterQuantityAmount.subtract(beforeQuantityAmount);
            return (delta.signum() > 0 ? "+" : "") + delta.stripTrailingZeros().toPlainString() + quantityUnit;
        }
        // Unit changes cannot be expressed as a numeric delta.
        return display(beforeQuantityAmount, beforeQuantityUnit) + " → " + remainingQuantity();
    }
    public String remainingQuantity() { return display(afterQuantityAmount, quantityUnit); }
    private static String display(BigDecimal amount, String unit) {
        return amount == null || unit == null ? "미입력" : amount.stripTrailingZeros().toPlainString() + unit;
    }
}
