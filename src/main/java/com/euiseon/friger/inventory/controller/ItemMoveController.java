package com.euiseon.friger.inventory.controller;

import java.util.*;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/foods/{id}/move")
public class ItemMoveController {
    private final ItemMoveService moves;
    private final FoodMasterService masters;
    public ItemMoveController(ItemMoveService moves, FoodMasterService masters) {
        this.moves=moves; this.masters=masters;
    }
    @GetMapping
    String page(@PathVariable long id,@RequestParam(required=false) List<Long> items,
                @RequestParam(defaultValue="false") boolean ended,Model model,RedirectAttributes redirect) {
        ItemMoveService.Selection selection;
        try {selection=moves.selection(id,items);}
        catch(InvalidFoodException error) {
            redirect.addFlashAttribute("successMessage",error.errors().get(""));
            return "redirect:/foods/"+id+(ended?"?ended=true":"");
        }
        model.addAttribute("master",selection.source());
        model.addAttribute("items",selection.items());
        model.addAttribute("whole",selection.whole());
        model.addAttribute("endedCount",selection.endedCount());
        model.addAttribute("activeCount",selection.activeCount());
        model.addAttribute("requestId",UUID.randomUUID());
        model.addAttribute("ended",ended);
        model.addAttribute("itemsParam",String.join(",",selection.items().stream()
                .map(item->String.valueOf(item.foodId())).toList()));
        return "inventory/item-move";
    }
    @GetMapping("/choices") @ResponseBody
    List<FoodMasterDao.RegistrationChoice> choices(@PathVariable long id,@RequestParam String q) {
        if(q.isBlank()) return List.of();
        String query=q.strip().toLowerCase(Locale.ROOT);
        return masters.registrationChoices().stream().filter(c -> c.masterId()!=id
                && c.foodName().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    @GetMapping("/preview") @ResponseBody
    ResponseEntity<?> preview(@PathVariable long id,@RequestParam List<Long> items,
                              @RequestParam ItemMoveService.Mode mode,
                              @RequestParam(required=false) Long targetId,
                              @RequestParam(required=false) String newName,
                              @RequestParam(required=false) String newCategory) {
        try {return ResponseEntity.ok(moves.preview(id,items,mode,targetId,newName,newCategory));}
        catch(InvalidFoodException error) {return ResponseEntity.badRequest().body(Map.of("message",error.errors().get("")));}
    }
    @PostMapping
    String move(@PathVariable long id,@RequestParam ItemMoveService.Mode mode,
                @RequestParam(required=false) Long targetId,@RequestParam(required=false) String newName,
                @RequestParam(required=false) String newCategory,@RequestParam long sourceId,
                @RequestParam long sourceVersion,@RequestParam(required=false) Long targetVersion,
                @RequestParam List<Long> itemIds,@RequestParam(defaultValue="false") boolean whole,
                @RequestParam(defaultValue="false") boolean ended,
                @RequestParam UUID requestId,RedirectAttributes redirect) {
        try {
            var result=moves.move(new ItemMoveService.Command(mode,targetId,newName,newCategory,
                    sourceId,sourceVersion,targetVersion,itemIds,whole),requestId);
            redirect.addFlashAttribute("successMessage",mode==ItemMoveService.Mode.NEW
                    ? "구매 항목 "+itemIds.size()+"건을 새로운 음식으로 변경했어.\n수량과 기록은 그대로 보관했어."
                    : whole
                    ? "음식을 통째로 병합했어.\n개별 구매 항목과 기록은 그대로 보관했어."
                    : "구매 항목 "+itemIds.size()+"건을 병합했어.\n수량과 기록은 그대로 보관했어.");
            long destination=result.sourceRemoved()?result.targetId():id;
            return "redirect:/foods/"+destination+(ended && !result.sourceRemoved()?"?ended=true":"");
        } catch(InvalidFoodException error) {
            redirect.addFlashAttribute("errorMessage",error.errors().get(""));
            return "redirect:/foods/"+id+"/move?items="+itemIds.stream().map(String::valueOf)
                    .reduce((a,b)->a+"&items="+b).orElse("")+(ended?"&ended=true":"");
        }
    }
}
