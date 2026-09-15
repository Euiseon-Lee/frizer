package com.euiseon.friger.inventory.controller;

import java.util.UUID;
import java.time.Clock;
import java.time.LocalDate;
import com.euiseon.friger.inventory.service.FoodMasterService;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FoodMasterController {
    private final FoodMasterService masters;
    private final Clock clock;
    private final com.euiseon.friger.inventory.service.FoodQuantityService quantities;
    public FoodMasterController(FoodMasterService masters, Clock clock, com.euiseon.friger.inventory.service.FoodQuantityService quantities) {
        this.masters=masters;
        this.clock=clock;
        this.quantities=quantities;
    }
    @GetMapping("/foods/{id}")
    String detail(@PathVariable long id,
                  @RequestParam(required=false) com.euiseon.friger.common.type.StorageType storage,
                  @RequestParam(defaultValue="false") boolean ended,
                  @RequestParam(defaultValue="false") boolean warning, Model model) {
        var today = LocalDate.now(clock);
        model.addAttribute("today",today);
        model.addAttribute("selectedStorage",ended ? null : storage);
        model.addAttribute("warningOnly",!ended && warning);
        model.addAttribute("savedWarning",warning);
        model.addAttribute("ended",ended);
        if (ended) model.addAttribute("endedSummaries", quantities.endedSummaries(id));
        model.addAttribute("master",masters.find(id));
        model.addAttribute("items",masters.items(id).stream()
                .filter(food -> ended == (food.status() != com.euiseon.friger.common.type.FoodStatus.ACTIVE))
                .filter(food -> ended || storage == null || food.storageType() == storage)
                .filter(food -> ended || !warning || food.needsReview(today))
                .toList());
        model.addAttribute("canMerge",!masters.choices(id).isEmpty());
        return "inventory/master";
    }
    @GetMapping("/foods/{id}/merge")
    String preview(@PathVariable long id,@RequestParam(required=false) Long targetId,Model model,
                   RedirectAttributes redirect) {
        model.addAttribute("master",masters.find(id));
        var choices=masters.choices(id);
        if(choices.isEmpty()) return "redirect:/foods/"+id;
        model.addAttribute("choices",choices);
        if(targetId!=null) {
            try {model.addAttribute("preview",masters.preview(id,targetId));}
            catch(InvalidFoodException invalid) {
                redirect.addFlashAttribute("errorMessage",invalid.errors().get(""));
                return "redirect:/foods/"+id+"/merge";
            }
            model.addAttribute("requestId",UUID.randomUUID());
        }
        return "inventory/merge";
    }
    @PostMapping("/foods/{id}/merge")
    String merge(@PathVariable long id,@RequestParam long targetId,@RequestParam long sourceVersion,
                 @RequestParam long targetVersion,@RequestParam UUID requestId,RedirectAttributes redirect) {
        try {
            masters.merge(id,targetId,sourceVersion,targetVersion,requestId);
            redirect.addFlashAttribute("successMessage","음식을 이동했어. 개별 구매 항목과 기록은 그대로 보관했어.");
            return "redirect:/inventory";
        } catch(InvalidFoodException invalid) {
            redirect.addFlashAttribute("successMessage",invalid.errors().get(""));
            return "redirect:/inventory";
        }
    }
}
