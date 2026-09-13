package com.euiseon.friger.inventory.exception;

import java.util.Map;

public class InvalidFoodException extends RuntimeException {
    private final Map<String, String> errors;

    public InvalidFoodException(Map<String, String> errors) {
        super("음식 등록 내용을 확인해 주세요.");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() { return errors; }
}
