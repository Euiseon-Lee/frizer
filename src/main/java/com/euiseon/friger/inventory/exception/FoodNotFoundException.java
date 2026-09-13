package com.euiseon.friger.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class FoodNotFoundException extends RuntimeException {
    public FoodNotFoundException(long id) {
        super("음식을 찾을 수 없습니다: " + id);
    }
}
