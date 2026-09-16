package com.euiseon.friger.inventory.controller;

import java.util.UUID;
import java.math.BigDecimal;
import com.euiseon.friger.common.type.StorageType;
import com.euiseon.friger.inventory.service.*;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class FoodQuantityController {
    private final FoodQuantityService quantities;
    private final FoodMasterService masters;
    public FoodQuantityController(FoodQuantityService quantities,FoodMasterService masters) {
        this.quantities=quantities;this.masters=masters;
    }
    @GetMapping("/inventory/{id}/quantity")
    String preview(@PathVariable long id,@RequestParam FoodQuantityService.Action action,
                   @RequestParam(required=false) StorageType storage,@RequestParam(defaultValue="false") boolean warning,
                   @RequestParam(defaultValue="false") boolean ended,Model model) {
        var preview=quantities.preview(id);
        model.addAttribute("preview",preview);
        model.addAttribute("action",action.name());
        model.addAttribute("requestId",UUID.randomUUID());
        model.addAttribute("selectedStorage",storage);model.addAttribute("savedWarning",warning);model.addAttribute("ended",ended);
        return "inventory/quantity";
    }
    @PostMapping("/inventory/{id}/quantity")
    String apply(@PathVariable long id,@RequestParam FoodQuantityService.Action action,@RequestParam long version,
                 @RequestParam(required=false) Long historyId,@RequestParam UUID requestId,
                 @RequestParam(required=false) String quantityAmount,
                 @RequestParam(required=false) StorageType storage,@RequestParam(defaultValue="false") boolean warning,
                 @RequestParam(defaultValue="false") boolean ended,RedirectAttributes redirect) {
        try {
            BigDecimal selected=null;
            if(action!=FoodQuantityService.Action.CANCEL || quantityAmount!=null) {
                if(quantityAmount==null || !quantityAmount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?"))
                    throw new InvalidFoodException(java.util.Map.of("","처리할 수량을 숫자로 입력해줘. 소수점 둘째 자리까지 사용할 수 있어."));
                selected=new BigDecimal(quantityAmount);
            }
            quantities.apply(id,action,version,historyId,requestId,selected);
            redirect.addFlashAttribute("successMessage",action==FoodQuantityService.Action.CANCEL ? "처리를 취소했어. 다시 보관 중이야." : action==FoodQuantityService.Action.CONSUME ? "먹은 것으로 기록했어." : "버린 것으로 기록했어.");
            if(action!=FoodQuantityService.Action.CANCEL) redirect.addFlashAttribute("processedItemId",id);
            return url("/foods/"+masters.masterId(id),storage,warning,action==FoodQuantityService.Action.CANCEL ? false : ended);
        } catch(InvalidFoodException error) {
            redirect.addFlashAttribute("successMessage",error.errors().get(""));
            return url("/inventory/"+id,storage,warning,ended);
        }
    }
    private String url(String path,StorageType storage,boolean warning,boolean ended) {
        var uri=UriComponentsBuilder.fromPath(path);
        if(storage!=null) uri.queryParam("storage",storage);
        if(warning) uri.queryParam("warning",true);
        if(ended) uri.queryParam("ended",true);
        return "redirect:"+uri.build().toUriString();
    }
}
