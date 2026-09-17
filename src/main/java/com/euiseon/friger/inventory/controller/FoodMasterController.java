package com.euiseon.friger.inventory.controller;

import java.time.Clock;
import java.time.LocalDate;
import com.euiseon.friger.inventory.service.FoodMasterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        return "inventory/master";
    }
}
