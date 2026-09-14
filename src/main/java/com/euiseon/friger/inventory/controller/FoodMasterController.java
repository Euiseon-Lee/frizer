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
    public FoodMasterController(FoodMasterService masters, Clock clock) {
        this.masters=masters;
        this.clock=clock;
    }
    @GetMapping("/foods/{id}")
    String detail(@PathVariable long id,Model model) {
        model.addAttribute("today",LocalDate.now(clock));
        model.addAttribute("master",masters.find(id));
        model.addAttribute("items",masters.items(id));
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
            redirect.addFlashAttribute("successMessage","음식을 합쳤어. 개별 구매 항목과 기록은 그대로 보관했어.");
            return "redirect:/inventory";
        } catch(InvalidFoodException invalid) {
            redirect.addFlashAttribute("successMessage",invalid.errors().get(""));
            return "redirect:/inventory";
        }
    }
}
