package com.euiseon.friger.inventory.controller;

import java.util.*;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.service.FoodDeleteService;
import com.euiseon.friger.inventory.service.FoodMasterService;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/foods/{id}/delete")
public class FoodDeleteController {
    private final FoodDeleteService deletes;
    private final FoodMasterService masters;
    public FoodDeleteController(FoodDeleteService deletes,FoodMasterService masters) {this.deletes=deletes;this.masters=masters;}
    @GetMapping
    String page(@PathVariable long id,@RequestParam(required=false) List<Long> items,
                @RequestParam(defaultValue="false") boolean ended,Model model,RedirectAttributes redirect) {
        FoodDeleteService.Selection selection;
        try {selection=deletes.selection(id,items);}
        catch(InvalidFoodException error) {
            redirect.addFlashAttribute("successMessage",error.errors().get(""));
            return "redirect:/foods/"+id+(ended?"?ended=true":"");
        }
        model.addAttribute("master",selection.source());
        model.addAttribute("items",selection.items());
        model.addAttribute("versions",selection.versions());
        model.addAttribute("whole",selection.whole());
        model.addAttribute("historyCount",selection.historyCount());
        model.addAttribute("requestId",UUID.randomUUID());
        model.addAttribute("ended",ended);
        // Entered from a collapsed (single-item) detail: 돌아가기 returns to that item,
        // because the middle list was never on the user's path.
        model.addAttribute("backItemId",selection.items().size()==1 && masters.items(id).stream()
                .filter(item->(item.status()==FoodStatus.DEPLETED)==ended).count()==1
                ? selection.items().getFirst().foodId() : null);
        return "inventory/item-delete";
    }
    @PostMapping
    String delete(@PathVariable long id,@RequestParam long sourceId,@RequestParam long sourceVersion,
                  @RequestParam List<Long> itemIds,@RequestParam List<Long> itemVersions,
                  @RequestParam(defaultValue="false") boolean deleteMaster,
                  @RequestParam(defaultValue="false") boolean ended,
                  @RequestParam UUID requestId,RedirectAttributes redirect) {
        try {
            var result=deletes.delete(new FoodDeleteService.Command(sourceId,sourceVersion,
                    itemIds,itemVersions,deleteMaster),requestId);
            if(result.masterRemoved()) {
                redirect.addFlashAttribute("successMessage",
                        "음식과 구매 항목 "+result.itemCount()+"건, 기록 "+result.historyCount()+"건을 완전히 삭제했어.");
                return "redirect:/inventory";
            }
            redirect.addFlashAttribute("successMessage",
                    "구매 항목 "+result.itemCount()+"건과 기록 "+result.historyCount()+"건을 완전히 삭제했어.");
            return "redirect:/foods/"+id+(ended?"?ended=true":"");
        } catch(InvalidFoodException error) {
            redirect.addFlashAttribute("errorMessage",error.errors().get(""));
            return "redirect:/foods/"+id+"/delete?items="+itemIds.stream().map(String::valueOf)
                    .reduce((a,b)->a+"&items="+b).orElse("")+(ended?"&ended=true":"");
        }
    }
}
