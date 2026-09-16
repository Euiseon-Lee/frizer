package com.euiseon.friger.inventory.bulk;

import java.util.ArrayList;
import java.util.List;

/** Row validation is classified at its source, never by matching displayed copy. */
public final class BulkValidation {
    public enum Type { MISSING, INVALID, CONDITION, AUTOMATIC }
    public record Issue(Type type, String field, String message) {}
    private final List<Issue> issues;

    public BulkValidation() { this(List.of()); }
    public BulkValidation(List<Issue> issues) { this.issues = new ArrayList<>(issues); }
    public List<Issue> issues() { return List.copyOf(issues); }
    public boolean invalid(String field) {
        return issues.stream().anyMatch(i -> i.field().equals(field) && i.type() != Type.AUTOMATIC);
    }
    public void add(Type type, String field, String message) {
        // Keep the first actionable error for each field; parser failures take precedence.
        if (type != Type.AUTOMATIC && invalid(field)) return;
        issues.add(new Issue(type, field, message));
    }
    public static List<String> errors(List<Issue> issues) {
        var messages = new ArrayList<String>();
        issues.stream().filter(i -> i.type() != Type.MISSING && i.type() != Type.AUTOMATIC)
                .map(Issue::message).distinct().forEach(messages::add);
        var missing = issues.stream().filter(i -> i.type() == Type.MISSING).map(i -> label(i.field())).toList();
        if (!missing.isEmpty()) messages.add("누락된 필수 정보: " + String.join(", ", missing));
        return List.copyOf(messages);
    }
    public static String label(String field) {
        return switch (field) {
            case "foodName" -> "음식명"; case "quantityAmount" -> "수량";
            case "quantityUnit" -> "단위"; case "storageType" -> "보관 위치";
            case "category" -> "분류"; case "capacityText" -> "용량";
            case "memo" -> "메모"; case "sourceMemo" -> "출처 메모";
            case "purchasedAt" -> "구매일"; case "openedAt" -> "개봉일";
            case "frozenAt" -> "냉동일"; case "expiredAt" -> "소비기한";
            case "sellByAt" -> "유통기한"; case "sourceType" -> "출처";
            case "freezeType" -> "냉동 구분"; case "masterId" -> "기존 음식 선택";
            default -> field;
        };
    }
}
