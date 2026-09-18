package com.euiseon.friger.inventory.controller;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.service.InventoryService;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class InventoryController {
    private final InventoryService service;
    private final Clock clock;
    private final jakarta.validation.Validator validator;
    private final com.euiseon.friger.inventory.service.FoodMasterService masters;
    private final com.euiseon.friger.inventory.service.FoodRegistrationService registrations;
    private final com.euiseon.friger.inventory.service.FoodQuantityService quantities;

    public InventoryController(InventoryService service, Clock clock, com.euiseon.friger.inventory.service.FoodMasterService masters,
                               jakarta.validation.Validator validator,
                               com.euiseon.friger.inventory.service.FoodRegistrationService registrations,
                               com.euiseon.friger.inventory.service.FoodQuantityService quantities) {
        this.service = service;
        this.clock = clock;
        this.masters = masters;
        this.validator = validator;
        this.registrations = registrations;
        this.quantities = quantities;
    }

    @ModelAttribute
    void choices(Model model, jakarta.servlet.http.HttpSession session) {
        com.euiseon.friger.inventory.bulk.BulkRegistrationController.owner(session);
        model.addAttribute("storageLabels", labels(new StorageType[]{StorageType.ROOM, StorageType.FRIDGE, StorageType.FREEZER}, "실온", "냉장실", "냉동실"));
        model.addAttribute("sourceLabels", labels(FoodSourceType.values(), "장보기", "배달 잔반", "직접 조리", "부모님의 은혜", "기타"));
        model.addAttribute("today", LocalDate.now(clock));
    }

    private static <E extends Enum<E>> Map<E, String> labels(E[] values, String... labels) {
        Map<E, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) result.put(values[i], labels[i]);
        return result;
    }

    @GetMapping("/inventory")
    String inventory(@RequestParam(required = false) StorageType storage,
                     @RequestParam(defaultValue = "false") boolean ended,
                     @RequestParam(defaultValue = "false") boolean warning, Model model) {
        var today = LocalDate.now(clock);
        var allGroups = masters.groups(null,ended);
        var groups = allGroups.stream().map(group -> new com.euiseon.friger.inventory.service.FoodMasterService.Group(
                group.master(), group.items().stream()
                .filter(food -> ended || storage == null || food.storageType() == storage)
                .filter(food -> ended || !warning || food.needsReview(today)).toList()))
                .filter(group -> !group.items().isEmpty()).toList();
        model.addAttribute("foods", groups.stream().flatMap(group -> group.items().stream()).toList());
        model.addAttribute("selectedStorage", ended ? null : storage);
        model.addAttribute("warningOnly", !ended && warning);
        model.addAttribute("savedWarning", warning);
        model.addAttribute("ended", ended);
        model.addAttribute("totalCount", allGroups.stream().mapToInt(group -> group.items().size()).sum());
        model.addAttribute("groupTotals", allGroups.stream().collect(java.util.stream.Collectors.toMap(
                group -> group.master().masterId(), group -> group.items().size())));
        if (ended) model.addAttribute("registrationQuantities", quantities.endedRegistrationQuantities());
        model.addAttribute("groups", groups);
        return "inventory/list";
    }

    @GetMapping("/inventory/new")
    String newFood(@RequestParam(required = false) Long masterId, @RequestParam(defaultValue = "new") String registrationMode, Model model) {
        var form = FoodCreateForm.empty();
        Long version = null;
        if (masterId != null) {
            var master = masters.find(masterId);
            form = form.withIdentity(master.foodName(), master.category());
            version = master.versionNo();
        }
        model.addAttribute("foodForm", form);
        model.addAttribute("registrationRequestId", java.util.UUID.randomUUID());
        registrationContext(masterId != null || "existing".equals(registrationMode) ? "existing" : "new", masterId, version, model);
        return "inventory/new";
    }

    @GetMapping("/inventory/{id}")
    String detail(@PathVariable long id, @RequestParam(required = false) StorageType storage,
                  @RequestParam(required = false) Boolean ended,
                  @RequestParam(defaultValue = "false") boolean warning, Model model) {
        var preview = quantities.preview(id);
        boolean endedView = ended == null ? preview.item().status() != FoodStatus.ACTIVE : ended;
        model.addAttribute("quantityPreview", preview);
        model.addAttribute("quantityHistory", quantities.history(id));
        if (preview.item().status() == FoodStatus.DEPLETED) {
            var summary = quantities.endedSummaries(masters.masterId(id)).get(id);
            model.addAttribute("registrationQuantity", summary.registrationQuantity());
            model.addAttribute("endedAt", summary.endedAt());
        }
        model.addAttribute("ended", endedView);
        model.addAttribute("savedWarning", warning);
        model.addAttribute("selectedStorage", storage);
        model.addAttribute("warningOnly", !endedView && warning);
        model.addAttribute("food", service.findById(id));
        model.addAttribute("masterId", masters.masterId(id));
        return "inventory/detail";
    }

    @GetMapping("/inventory/{id}/edit")
    String editFood(@PathVariable long id, @RequestParam(required = false) StorageType storage,
            @RequestParam(defaultValue = "false") boolean warning, @RequestParam(defaultValue = "false") boolean ended,
            Model model, RedirectAttributes redirect) {
        editFilters(storage, warning, ended, model);
        var food = service.findById(id);
        if (food.status() != FoodStatus.ACTIVE) {
            redirect.addFlashAttribute("successMessage", "보관 중인 음식만 수정할 수 있어.");
            return FoodQuantityController.url("/inventory/" + id, storage, warning, ended);
        }
        model.addAttribute("foodForm", FoodCreateForm.from(food));
        editContext(id, food.updatedAt(), model);
        return "inventory/new";
    }

    @PostMapping("/inventory/{id}/edit")
    String updateFood(@PathVariable long id, @Valid @ModelAttribute("foodForm") FoodCreateForm form, BindingResult errors,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime expectedUpdatedAt,
            @RequestParam(required = false) StorageType storage,
            @RequestParam(defaultValue = "false") boolean warning, @RequestParam(defaultValue = "false") boolean ended,
            Model model, RedirectAttributes redirect) {
        editFilters(storage, warning, ended, model);
        editContext(id, expectedUpdatedAt, model);
        collectValidationErrors(form, errors);
        if (errors.hasErrors()) return "inventory/new";
        try {
            boolean changed = service.update(id, form, expectedUpdatedAt);
            redirect.addFlashAttribute("successMessage", changed ? "수정했어!" : "변경한 내용이 없어.");
        } catch (InvalidFoodException invalid) {
            invalid.errors().forEach((field, message) -> {
                if (field.isEmpty()) errors.reject("updateConflict", message);
                else errors.rejectValue(field, "invalid", message);
            });
            return "inventory/new";
        }
        return FoodQuantityController.url("/foods/" + masters.masterId(id), storage, warning, ended);
    }

    private void editFilters(StorageType storage, boolean warning, boolean ended, Model model) {
        model.addAttribute("selectedStorage", storage);
        model.addAttribute("savedWarning", warning);
        model.addAttribute("ended", ended);
    }

    private void collectValidationErrors(FoodCreateForm form, BindingResult errors) {
        validator.validate(form).forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            if (!errors.hasFieldErrors(field)) {
                String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
                if (errors.getTarget() != null) errors.rejectValue(field, code, violation.getMessage());
                else errors.addError(new org.springframework.validation.FieldError(errors.getObjectName(), field,
                        errors.getFieldValue(field), false, errors.resolveMessageCodes(code, field), null, violation.getMessage()));
            }
        });
        service.validationErrors(form).forEach((field, message) -> {
            if (!errors.hasFieldErrors(field)) {
                if (errors.getTarget() != null) errors.rejectValue(field, "invalid", message);
                else errors.addError(new org.springframework.validation.FieldError(errors.getObjectName(), field,
                        errors.getFieldValue(field), true, errors.resolveMessageCodes("invalid", field), null, message));
            }
        });
    }
    private void editContext(long id, OffsetDateTime expectedUpdatedAt, Model model) {
        var food = service.findById(id);
        model.addAttribute("editId", id);
        model.addAttribute("expectedUpdatedAt", expectedUpdatedAt);
        model.addAttribute("legacyQuantity", food.quantityAmount() == null ? (food.quantityText() == null ? "-" : food.quantityText()) : null);
    }
    @PostMapping("/inventory")
    String create(@ModelAttribute("foodForm") FoodCreateForm form, BindingResult errors,
            @RequestParam(defaultValue = "new") String registrationMode,
            @RequestParam(required = false) Long masterId,
            @RequestParam(required = false) Long masterVersion,
            @RequestParam(required = false) java.util.UUID registrationRequestId,
            Model model, RedirectAttributes redirect) {
        boolean existing = "existing".equals(registrationMode);
        registrationContext(existing ? "existing" : "new", masterId, masterVersion, model);
        model.addAttribute("registrationRequestId", registrationRequestId == null ? java.util.UUID.randomUUID() : registrationRequestId);
        if (!existing && registrationRequestId == null)
            errors.reject("missingRequestId", "등록 요청을 확인하지 못했어. 입력 내용을 확인하고 다시 등록해줘.");
        if (!existing && !"new".equals(registrationMode)) errors.reject("invalidMode", "등록 방식을 다시 선택해줘.");
        var validatedForm = form;
        if (existing) {
            var selected = masters.registrationChoices().stream().filter(m -> java.util.Objects.equals(m.masterId(), masterId)).findFirst();
            if (selected.isEmpty()) errors.reject("missingMaster", "추가할 기존 음식을 선택해줘.");
            else validatedForm = form.withIdentity(selected.get().foodName(), selected.get().category());
        }
        collectValidationErrors(validatedForm, errors);
        if (errors.hasErrors()) return "inventory/new";
        try {
            if (existing) service.create(validatedForm, masterId, masterVersion);
            else registrations.create(validatedForm, registrationRequestId);
        } catch (InvalidFoodException invalid) {
            invalid.errors().forEach((field, message) -> {
                if (field.isEmpty()) errors.reject("registrationConflict", message);
                else errors.rejectValue(field, "invalid", message);
            });
            return "inventory/new";
        }
        redirect.addFlashAttribute("successMessage", existing ? "구매 항목을 추가했어!" : "등록했어!");
        return existing ? "redirect:/foods/" + masterId : "redirect:/inventory";
    }
    private void registrationContext(String mode, Long masterId, Long version, Model model) {
        model.addAttribute("registrationMode", mode);
        model.addAttribute("selectedMasterId", masterId);
        model.addAttribute("masterVersion", version);
        model.addAttribute("registrationFoods", masters.registrationChoices());
    }
}
