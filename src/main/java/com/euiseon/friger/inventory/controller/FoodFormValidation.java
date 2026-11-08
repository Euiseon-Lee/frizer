package com.euiseon.friger.inventory.controller;

import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.service.InventoryService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/** Shared form validation keeps field codes, submitted values and business errors together. */
@Component
public class FoodFormValidation {
    private final Validator validator;
    private final InventoryService inventory;

    public FoodFormValidation(Validator validator, InventoryService inventory) {
        this.validator = validator;
        this.inventory = inventory;
    }

    public void collect(FoodCreateForm form, BindingResult errors) {
        validator.validate(form).forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            reject(errors, field, code, violation.getMessage(), false);
        });
        inventory.validationErrors(form).forEach((field, message) -> reject(errors, field, "invalid", message, true));
    }

    private void reject(BindingResult errors, String field, String code, String message, boolean bindingFailure) {
        if (errors.hasFieldErrors(field)) return;
        if (errors.getTarget() != null) errors.rejectValue(field, code, message);
        else errors.addError(new FieldError(errors.getObjectName(), field, errors.getFieldValue(field),
                bindingFailure, errors.resolveMessageCodes(code, field), null, message));
    }
}
