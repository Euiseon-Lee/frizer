package com.euiseon.friger.cooking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.entity.FoodItem;
import com.euiseon.friger.inventory.exception.*;
import com.euiseon.friger.inventory.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CookingService {
    private final CookingDao cooking;
    private final InventoryDao inventory;
    private final FoodMasterDao masters;
    private final FoodQuantityDao quantities;
    private final InventoryService foods;
    private final FoodQuantityService consumption;
    private final FoodCategoryService categories;
    private final ObjectMapper json;
    private final Clock clock;
    public CookingService(CookingDao cooking,InventoryDao inventory,FoodMasterDao masters,
            FoodQuantityDao quantities,InventoryService foods,FoodQuantityService consumption,
            FoodCategoryService categories,ObjectMapper json,Clock clock) {
        this.cooking=cooking;this.inventory=inventory;this.masters=masters;this.quantities=quantities;
        this.foods=foods;this.consumption=consumption;this.categories=categories;this.json=json;this.clock=clock;
    }
    public record IngredientPreview(FoodItem item,long version,BigDecimal used,BigDecimal remaining) {
        public String usedText() { return CookingDao.format(used,item.quantityUnit()); }
        public String remainingText() { return CookingDao.format(remaining,item.quantityUnit()); }
    }
    public record Preview(CookingForm form,String name,List<IngredientPreview> ingredients) {
        public String quantity() { return CookingDao.format(form.quantityAmount(),form.quantityUnit()); }
        public String remaining() { return CookingDao.format(form.quantityAmount().subtract(
            form.consumedAmount()==null?BigDecimal.ZERO:form.consumedAmount()),form.quantityUnit()); }
    }
    public record Choice(FoodItem item,long version,long masterId) {}
    public record Detail(CookingDao.Record record,List<CookingDao.Ingredient> ingredients) {}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Choice> choices() {
        var versions=quantities.activeVersions().stream().collect(Collectors.toMap(FoodQuantityDao.Version::foodId,version->version));
        return inventory.findActive().stream().filter(i->i.quantityAmount()!=null && i.quantityUnit()!=null)
            .map(i->new Choice(i,versions.get(i.foodId()).versionNo(),versions.get(i.foodId()).masterId())).toList();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview preview(CookingForm form) {
        String name=validateForm(form);
        var rows=new ArrayList<IngredientPreview>();
        for(var input:form.selected()) rows.add(validateIngredient(input));
        return new Preview(form,name,List.copyOf(rows));
    }
    private String validateForm(CookingForm form) {
        if(form==null || form.requestId()==null) throw invalid("입력 화면에서 다시 시작해줘.");
        if(form.selected().isEmpty() || form.selected().size()>30) throw invalid("재료를 1개 이상, 30개 이하로 선택해줘.");
        if(form.selected().stream().map(CookingForm.IngredientInput::foodId).distinct().count()!=form.selected().size())
            throw invalid("같은 보관 항목을 두 번 선택할 수 없어.");
        if(form.cookedAt()==null || form.cookedAt().isAfter(LocalDate.now(clock))) throw invalid("조리일은 오늘 또는 이전 날짜로 입력해줘.");
        if(form.frozenAt()!=null && form.frozenAt().isBefore(form.cookedAt())) throw invalid("냉동 보관일은 조리일 이후로 입력해줘.");
        var food=form.foodForm();
        if(form.masterId()!=null) {
            var master=masters.find(form.masterId());
            if(master==null || form.masterVersion()==null || master.versionNo()!=form.masterVersion())
                throw invalid("선택한 음식이 변경되었어. 기존 음식을 다시 선택해줘.");
            food=food.withIdentity(master.foodName(),master.category());
            if(master.categoryMajorCode()==null) categories.requireSelection(form.categoryMajorCode(),form.categoryMinorCode());
        } else categories.requireSelection(form.categoryMajorCode(),form.categoryMinorCode());
        var errors=foods.validationErrors(food);
        if(!errors.isEmpty()) throw new InvalidFoodException(errors);
        if(form.consumedAmount()!=null) {
            amount(form.consumedAmount(),true);
            if(form.consumedAmount().compareTo(form.quantityAmount())>0) throw invalid("먹은 양은 요리 수량보다 클 수 없어.");
        }
        return food.foodName().strip();
    }
    private IngredientPreview validateIngredient(CookingForm.IngredientInput input) {
        var item=inventory.findById(input.foodId());
        if(item==null) throw new FoodNotFoundException(input.foodId());
        amount(input.amount(),false);
        var state=quantities.state(input.foodId());
        if(input.version()==null || state.versionNo()!=input.version()) throw invalid(item.foodName()+"의 재고가 바뀌었어. 재료를 다시 확인해줘.");
        if(item.status()!=FoodStatus.ACTIVE || item.quantityAmount()==null || item.quantityUnit()==null)
            throw invalid(item.foodName()+"의 현재 수량과 단위를 먼저 확인해줘.");
        // Using an ingredient preserves its group and category, including an unclassified group.
        // The result group's required category is validated and saved with the cooking output.
        if(input.amount().compareTo(item.quantityAmount())>0) throw invalid(item.foodName()+"의 사용량이 현재 잔량보다 많아.");
        return new IngredientPreview(item,state.versionNo(),input.amount(),item.quantityAmount().subtract(input.amount()));
    }
    @Transactional
    public UUID create(CookingForm form) {
        if(form==null || form.requestId()==null) throw invalid("입력 화면에서 다시 시작해줘.");
        String payload=payload(form);
        // A successful request stays replayable after later stock changes or deletion.
        var existing=cooking.find(form.requestId());
        if(existing!=null) return replay(existing,payload);
        String name=validateForm(form);
        if(cooking.claim(form,payload,name)==0) return replay(cooking.find(form.requestId()),payload);
        // Match the established master-before-item lock order, including a shared result group.
        var links=new TreeMap<Long,Long>();
        for(var input:form.selected()) {
            Long masterId=masters.masterIdForItem(input.foodId());
            if(masterId==null) throw new FoodNotFoundException(input.foodId());
            links.put(input.foodId(),masterId);
        }
        var masterIds=new TreeSet<>(links.values());
        if(form.masterId()!=null) masterIds.add(form.masterId());
        for(long masterId:masterIds) if(masters.lock(masterId)==null) throw invalid("음식이 바뀌었어. 다시 확인해줘.");
        for(var link:links.entrySet()) {
            if(!link.getValue().equals(masters.masterIdForItem(link.getKey()))) throw invalid("재료가 다른 음식으로 이동했어. 다시 확인해줘.");
            if(inventory.findByIdForUpdate(link.getKey())==null) throw new FoodNotFoundException(link.getKey());
        }
        var checked=preview(form);
        var categoryBefore=form.masterId()==null?null:masters.find(form.masterId());
        // Create first, so the result group's expected version is checked before ingredient touches.
        long result=foods.createCookingOutput(form.foodForm(),form.masterId(),form.masterVersion(),form.categoryMajorCode(),form.categoryMinorCode());
        cooking.setCookedAt(result,form.cookedAt());
        for(var row:checked.ingredients()) {
            var before=quantities.state(row.item().foodId());
            changeStock(row.item(),before,row.remaining());
            var after=quantities.state(row.item().foodId());
            long history=quantities.linkedHistory(row.item().foodId(),"COOK_OUT",form.requestId(),row.used(),row.item().quantityAmount(),row.usedText(),
                "요리: "+name+"\n사용량: "+row.usedText()+"\n수량: "+row.item().displayQuantity()+" → "+row.remainingText(),before,after,null,form.requestId());
            cooking.ingredient(form.requestId(),row.item().foodId(),row.item().foodName(),row.item().quantityUnit(),row.used(),row.item().quantityAmount(),row.remaining(),history);
        }
        String changes="수량: "+checked.quantity()+"\n사용 재료: "+checked.ingredients().stream()
            .map(r->r.item().foodName()+" "+r.usedText()).collect(Collectors.joining(", "))+"\n조리일: "+form.cookedAt();
        long resultHistory=cooking.resultHistory(form.requestId(),result,changes,categoryBefore,
            masters.find(masters.masterIdForItem(result)));
        if(cooking.complete(form.requestId(),result,resultHistory)!=1) throw new IllegalStateException("요리 기록 저장 실패");
        if(form.consumedAmount()!=null && form.consumedAmount().signum()>0)
            consumption.apply(result,FoodQuantityService.Action.CONSUME,quantities.state(result).versionNo(),null,UUID.randomUUID(),form.consumedAmount());
        return form.requestId();
    }
    private UUID replay(CookingDao.Record record,String payload) {
        if(record==null || record.resultFoodId()==null || !record.requestPayload().equals(payload)) throw invalid("이미 보낸 요청과 내용이 달라. 입력 화면에서 다시 시작해줘.");
        return record.cookingId();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detail detail(UUID id) { return new Detail(require(id),cooking.ingredients(id)); }
    private CookingDao.Record require(UUID id) {
        var record=cooking.find(id);
        if(record==null || record.resultFoodId()==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"요리 기록을 찾을 수 없어.");
        return record;
    }
    public record Correction(UUID requestId,UUID cookingId,long foodId,long cookingVersion,long itemVersion,
            BigDecimal amount,BigDecimal confirmedRemaining) {}
    public record CorrectionContext(CookingDao.Record record,FoodItem item,long itemVersion,boolean output,
            BigDecimal effectiveAmount,String unit,boolean needsRemaining) {
        public String effective() { return CookingDao.format(effectiveAmount,unit); }
        public String amountLabel() { return output?"요리 수량":"사용량"; }
    }
    public record CorrectionPreview(Correction command,CorrectionContext context,BigDecimal remaining) {
        public String amount() { return CookingDao.format(command.amount(),context.unit()); }
        public String remainingText() { return CookingDao.format(remaining,context.unit()); }
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CorrectionContext correctionContext(UUID id,long foodId) {
        var record=require(id);
        boolean output=Objects.equals(record.resultFoodId(),foodId);
        var ingredient=output?null:cooking.ingredients(id).stream().filter(i->i.foodId()==foodId).findFirst()
            .orElseThrow(()->new FoodNotFoundException(foodId));
        var item=inventory.findById(foodId);
        if(item==null) throw new FoodNotFoundException(foodId);
        String unit=output?record.quantityUnit():ingredient.quantityUnit();
        if(item.quantityAmount()==null || !Objects.equals(unit,item.quantityUnit()))
            throw invalid("이후 단위가 바뀌어 요리 기록에서 정정할 수 없어. 항목의 현재 수량과 단위를 먼저 확인해줘.");
        long baseline=output?record.resultHistoryId():ingredient.historyId();
        return new CorrectionContext(record,item,quantities.state(foodId).versionNo(),output,
            output?record.quantityAmount():ingredient.usedAmount(),unit,cooking.ambiguousChanges(foodId,baseline,unit)>0);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CorrectionPreview correctionPreview(Correction command) {
        if(command.requestId()==null) throw invalid("정정 화면을 다시 열어줘.");
        var context=correctionContext(command.cookingId(),command.foodId());
        if(command.cookingVersion()!=context.record().versionNo() || command.itemVersion()!=context.itemVersion())
            throw invalid("요리 기록이나 현재 재고가 바뀌었어. 정정 화면을 다시 열어줘.");
        amount(command.amount(),!context.output());
        BigDecimal remaining=command.confirmedRemaining();
        if(remaining==null) {
            if(context.needsRemaining()) throw invalid("이후 수량을 수정한 이력이 있어. 확인한 현재 잔량을 입력해줘.");
            BigDecimal delta=command.amount().subtract(context.effectiveAmount());
            remaining=context.item().quantityAmount().add(context.output()?delta:delta.negate());
        }
        amount(remaining,true);
        if(command.amount().compareTo(context.effectiveAmount())==0 && remaining.compareTo(context.item().quantityAmount())==0)
            throw invalid("변경된 수량이 없어.");
        return new CorrectionPreview(command,context,remaining);
    }
    @Transactional
    public void correct(Correction command) {
        require(command.cookingId());
        String payload=payload(command);
        if(cooking.claimCorrection(command.requestId(),command.cookingId(),payload)==0) {
            var receipt=cooking.correctionReceipt(command.requestId());
            if(receipt==null || !receipt.requestPayload().equals(payload) || receipt.historyId()==null) throw invalid("이미 보낸 정정 요청과 내용이 달라.");
            return;
        }
        cooking.lock(command.cookingId());
        Long masterId=masters.masterIdForItem(command.foodId());
        if(masterId==null) throw new FoodNotFoundException(command.foodId());
        masters.lock(masterId);
        if(!masterId.equals(masters.masterIdForItem(command.foodId()))) throw invalid("음식이 이동되었어. 정정 화면을 다시 열어줘.");
        inventory.findByIdForUpdate(command.foodId());
        var preview=correctionPreview(command);
        var context=preview.context();
        var before=quantities.state(command.foodId());
        if(preview.remaining().compareTo(context.item().quantityAmount())!=0) changeStock(context.item(),before,preview.remaining());
        var after=quantities.state(command.foodId());
        String changes="요리: "+context.record().foodName()+"\n"+context.amountLabel()+": "+context.effective()+" → "+preview.amount()
            +"\n수량: "+context.item().displayQuantity()+" → "+preview.remainingText();
        long history=quantities.linkedHistory(command.foodId(),"UPDATE",command.requestId(),null,context.item().quantityAmount(),
            preview.remainingText(),changes,before,after,null,command.cookingId());
        if(context.output()) cooking.correctResult(command.cookingId(),command.amount(),history);
        else { cooking.correctIngredient(command.cookingId(),command.foodId(),command.amount(),history);cooking.touch(command.cookingId()); }
        cooking.completeCorrection(command.requestId(),history);
    }
    private void changeStock(FoodItem item,FoodQuantityDao.State before,BigDecimal amount) {
        if(quantities.quantity(item.foodId(),before.versionNo(),amount,CookingDao.format(amount,item.quantityUnit()),amount.signum()==0?"DEPLETED":"ACTIVE")!=1)
            throw invalid("현재 재고가 바뀌었어. 다시 확인해줘.");
        masters.touch(masters.masterIdForItem(item.foodId()));
    }
    private static void amount(BigDecimal amount,boolean zeroAllowed) {
        if(amount==null || amount.signum()<0 || (!zeroAllowed && amount.signum()==0)
                || amount.stripTrailingZeros().scale()>2 || amount.compareTo(new BigDecimal("999999999.99"))>0)
            throw invalid(zeroAllowed?"수량은 0 이상, 소수 둘째 자리까지 입력해줘.":"수량은 0보다 크게, 소수 둘째 자리까지 입력해줘.");
    }
    private String payload(Object value) {
        try { return json.writeValueAsString(value); }
        catch(JsonProcessingException e) { throw new IllegalStateException("입력 내용을 저장하지 못했어.",e); }
    }
    private static InvalidFoodException invalid(String message) { return new InvalidFoodException(Map.of("",message)); }
}
