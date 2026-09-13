package com.euiseon.friger.inventory.dto;

import java.time.LocalDate;
import com.euiseon.friger.common.type.FoodSourceType;
import com.euiseon.friger.common.type.FreezeType;
import com.euiseon.friger.common.type.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

public record FoodCreateForm(
        @NotBlank(message = "음식명을 입력해 주세요.")
        @Size(max = 100, message = "음식명은 100자 이내로 입력해 주세요.") String foodName,
        StorageType storageType,
        @Size(max = 50, message = "분류는 50자 이내로 입력해 주세요.") String category,
        @NotBlank(message = "수량을 입력해 주세요.")
        @Size(max = 50, message = "수량은 50자 이내로 입력해 주세요.") String quantityText,
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
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sellByAt) {
    public static FoodCreateForm empty() {
        return new FoodCreateForm(null, null, null, null, null, null, null, null, null, null, false, null, null, null, null);
    }
}
