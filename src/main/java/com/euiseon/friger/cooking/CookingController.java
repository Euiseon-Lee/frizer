package com.euiseon.friger.cooking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import com.euiseon.friger.account.CurrentUser;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import com.euiseon.friger.inventory.service.FoodCategoryService;
import com.euiseon.friger.inventory.controller.FoodFormValidation;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CookingController {
    private final CookingService service;
    private final FoodMasterDao masters;
    private final FoodCategoryService categories;
    private final Clock clock;
    private final CurrentUser user;
    private final FoodFormValidation formValidation;
    public CookingController(CookingService service,FoodMasterDao masters,FoodCategoryService categories,Clock clock,CurrentUser user,FoodFormValidation formValidation) {
        this.service=service;this.masters=masters;this.categories=categories;this.clock=clock;this.user=user;
        this.formValidation=formValidation;
    }
    @GetMapping("/register")
    String register() { return "cooking/register"; }
    @GetMapping("/cooking/new")
    String form(@RequestParam(required=false) UUID draft,HttpSession session,Model model) {
        var form=draft==null?null:drafts(session).get(draft);
        return edit(form==null?CookingForm.empty(LocalDate.now(clock)):form,model);
    }
    private String edit(CookingForm form,Model model) {
        // Keep the MVC binding result, including raw values from failed record-constructor binding.
        var binding=model.getAttribute(BindingResult.MODEL_KEY_PREFIX+"cookingForm");
        model.addAttribute("cookingForm",form);
        if(binding!=null) model.addAttribute(BindingResult.MODEL_KEY_PREFIX+"cookingForm",binding);
        model.addAttribute("today",LocalDate.now(clock));
        model.addAttribute("storageLabels",com.euiseon.friger.common.type.StorageType.labels());
        var choices=service.choices();
        model.addAttribute("ingredients",choices);
        String query=Objects.toString(model.getAttribute("ingredientQuery"),"").strip().toLowerCase(Locale.ROOT);
        boolean searched=Boolean.TRUE.equals(model.getAttribute("ingredientSearchPerformed")) || !query.isEmpty();
        model.addAttribute("ingredientSearchPerformed",searched);
        var matches=!searched?List.<Long>of():choices.stream()
            .filter(choice->choice.item().foodName().toLowerCase(Locale.ROOT).contains(query))
            .map(choice->choice.item().foodId()).toList();
        model.addAttribute("visibleIngredientIds",matches);
        model.addAttribute("ingredientSearchMessage",!searched?"":matches.isEmpty()?"일치하는 재료가 없어.":"검색 결과 "+matches.size()+"개");
        var selected=new HashMap<Long,CookingForm.IngredientInput>();
        for(var row:form.ingredients()) selected.put(row.foodId(),row);
        model.addAttribute("selectedIngredients",selected);
        var existingFoods=categories.displayChoices(masters.registrationChoices());
        model.addAttribute("existingFoods",existingFoods);
        var categoriesByMaster=new HashMap<Long,String>();
        for(var food:existingFoods) categoriesByMaster.put(food.masterId(),food.category());
        var ingredientCategoryLabels=new HashMap<Long,String>();
        for(var choice:choices) ingredientCategoryLabels.put(choice.item().foodId(),categoriesByMaster.get(choice.masterId()));
        model.addAttribute("ingredientCategoryLabels",ingredientCategoryLabels);
        var master=form.masterId()==null?null:masters.find(form.masterId());
        model.addAttribute("selectedMaster",master);
        model.addAttribute("categoryChoices",categories.choices());
        return "cooking/new";
    }
    @PostMapping("/cooking/preview")
    String preview(@ModelAttribute("cookingForm") CookingForm form,BindingResult binding,
            @RequestParam(required=false) String refresh,@RequestParam(required=false) String ingredientQuery,
            @RequestParam(required=false) String resultMode,@RequestParam(defaultValue="false") boolean ingredientSearchPerformed,
            HttpSession session,Model model) {
        model.addAttribute("ingredientQuery",ingredientQuery);
        model.addAttribute("ingredientSearchPerformed",ingredientSearchPerformed || "ingredients".equals(refresh));
        if(refresh!=null) {
            if(binding.hasErrors()) model.addAttribute("errorMessage","수량과 날짜 형식을 확인해줘.");
            return edit(form,model);
        }
        var food=form.foodForm();
        var master=form.masterId()==null?null:masters.find(form.masterId());
        if(master!=null) food=food.withIdentity(master.foodName(),master.category());
        else if(form.masterId()!=null) model.addAttribute("errorMessage","선택한 음식이 변경되었어. 기존 음식을 다시 선택해줘.");
        formValidation.collect(food,binding);
        if(binding.hasErrors()) {
            var inlineFields=Set.of("foodName","quantityAmount","quantityUnit","capacityText","memo","storageType","cookedAt","frozenAt","consumedAmount");
            if(binding.getFieldErrors().stream().anyMatch(error->!inlineFields.contains(error.getField())))
                model.addAttribute("errorMessage","입력한 재료와 날짜 형식을 확인해줘.");
            return edit(form,model);
        }
        if("existing".equals(resultMode) && form.masterId()==null) {
            model.addAttribute("errorMessage","만들어진 요리로 등록할 기존 음식을 검색해서 선택해줘.");
            return edit(form,model);
        }
        try {
            var preview=service.preview(form);
            UUID confirmationId = remember(drafts(session), form, prior -> prior.requestId().equals(form.requestId()));
            model.addAttribute("confirmationId", confirmationId);
            model.addAttribute("preview",preview);
            return "cooking/preview";
        } catch(InvalidFoodException e) {
            if(e.errors().containsKey("storageType")) binding.rejectValue("storageType","invalid",e.errors().get("storageType"));
            model.addAttribute("errorMessage",message(e)); return edit(form,model);
        }
    }
    @PostMapping("/cooking")
    String save(@RequestParam(required=false) UUID confirmationId,HttpSession session,Model model,RedirectAttributes redirect) {
        var form=drafts(session).get(confirmationId);
        if(form==null) { redirect.addFlashAttribute("errorMessage","이전 확인 화면이야. 최근 확인 화면에서 저장하거나 다시 입력해줘."); return "redirect:/cooking/new"; }
        try {
            UUID id=service.create(form);
            redirect.addFlashAttribute("successMessage","요리와 재료 사용량을 기록했어.");
            return "redirect:/cooking/"+id;
        } catch(InvalidFoodException e) { model.addAttribute("errorMessage",message(e)); return edit(form,model); }
    }
    @GetMapping("/cooking/{id}")
    String detail(@PathVariable UUID id,Model model) {
        var detail=service.detail(id);
        model.addAttribute("cooking",detail.record());
        model.addAttribute("ingredients",detail.ingredients());
        return "cooking/detail";
    }
    @GetMapping("/cooking/{id}/correct/{foodId}")
    String correction(@PathVariable UUID id,@PathVariable long foodId,Model model,RedirectAttributes redirect) {
        try {
            var context=service.correctionContext(id,foodId);
            model.addAttribute("context",context);
            model.addAttribute("correction",new CookingService.Correction(UUID.randomUUID(),id,foodId,
                context.record().versionNo(),context.itemVersion(),context.effectiveAmount(),null));
            return "cooking/correct";
        } catch(InvalidFoodException e) { redirect.addFlashAttribute("errorMessage",message(e)); return "redirect:/cooking/"+id; }
    }
    @PostMapping("/cooking/{id}/correct/{foodId}/preview")
    String correctionPreview(@PathVariable UUID id,@PathVariable long foodId,
            @RequestParam UUID requestId,@RequestParam long cookingVersion,@RequestParam long itemVersion,
            @RequestParam BigDecimal amount,@RequestParam(required=false) BigDecimal confirmedRemaining,
            HttpSession session,Model model,RedirectAttributes redirect) {
        var command=new CookingService.Correction(requestId,id,foodId,cookingVersion,itemVersion,amount,confirmedRemaining);
        try {
            var preview=service.correctionPreview(command);
            UUID confirmationId = remember(corrections(session), command,
                prior -> prior.cookingId().equals(id) && prior.foodId() == foodId);
            model.addAttribute("confirmationId", confirmationId);
            model.addAttribute("preview",preview);
            return "cooking/correction-preview";
        } catch(InvalidFoodException e) {
            // Fresh versions must be reviewed on the next attempt; never overwrite a concurrent change.
            redirect.addFlashAttribute("errorMessage",message(e));
            return "redirect:/cooking/"+id+"/correct/"+foodId;
        }
    }
    @PostMapping("/cooking/{id}/correct")
    String correct(@PathVariable UUID id,@RequestParam(required=false) UUID confirmationId,HttpSession session,RedirectAttributes redirect) {
        var command=corrections(session).get(confirmationId);
        if(command==null || !command.cookingId().equals(id)) {
            redirect.addFlashAttribute("errorMessage","이전 확인 화면이야. 최근 확인 화면에서 저장하거나 다시 정정해줘.");
        } else {
            try { service.correct(command); redirect.addFlashAttribute("successMessage","요리 수량과 현재 잔량을 정정했어."); }
            catch(InvalidFoodException e) { redirect.addFlashAttribute("errorMessage",message(e)); }
        }
        return "redirect:/cooking/"+id;
    }
    // Confirmation IDs identify reviewed values. Database retries use the original request ID.
    private static <T> UUID remember(Map<UUID,T> reviews, T value, Predicate<T> superseded) {
        synchronized (reviews) {
            reviews.values().removeIf(superseded);
            if (reviews.size() >= 10) reviews.remove(reviews.keySet().iterator().next());
            UUID confirmationId = UUID.randomUUID();
            reviews.put(confirmationId, value);
            return confirmationId;
        }
    }
    @SuppressWarnings("unchecked")
    private Map<UUID,CookingForm> drafts(HttpSession session) {
        String key="cookingDrafts:"+user.id();
        synchronized(session) {
            if(session.getAttribute(key)==null) session.setAttribute(key,Collections.synchronizedMap(new LinkedHashMap<UUID,CookingForm>()));
            return (Map<UUID,CookingForm>) session.getAttribute(key);
        }
    }
    @SuppressWarnings("unchecked")
    private Map<UUID,CookingService.Correction> corrections(HttpSession session) {
        String key="cookingCorrections:"+user.id();
        synchronized(session) {
            if(session.getAttribute(key)==null) session.setAttribute(key,Collections.synchronizedMap(new LinkedHashMap<UUID,CookingService.Correction>()));
            return (Map<UUID,CookingService.Correction>) session.getAttribute(key);
        }
    }
    private static String message(InvalidFoodException e) { return String.join(" ",e.errors().values()); }
}
