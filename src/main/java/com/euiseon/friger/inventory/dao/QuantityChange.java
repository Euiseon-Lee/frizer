package com.euiseon.friger.inventory.dao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Quantity changes use recorded values, never today's remaining quantity. */
public record QuantityChange(long historyId, String actionType, BigDecimal beforeQuantityAmount,
        BigDecimal afterQuantityAmount, String beforeQuantityUnit, String quantityUnit, String quantityText,
        OffsetDateTime createdAt, java.util.UUID cookingId, String cookingName, String changesText) {
    @org.apache.ibatis.annotations.AutomapConstructor
    public QuantityChange {}
    public QuantityChange(long historyId,String actionType,BigDecimal beforeQuantityAmount,BigDecimal afterQuantityAmount,
            String beforeQuantityUnit,String quantityUnit,String quantityText,OffsetDateTime createdAt) {
        this(historyId,actionType,beforeQuantityAmount,afterQuantityAmount,beforeQuantityUnit,quantityUnit,quantityText,createdAt,null,null,null);
    }
    public String cookingDescription() {
        if (actionType.equals("COOK_IN")) return "수량 " + remainingQuantity();
        if (actionType.equals("COOK_OUT")) return cookingName + " - "
            + display(beforeQuantityAmount.subtract(afterQuantityAmount),quantityUnit) + " 사용 ("
            + display(beforeQuantityAmount,quantityUnit) + " → " + remainingQuantity() + ")";
        return changesText == null ? changeQuantity() : changesText.replace("\n", ", ");
    }
    public String actionLabel() {
        return com.euiseon.friger.common.type.FoodActionType.valueOf(actionType).label();
    }
    public String changeQuantity() {
        if (firstQuantity()) {
            return afterQuantityAmount != null && quantityUnit != null ? "+" + remainingQuantity() : remainingQuantity();
        }
        if (beforeQuantityAmount != null && afterQuantityAmount != null && quantityUnit != null
                && (!actionType.equals("UPDATE") || Objects.equals(beforeQuantityUnit, quantityUnit))) {
            var delta = afterQuantityAmount.subtract(beforeQuantityAmount);
            return (delta.signum() > 0 ? "+" : "") + delta.stripTrailingZeros().toPlainString() + quantityUnit;
        }
        // Unit changes cannot be expressed as a numeric delta.
        return display(beforeQuantityAmount, beforeQuantityUnit) + " → " + remainingQuantity();
    }
    public String remainingQuantity() {
        if (firstQuantity() && (afterQuantityAmount == null || quantityUnit == null))
            return quantityText == null || quantityText.isBlank() ? "미입력" : quantityText;
        return display(afterQuantityAmount, quantityUnit);
    }
    /** Registration and split children both carry the item's first quantity. */
    private boolean firstQuantity() {
        return actionType.equals("CREATE") || actionType.equals("SPLIT_IN");
    }
    private static String display(BigDecimal amount, String unit) {
        return amount == null || unit == null ? "미입력" : amount.stripTrailingZeros().toPlainString() + unit;
    }
}
