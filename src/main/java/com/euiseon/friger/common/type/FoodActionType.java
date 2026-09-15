package com.euiseon.friger.common.type;

public enum FoodActionType {
    CREATE, UPDATE, CONSUME, FREEZE, MOVE, DISCARD, CANCEL;

    public String label() {
        return switch (this) {
            case CREATE -> "등록";
            case UPDATE -> "수정";
            case CONSUME -> "소비";
            case FREEZE -> "냉동";
            case MOVE -> "이동";
            case DISCARD -> "폐기";
            case CANCEL -> "취소";
        };
    }
}
