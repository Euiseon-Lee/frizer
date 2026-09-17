package com.euiseon.friger.common.type;

public enum FoodActionType {
    CREATE, UPDATE, CONSUME, FREEZE, MOVE, DISCARD, CANCEL, SPLIT_OUT, SPLIT_IN;

    public String label() {
        return switch (this) {
            case CREATE -> "등록";
            case UPDATE -> "수정";
            case CONSUME -> "소비";
            case FREEZE -> "냉동";
            case MOVE -> "병합";
            case DISCARD -> "폐기";
            case CANCEL -> "취소";
            // Both sides of a split share one label; the signed quantity tells the direction.
            case SPLIT_OUT, SPLIT_IN -> "분리";
        };
    }
}
