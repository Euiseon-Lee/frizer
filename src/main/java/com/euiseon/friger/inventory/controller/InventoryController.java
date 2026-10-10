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
    private final com.euiseon.friger.inventory.service.FoodCategoryService categories;

    public InventoryController(InventoryService service, Clock clock, com.euiseon.friger.inventory.service.FoodMasterService masters,
                               jakarta.validation.Validator validator,
                               com.euiseon.friger.inventory.service.FoodRegistrationService registrations,
                               com.euiseon.friger.inventory.service.FoodQuantityService quantities,
                               com.euiseon.friger.inventory.service.FoodCategoryService categories) {
        this.service = service;
        this.clock = clock;
        this.masters = masters;
        this.validator = validator;
        this.registrations = registrations;
        this.quantities = quantities;
        this.categories = categories;
    }

    @ModelAttribute
    void choices(Model model, jakarta.servlet.http.HttpSession session) {
        com.euiseon.friger.inventory.bulk.BulkRegistrationController.owner(session);
        model.addAttribute("storageLabels", labels(new StorageType[]{StorageType.ROOM, StorageType.FRIDGE, StorageType.FREEZER}, "실온", "냉장실", "냉동실"));
        model.addAttribute("sourceLabels", labels(FoodSourceType.values(), "장보기", "배달 잔반", "직접 조리", "부모님의 은혜", "기타"));
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("categoryErrors", new LinkedHashMap<String,String>());
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
        var master = masterId == null ? null : masters.find(masterId);
        if (master != null) {
            form = form.withIdentity(master.foodName(), master.category());
            version = master.versionNo();
        }
        model.addAttribute("foodForm", form);
        model.addAttribute("registrationRequestId", java.util.UUID.randomUUID());
        registrationContext(masterId != null || "existing".equals(registrationMode) ? "existing" : "new", masterId, version, model);
        categoryContext(master == null ? null : master.categoryMajorCode(), master == null ? null : master.categoryMinorCode(),
                master, master != null && master.categoryMajorCode() != null, false, model);
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
        long masterId = masters.masterId(id);
        model.addAttribute("masterId", masterId);
        // A group with a single item in this view collapses the middle list: the food
        // card links straight here, so the detail exposes the group actions itself.
        model.addAttribute("soleItem", masters.items(masterId).stream()
                .filter(item -> (item.status() == FoodStatus.DEPLETED) == endedView).count() == 1);
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
        model.addAttribute("foodForm", FoodCreateForm.from(food, LocalDate.now(clock)));
        editContext(id, food.updatedAt(), model);
        var master = masters.find(masters.masterId(id));
        categoryContext(master.categoryMajorCode(), master.categoryMinorCode(), master, false, false, model);
        return "inventory/new";
    }

    @PostMapping("/inventory/{id}/edit")
    String updateFood(@PathVariable long id, @ModelAttribute("foodForm") FoodCreateForm form, BindingResult errors,
            @RequestParam(required = false) String categoryMajorCode, @RequestParam(required = false) String categoryMinorCode,
            @RequestParam(defaultValue = "save") String categoryAction,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime expectedUpdatedAt,
            @RequestParam(required = false) StorageType storage,
            @RequestParam(defaultValue = "false") boolean warning, @RequestParam(defaultValue = "false") boolean ended,
            Model model, RedirectAttributes redirect) {
        editFilters(storage, warning, ended, model);
        editContext(id, expectedUpdatedAt, model);
        var master = masters.find(masters.masterId(id));
        categoryContext(categoryMajorCode, categoryMinorCode, master, false, "refresh".equals(categoryAction), model);
        if ("refresh".equals(categoryAction)) return "inventory/new";
        collectValidationErrors(form, errors);
        if (errors.hasErrors()) {
            categoryValidation(categoryMajorCode, categoryMinorCode, master, false, model);
            return "inventory/new";
        }
        try {
            boolean changed = service.updateCategorized(id, form, expectedUpdatedAt, categoryMajorCode, categoryMinorCode);
            redirect.addFlashAttribute("successMessage", changed ? "수정했어!" : "변경한 내용이 없어.");
        } catch (InvalidFoodException invalid) {
            rejectInvalid(invalid, errors, model);
            return "inventory/new";
        }
        return FoodQuantityController.url("/foods/" + masters.masterId(id), storage, warning, ended);
    }

    @PostMapping("/inventory/{id}/warning")
    String warning(@PathVariable long id,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate warningPausedUntil,
            @RequestParam(defaultValue="false") boolean warningForever,
            @RequestParam(defaultValue="false") boolean resume,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime expectedUpdatedAt,
            @RequestParam(required=false) StorageType storage,
            @RequestParam(defaultValue="false") boolean warning,
            @RequestParam(defaultValue="false") boolean ended, RedirectAttributes redirect) {
        try {
            service.changeWarning(id,warningPausedUntil,warningForever,resume,expectedUpdatedAt);
            redirect.addFlashAttribute("successMessage",resume ? "경고 알림을 다시 켰어." : "경고 알림 설정을 저장했어.");
        } catch (InvalidFoodException invalid) {
            redirect.addFlashAttribute("warningError",String.join(" ",invalid.errors().values()));
            redirect.addFlashAttribute("warningDialogOpen",true);
            redirect.addFlashAttribute("warningUntilInput",warningPausedUntil);
            redirect.addFlashAttribute("warningForeverInput",warningForever);
        }
        return FoodQuantityController.url("/inventory/"+id,storage,warning,ended);
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
            @RequestParam(required = false) String categoryMajorCode, @RequestParam(required = false) String categoryMinorCode,
            @RequestParam(defaultValue = "save") String categoryAction,
            @RequestParam(defaultValue = "new") String registrationMode,
            @RequestParam(required = false) Long masterId,
            @RequestParam(required = false) Long masterVersion,
            @RequestParam(required = false) java.util.UUID registrationRequestId,
            Model model, RedirectAttributes redirect) {
        boolean existing = "existing".equals(registrationMode);
        registrationContext(existing ? "existing" : "new", masterId, masterVersion, model);
        model.addAttribute("registrationRequestId", registrationRequestId == null ? java.util.UUID.randomUUID() : registrationRequestId);
        @SuppressWarnings("unchecked")
        var registrationFoods = (java.util.List<com.euiseon.friger.inventory.dao.FoodMasterDao.RegistrationChoice>) model.getAttribute("registrationFoods");
        var selectedMaster = existing && masterId != null
                ? registrationFoods.stream().filter(m -> m.masterId() == masterId).findFirst().orElse(null) : null;
        var master = selectedMaster == null ? null : masters.find(masterId);
        boolean inherited = master != null && master.categoryMajorCode() != null;
        categoryContext(categoryMajorCode, categoryMinorCode, master, inherited, "refresh".equals(categoryAction), model);
        if ("refresh".equals(categoryAction)) return "inventory/new";
        if ((existing || "new".equals(registrationMode)) && !errors.hasErrors()
                && registrations.completedCategorizedRequest(form, existing ? masterId : null, existing ? masterVersion : null,
                    categoryMajorCode, categoryMinorCode, registrationRequestId)) {
            redirect.addFlashAttribute("successMessage", "이미 등록한 내용이야.");
            return existing && master != null ? "redirect:/foods/" + masterId : "redirect:/inventory";
        }
        if (!existing && "new".equals(registrationMode) && !errors.hasErrors()
                && registrations.completedLegacyRequest(form, registrationRequestId)) {
            redirect.addFlashAttribute("successMessage", "이미 등록한 내용이야.");
            return "redirect:/inventory";
        }
        if (registrationRequestId == null)
            errors.reject("missingRequestId", "등록 요청을 확인하지 못했어. 입력 내용을 확인하고 다시 등록해줘.");
        if (!existing && !"new".equals(registrationMode)) errors.reject("invalidMode", "등록 방식을 다시 선택해줘.");
        var validatedForm = form;
        if (existing) {
            if (master == null) errors.reject("missingMaster", "추가할 기존 음식을 선택해줘.");
            else validatedForm = form.withIdentity(master.foodName(), master.category());
        }
        collectValidationErrors(validatedForm, errors);
        if (errors.hasErrors()) {
            categoryValidation(categoryMajorCode, categoryMinorCode, master, inherited, model);
            return "inventory/new";
        }
        try {
            registrations.createCategorized(form, existing ? masterId : null, existing ? masterVersion : null,
                    categoryMajorCode, categoryMinorCode, registrationRequestId);
        } catch (InvalidFoodException invalid) {
            rejectInvalid(invalid, errors, model);
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

    private void categoryContext(String major, String minor, com.euiseon.friger.inventory.entity.FoodMaster current,
                                 boolean inherited, boolean refresh, Model model) {
        boolean edit = model.containsAttribute("editId") && current != null;
        var choices = categories.choicesWithCurrent(edit ? current.categoryMajorCode() : null,
                edit ? current.categoryMinorCode() : null);
        String selectedMajor = major == null ? "" : major.strip();
        String selectedMinor = minor == null ? "" : minor.strip();
        var selected = choices.stream().filter(c -> c.code().equals(selectedMajor)).findFirst().orElse(null);
        if (refresh && (selected == null || selected.minors().stream().noneMatch(m -> m.code().equals(minor)))) selectedMinor = "";
        model.addAttribute("categoryMajorCode", selectedMajor);
        model.addAttribute("categoryMinorCode", selectedMinor);
        model.addAttribute("categoryChoices", choices);
        model.addAttribute("categoryMinors", selected == null ? java.util.List.of() : selected.minors());
        model.addAttribute("categoryNeedsMinor", selected == null || selected.requiresMinor());
        model.addAttribute("categoryInherited", inherited);
        model.addAttribute("categoryInheritedLabel", inherited ? current.category() : "");
        model.addAttribute("categoryLegacyLabel", current != null && current.categoryMajorCode() == null ? current.category() : null);
        model.addAttribute("categoryRefresh", refresh);
        model.addAttribute("categoryUnknownMajor", !selectedMajor.isEmpty() && selected == null);
        String minorValue = selectedMinor;
        var child = selected == null ? null : selected.minors().stream().filter(m -> m.code().equals(minorValue)).findFirst().orElse(null);
        model.addAttribute("categoryUnknownMinor", !selectedMinor.isEmpty() && child == null);
        model.addAttribute("categoryExample", child != null ? categoryExample(child.label(), child.example())
                : selected != null && !selected.requiresMinor() ? categoryExample(selected.label(), selected.example()) : "");
    }

    private static String categoryExample(String label, String example) {
        return "예) " + java.util.Arrays.stream((label + ", " + example).replaceFirst("\\s+등$", "").split("[·,]"))
                .map(String::strip).filter(value -> !value.isEmpty()).distinct()
                .collect(java.util.stream.Collectors.joining(", ")) + " 등";
    }

    @SuppressWarnings("unchecked")
    private void rejectInvalid(InvalidFoodException invalid, BindingResult errors, Model model) {
        var categoryErrors = (Map<String,String>) model.getAttribute("categoryErrors");
        invalid.errors().forEach((field, message) -> {
            if (field.equals("categoryMajorCode") || field.equals("categoryMinorCode")) categoryErrors.put(field, message);
            else if (field.isEmpty()) errors.reject("conflict", message);
            else errors.rejectValue(field, "invalid", message);
        });
    }

    @SuppressWarnings("unchecked")
    private void categoryValidation(String major, String minor, com.euiseon.friger.inventory.entity.FoodMaster current,
                                    boolean inherited, Model model) {
        if (inherited || (current != null && current.categoryMajorCode() != null
                && java.util.Objects.equals(current.categoryMajorCode(), major)
                && java.util.Objects.equals(current.categoryMinorCode(), minor == null || minor.isBlank() ? null : minor))) return;
        try { categories.requireSelection(major, minor); }
        catch (InvalidFoodException invalid) { ((Map<String,String>) model.getAttribute("categoryErrors")).putAll(invalid.errors()); }
    }
}
