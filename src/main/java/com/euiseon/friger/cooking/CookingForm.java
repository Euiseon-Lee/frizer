package com.euiseon.friger.cooking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import org.springframework.format.annotation.DateTimeFormat;

public record CookingForm(UUID requestId, String foodName, Long masterId, Long masterVersion,
        String categoryMajorCode, String categoryMinorCode, BigDecimal quantityAmount, String quantityUnit,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate cookedAt, StorageType storageType,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate frozenAt,
        String memo, String capacityText, BigDecimal consumedAmount, List<IngredientInput> ingredients) {
    public record IngredientInput(long foodId, Long version, BigDecimal amount, Boolean selected) {
        public IngredientInput { selected=Boolean.TRUE.equals(selected); }
    }
    public CookingForm {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        quantityUnit = quantityUnit == null ? null : quantityUnit.strip();
    }
    public List<IngredientInput> selected() { return ingredients.stream().filter(IngredientInput::selected).toList(); }
    public FoodCreateForm foodForm() {
        return new FoodCreateForm(foodName, storageType, null, quantityAmount, null, null, null,
            frozenAt, FoodSourceType.COOKED, FreezeType.HOME_FROZEN, false, memo, capacityText,
            null, null, quantityUnit, false, null, false, OpeningStatus.NOT_APPLICABLE);
    }
    public static CookingForm empty(LocalDate today) {
        return new CookingForm(UUID.randomUUID(),null,null,null,null,null,null,null,today,
            null,null,null,null,null,List.of());
    }
}
