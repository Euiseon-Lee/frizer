package com.euiseon.friger.inventory.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.euiseon.friger.common.type.FreezeType;
import com.euiseon.friger.common.type.StorageType;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FoodSplitController {
    private final FoodQuantityService quantities;
    private final FoodSplitService splits;
    private final java.time.Clock clock;
    public FoodSplitController(FoodQuantityService quantities, FoodSplitService splits, java.time.Clock clock) {
        this.quantities=quantities; this.splits=splits; this.clock=clock;
    }
    @GetMapping("/inventory/{id}/split")
    String preview(@PathVariable long id,
                   @RequestParam(required=false) StorageType storage,@RequestParam(defaultValue="false") boolean warning,
                   @RequestParam(defaultValue="false") boolean ended,Model model) {
        var preview=quantities.preview(id);
        model.addAttribute("preview",preview);
        model.addAttribute("today",LocalDate.now(clock));
        model.addAttribute("requestId",UUID.randomUUID());
        model.addAttribute("selectedStorage",storage);model.addAttribute("savedWarning",warning);model.addAttribute("ended",ended);
        return "inventory/split";
    }
    @PostMapping("/inventory/{id}/split")
    String split(@PathVariable long id,@RequestParam long version,@RequestParam UUID requestId,
                 @RequestParam(required=false) String quantityAmount,
                 @RequestParam(required=false) StorageType newStorage,
                 @RequestParam(required=false) FreezeType freezeType,
                 @RequestParam(required=false) LocalDate frozenAt,@RequestParam(defaultValue="false") boolean freezeToday,
                 @RequestParam(required=false) StorageType storage,@RequestParam(defaultValue="false") boolean warning,
                 @RequestParam(defaultValue="false") boolean ended,RedirectAttributes redirect) {
        try {
            if(quantityAmount==null || !quantityAmount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?"))
                throw new InvalidFoodException(java.util.Map.of("","나눌 수량을 숫자로 입력해줘. 소수점 둘째 자리까지 사용할 수 있어."));
            long child=splits.split(id,new FoodSplitService.Command(new BigDecimal(quantityAmount),
                    newStorage,freezeType,frozenAt,freezeToday),version,requestId);
            redirect.addFlashAttribute("successMessage","수량을 분리해서 저장했어.");
            return FoodQuantityController.url("/inventory/"+child,storage,warning,ended);
        } catch(InvalidFoodException error) {
            redirect.addFlashAttribute("successMessage",error.errors().get(""));
            return FoodQuantityController.url("/inventory/"+id,storage,warning,ended);
        }
    }
}
