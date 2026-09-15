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
@RequestMapping("/inventory/{id}/move")
public class ItemMoveController {
    private final ItemMoveService moves;
    private final InventoryService inventory;
    private final FoodMasterService masters;
    public ItemMoveController(ItemMoveService moves, InventoryService inventory, FoodMasterService masters) {
        this.moves=moves; this.inventory=inventory; this.masters=masters;
    }
    @GetMapping
    String page(@PathVariable long id, Model model) {
        model.addAttribute("food",inventory.findById(id));
        return "inventory/item-move";
    }
    @GetMapping("/choices") @ResponseBody
    List<FoodMasterDao.RegistrationChoice> choices(@PathVariable long id,@RequestParam String q) {
        long source=masters.masterId(id);
        if(q.isBlank()) return List.of();
        String query=q.strip().toLowerCase(Locale.ROOT);
        return masters.registrationChoices().stream().filter(c -> c.masterId()!=source
                && c.foodName().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    @GetMapping("/preview") @ResponseBody
    ResponseEntity<?> preview(@PathVariable long id,@RequestParam ItemMoveService.Mode mode,
                              @RequestParam(required=false) Long targetId,
                              @RequestParam(required=false) String newName,
                              @RequestParam(required=false) String newCategory) {
        try {return ResponseEntity.ok(moves.preview(id,mode,targetId,newName,newCategory));}
        catch(InvalidFoodException error) {return ResponseEntity.badRequest().body(Map.of("message",error.errors().get("")));}
    }
    @PostMapping
    String move(@PathVariable long id,@RequestParam ItemMoveService.Mode mode,
                @RequestParam(required=false) Long targetId,@RequestParam(required=false) String newName,
                @RequestParam(required=false) String newCategory,@RequestParam long sourceId,
                @RequestParam long sourceVersion,@RequestParam(required=false) Long targetVersion,
                @RequestParam UUID requestId,RedirectAttributes redirect) {
        try {
            moves.move(id,new ItemMoveService.Command(mode,targetId,newName,newCategory,sourceId,sourceVersion,targetVersion),requestId);
            redirect.addFlashAttribute("successMessage","구매 항목을 이동했어. 수량과 기록은 그대로 보관했어.");
            return "redirect:/inventory/"+id;
        } catch(InvalidFoodException error) {
            redirect.addFlashAttribute("errorMessage",error.errors().get(""));
            return "redirect:/inventory/"+id+"/move";
        }
    }
}
