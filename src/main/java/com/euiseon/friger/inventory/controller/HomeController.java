package com.euiseon.friger.inventory.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import com.euiseon.friger.common.type.StorageType;
import com.euiseon.friger.history.service.HistoryService;
import com.euiseon.friger.inventory.service.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final InventoryService inventory;
    private final HistoryService history;
    private final Clock clock;
    public HomeController(InventoryService inventory, HistoryService history, Clock clock) {
        this.inventory = inventory; this.history = history; this.clock = clock;
    }
    @GetMapping("/")
    String home(Model model) {
        var foods = inventory.findActive();
        var today = LocalDate.now(clock);
        model.addAttribute("today", today);
        model.addAttribute("totalCount", foods.size());
        model.addAttribute("attentionFoods", foods.stream().filter(f -> f.needsAttention(today)).toList());
        model.addAttribute("dueFoods", foods.stream().filter(f -> today.equals(f.expiredAt() != null ? f.expiredAt() : f.sellByAt())).toList());
        model.addAttribute("overviewFoods", foods.stream().filter(f -> f.needsAttention(today) || today.equals(f.expiredAt() != null ? f.expiredAt() : f.sellByAt())).toList());
        model.addAttribute("unknownDateCount", foods.stream().filter(f -> f.expiredAt() == null).count());
        var counts = new LinkedHashMap<String, Long>();
        for (var type : new StorageType[]{StorageType.ROOM, StorageType.FRIDGE, StorageType.FREEZER})
            counts.put(type.name(), foods.stream().filter(f -> f.storageType() == type).count());
        model.addAttribute("counts", counts);
        model.addAttribute("entries", history.recent(5));
        return "home";
    }
}
