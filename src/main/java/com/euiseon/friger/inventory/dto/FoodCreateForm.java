package com.euiseon.friger.inventory.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import com.euiseon.friger.common.type.FoodSourceType;
import com.euiseon.friger.common.type.FreezeType;
import com.euiseon.friger.common.type.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.format.annotation.DateTimeFormat;

public record FoodCreateForm(
        @NotBlank(message = "음식명을 입력해 주세요.")
        @Size(max = 100, message = "음식명은 100자 이내로 입력해 주세요.") String foodName,
        StorageType storageType,
        @Size(max = 50, message = "분류는 50자 이내로 입력해 주세요.") String category,
        @NotNull(message = "수량을 입력해 주세요.")
        @DecimalMin(value = "0", inclusive = false, message = "수량은 0보다 커야 합니다.")
        @Digits(integer = 9, fraction = 2, message = "수량은 정수 9자리, 소수 2자리 이내로 입력해 주세요.") BigDecimal quantityAmount,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiredAt,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchasedAt,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openedAt,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate frozenAt,
        FoodSourceType sourceType,
        FreezeType freezeType,
        Boolean freezeToday,
        @Size(max = 500, message = "메모는 500자 이내로 입력해 주세요.") String memo,
        @Size(max = 50, message = "용량은 50자 이내로 입력해 주세요.") String capacityText,
        @Size(max = 200, message = "출처 메모는 200자 이내로 입력해 주세요.") String sourceMemo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sellByAt,
        @NotBlank(message = "단위를 입력해 주세요.")
        @Size(max = 10, message = "단위는 10자 이내로 입력해 주세요.") String quantityUnit) {
    public FoodCreateForm {
        if (quantityUnit != null) quantityUnit = quantityUnit.strip();
    }
    public static FoodCreateForm from(com.euiseon.friger.inventory.entity.FoodItem food) {
        return new FoodCreateForm(food.foodName(), food.storageType(), food.category(),
                food.quantityAmount() == null ? null : food.quantityAmount().stripTrailingZeros(),
                food.expiredAt(), food.purchasedAt(), food.openedAt(), food.frozenAt(), food.sourceType(), food.freezeType(), false,
                food.memo(), food.capacityText(), food.sourceMemo(), food.sellByAt(), food.quantityUnit());
    }
    public static FoodCreateForm empty() {
        return new FoodCreateForm(null, null, null, null, null, null, null, null, null, null, false, null, null, null, null, null);
    }
    public FoodCreateForm withIdentity(String name, String sharedCategory) {
        return new FoodCreateForm(name, storageType, sharedCategory, quantityAmount, expiredAt, purchasedAt,
                openedAt, frozenAt, sourceType, freezeType, freezeToday, memo, capacityText, sourceMemo,
                sellByAt, quantityUnit);
    }
}
