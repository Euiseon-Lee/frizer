package com.euiseon.friger.history.dto;

import java.time.OffsetDateTime;
import com.euiseon.friger.common.type.FoodActionType;
import com.euiseon.friger.common.type.StorageType;

public record HistoryEntry(Long historyId, Long foodId, String foodName,
        FoodActionType actionType, StorageType previousStorageType, StorageType newStorageType,
        String quantityText, OffsetDateTime createdAt, String changesText,
        Long currentMasterId, String currentFoodName, String mergedFromName, String mergedIntoName,
        Integer mergedItemCount, Boolean itemMoved) {
    public boolean isMerge() { return mergedFromName != null; }
    public String currentLocationNote() {
        if (currentFoodName == null) return isMerge() ? "현재 음식은 전체 목록에서 확인해줘." : null;
        if (isMerge()) return "이동 이력이 있어 클릭 시 ‘" + currentFoodName + "’로 이동해.";
        if (currentFoodName.equals(displayFoodName())) return null;
        return Boolean.TRUE.equals(itemMoved)
                ? "병합 처리가 완료돼서 이제는 ‘" + currentFoodName + "’로 이동할 거야."
                : "이름이 바뀌어서 이제는 ‘" + currentFoodName + "’로 이동할 거야.";
    }
    public String actionLabel() {
        if (isMerge()) return "병합";
        return actionType.label();
    }
    public String homeSummary() {
        if (actionType == FoodActionType.CREATE || changesText == null || changesText.isBlank()) return actionLabel() + "했어";
        if (actionType == FoodActionType.UPDATE && detailFields().size() >= 2) return "수정한 정보 " + detailFields().size() + "건";
        return changesText.replaceAll("\\R+", " · ");
    }

    public record DetailField(String label, String value) {}

    private java.util.Map<String, String> registrationValues() {
        if (actionType != FoodActionType.CREATE || changesText == null || !changesText.startsWith("snapshot-v1:"))
            return java.util.Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    changesText.substring("snapshot-v1:".length()),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, String>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return java.util.Map.of();
        }
    }

    public String displayFoodName() {
        return registrationValues().getOrDefault("음식명", foodName);
    }

    public boolean incompleteRegistration() {
        return actionType == FoodActionType.CREATE && registrationValues().isEmpty();
    }

    public java.util.List<DetailField> detailFields() {
        var result = new java.util.ArrayList<DetailField>();
        if (actionType == FoodActionType.CREATE) {
            var snapshot = registrationValues();
            if (!snapshot.isEmpty()) {
                snapshot.forEach((label, value) -> {
                    if (!label.equals("음식명") && value != null && !value.isBlank() && !value.equals("-"))
                        result.add(new DetailField(label, value));
                });
            } else {
                if (newStorageType != null) result.add(new DetailField("보관 위치", locationLabel(newStorageType)));
                if (quantityText != null) result.add(new DetailField("수량", quantityText));
            }
        } else if (changesText != null && !changesText.isBlank()) {
            for (String line : changesText.split("\\R", -1)) {
                int colon = line.indexOf(": ");
                if (colon > 0 && java.util.Set.of("음식명", "수량", "단위", "용량", "출처", "출처 메모", "분류", "보관 위치", "냉동 유형", "냉동 보관 시작일", "유통기한", "소비기한", "구매일", "개봉일", "메모", "새 항목").contains(line.substring(0, colon))) {
                    result.add(new DetailField(line.substring(0, colon), line.substring(colon + 2)));
                } else if (!result.isEmpty()) {
                    var previous = result.remove(result.size() - 1);
                    result.add(new DetailField(previous.label(), previous.value() + "\n" + line));
                } else result.add(new DetailField("내용", line));
            }
        } else {
            if (newStorageType != null) result.add(new DetailField("보관 위치",
                    previousStorageType != null && previousStorageType != newStorageType
                            ? locationLabel(previousStorageType) + " → " + locationLabel(newStorageType)
                            : locationLabel(newStorageType)));
            if (quantityText != null) result.add(new DetailField("수량", quantityText));
        }
        return result;
    }

    public String locationLabel(StorageType storage) {
        if (storage == null) return "";
        return switch (storage) { case ROOM -> "실온"; case FRIDGE -> "냉장실"; case FREEZER -> "냉동실"; };
    }
}
