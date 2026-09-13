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

    public InventoryController(InventoryService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @ModelAttribute
    void choices(Model model) {
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
    String inventory(@RequestParam(required = false) StorageType storage, Model model) {
        var all = service.findActive();
        model.addAttribute("foods", all.stream().filter(food -> storage == null || food.storageType() == storage).toList());
        model.addAttribute("selectedStorage", storage);
        model.addAttribute("totalCount", all.size());
        return "inventory/list";
    }

    @GetMapping("/inventory/new")
    String newFood(Model model) {
        model.addAttribute("foodForm", FoodCreateForm.empty());
        return "inventory/new";
    }

    @GetMapping("/inventory/{id}")
    String detail(@PathVariable long id, Model model) {
        model.addAttribute("food", service.findById(id));
        return "inventory/detail";
    }

    @GetMapping("/inventory/{id}/edit")
    String editFood(@PathVariable long id, Model model, RedirectAttributes redirect) {
        var food = service.findById(id);
        if (food.status() != FoodStatus.ACTIVE) {
            redirect.addFlashAttribute("successMessage", "보관 중인 음식만 수정할 수 있어.");
            return "redirect:/inventory/" + id;
        }
        model.addAttribute("foodForm", FoodCreateForm.from(food));
        editContext(id, food.updatedAt(), model);
        return "inventory/new";
    }

    @PostMapping("/inventory/{id}/edit")
    String updateFood(@PathVariable long id, @Valid @ModelAttribute("foodForm") FoodCreateForm form, BindingResult errors,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime expectedUpdatedAt,
            Model model, RedirectAttributes redirect) {
        editContext(id, expectedUpdatedAt, model);
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
        return "redirect:/inventory/" + id;
    }

    private void editContext(long id, OffsetDateTime expectedUpdatedAt, Model model) {
        var food = service.findById(id);
        model.addAttribute("editId", id);
        model.addAttribute("expectedUpdatedAt", expectedUpdatedAt);
        model.addAttribute("legacyQuantity", food.quantityAmount() == null ? (food.quantityText() == null ? "-" : food.quantityText()) : null);
    }
    @PostMapping("/inventory")
    String create(@Valid @ModelAttribute("foodForm") FoodCreateForm form, BindingResult errors,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) return "inventory/new";
        try {
            service.create(form);
        } catch (InvalidFoodException invalid) {
            invalid.errors().forEach((field, message) -> errors.rejectValue(field, "invalid", message));
            return "inventory/new";
        }
        redirect.addFlashAttribute("successMessage", "등록했어!");
        return "redirect:/inventory";
    }
}
