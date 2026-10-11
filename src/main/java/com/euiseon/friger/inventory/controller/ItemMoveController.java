package com.euiseon.friger.inventory.controller;

import java.util.*;
import com.euiseon.friger.inventory.service.*;
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
    private final FoodCategoryService categories;
    public ItemMoveController(ItemMoveService moves, FoodMasterService masters, FoodCategoryService categories) {
        this.moves=moves; this.masters=masters; this.categories=categories;
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
        // Entered from a collapsed (single-item) detail: 돌아가기 returns to that item,
        // because the middle list was never on the user's path.
        model.addAttribute("backItemId",(ended?selection.endedCount():selection.activeCount())==1
                && selection.items().size()==1 ? selection.items().getFirst().foodId() : null);
        model.addAttribute("itemsParam",String.join(",",selection.items().stream()
                .map(item->String.valueOf(item.foodId())).toList()));
        // The whole target list ships with the page; the browser filters it locally.
        model.addAttribute("moveChoices",masters.registrationChoices().stream()
                .filter(choice->choice.masterId()!=id).toList());
        model.addAttribute("categoryChoices",categories.choices());
        model.addAttribute("categoryErrors",Map.of());
        model.addAttribute("categoryMinors",List.of());
        model.addAttribute("categoryMajorCode",""); model.addAttribute("categoryMinorCode","");
        model.addAttribute("categoryInherited",false); model.addAttribute("categoryRefresh",false);
        model.addAttribute("categoryNeedsMinor",true); model.addAttribute("categoryUnknownMajor",false);
        model.addAttribute("categoryUnknownMinor",false);
        return "inventory/item-move";
    }
    @GetMapping("/preview") @ResponseBody
    ResponseEntity<?> preview(@PathVariable long id,@RequestParam List<Long> items,
                              @RequestParam ItemMoveService.Mode mode,
                              @RequestParam(required=false) Long targetId,
                              @RequestParam(required=false) String newName,
                              @RequestParam(required=false) String categoryMajorCode,
                              @RequestParam(required=false) String categoryMinorCode) {
        try {return ResponseEntity.ok(moves.previewCategorized(id,items,mode,targetId,newName,categoryMajorCode,categoryMinorCode));}
        catch(InvalidFoodException error) {return ResponseEntity.badRequest().body(Map.of("message",String.join(" ",error.errors().values())));}
    }
    @PostMapping
    String move(@PathVariable long id,@RequestParam ItemMoveService.Mode mode,
                @RequestParam(required=false) Long targetId,@RequestParam(required=false) String newName,
                @RequestParam(required=false) String newCategory,@RequestParam long sourceId,
                @RequestParam long sourceVersion,@RequestParam(required=false) Long targetVersion,
                @RequestParam List<Long> itemIds,@RequestParam(defaultValue="false") boolean whole,
                @RequestParam(defaultValue="false") boolean ended,
                @RequestParam(required=false) String categoryContract,
                @RequestParam(required=false) String categoryMajorCode,
                @RequestParam(required=false) String categoryMinorCode,
                @RequestParam UUID requestId,RedirectAttributes redirect) {
        try {
            if(id!=sourceId) throw new InvalidFoodException(Map.of("","병합할 원본 음식을 다시 확인해줘."));
            var command=new ItemMoveService.Command(mode,targetId,newName,"v1".equals(categoryContract)?null:newCategory,
                    sourceId,sourceVersion,targetVersion,itemIds,whole);
            var result="v1".equals(categoryContract)?moves.moveCategorized(command,requestId,categoryMajorCode,categoryMinorCode)
                    :moves.replayLegacy(command,requestId);
            redirect.addFlashAttribute("successMessage",mode==ItemMoveService.Mode.NEW
                    ? "구매 항목 "+itemIds.size()+"건을 새로운 음식으로 변경했어.\n수량과 기록은 그대로 보관했어."
                    : whole
                    ? "음식을 통째로 병합했어.\n개별 구매 항목과 기록은 그대로 보관했어."
                    : "구매 항목 "+itemIds.size()+"건을 병합했어.\n수량과 기록은 그대로 보관했어.");
            long destination=result.sourceRemoved()?result.targetId():id;
            return "redirect:/foods/"+destination+(ended && !result.sourceRemoved()?"?ended=true":"");
        } catch(InvalidFoodException error) {
            redirect.addFlashAttribute("errorMessage",String.join(" ",error.errors().values()));
            redirect.addFlashAttribute("moveDraft",Map.of("mode",mode.name(),"targetId",targetId==null?"":targetId.toString(),
                    "name",newName==null?"":newName,"major",categoryMajorCode==null?"":categoryMajorCode,"minor",categoryMinorCode==null?"":categoryMinorCode));
            return "redirect:/foods/"+id+"/move?items="+itemIds.stream().map(String::valueOf)
                    .reduce((a,b)->a+"&items="+b).orElse("")+(ended?"&ended=true":"");
        }
    }
}
