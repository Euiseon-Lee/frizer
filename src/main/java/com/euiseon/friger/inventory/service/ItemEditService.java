package com.euiseon.friger.inventory.service;

import java.time.OffsetDateTime;
import java.util.*;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One transaction for editing a purchase, optionally splitting and regrouping it. */
@Service
public class ItemEditService {
    private final InventoryService inventory;
    private final FoodMasterDao masters;
    private final FoodQuantityDao quantities;
    private final FoodSplitService splits;
    private final ItemMoveService moves;
    public ItemEditService(InventoryService inventory, FoodMasterDao masters, FoodQuantityDao quantities,
                           FoodSplitService splits, ItemMoveService moves) {
        this.inventory=inventory; this.masters=masters; this.quantities=quantities; this.splits=splits; this.moves=moves;
    }
    public List<FoodMasterDao.RegistrationChoice> choices(long id) {
        var source=masters.masterIdForItem(id);
        return masters.registrationChoices().stream().filter(c->!Long.valueOf(c.masterId()).equals(source)).toList();
    }
    @Transactional
    public long save(long id, FoodCreateForm form, OffsetDateTime expected, String mode,
                     boolean merge, Long targetId, Long targetVersion, String major, String minor) {
        if (!"all".equals(mode) && !"partial".equals(mode)) throw invalid("수정 방식을 다시 선택해줘.");
        boolean partial="partial".equals(mode);
        Long sourceId=masters.masterIdForItem(id);
        if(sourceId==null) throw new FoodNotFoundException(id);
        if(merge && (targetId==null || targetId.equals(sourceId))) throw invalid("합칠 음식을 선택해줘.");
        // Keep the same lock ordering as list merges, before splitting or editing anything.
        if(merge) { masters.lock(Math.min(sourceId,targetId)); masters.lock(Math.max(sourceId,targetId)); }
        else masters.lock(sourceId);
        if(!sourceId.equals(masters.masterIdForItem(id))) throw invalid("음식이 변경되었어. 수정 화면을 다시 열어줘.");
        var before=inventory.findById(id);
        if(expected==null || !before.updatedAt().isEqual(expected)) throw invalid("다른 화면에서 음식 정보가 바뀌었어. 상세를 다시 열어 최신 내용을 확인해줘.");
        if(merge) {
            var target=masters.find(targetId);
            if(target==null || targetVersion==null || target.versionNo()!=targetVersion) throw invalid("합칠 음식이 변경되었어. 다시 선택해줘.");
        }
        // Identity changes must never leak from a moved portion into its remaining siblings.
        if(partial || merge) form=form.withIdentity(before.foodName(),before.category());
        if(partial) {
            if(before.quantityAmount()==null || form.quantityAmount()==null || form.quantityAmount().signum()<=0
                    || form.quantityAmount().compareTo(before.quantityAmount())>=0)
                throw invalid("0보다 크고 현재 수량보다 적은 수량을 선택해줘.");
            if(!Objects.equals(form.quantityUnit(),before.quantityUnit())) throw invalid("일부 수정에서는 단위를 바꿀 수 없어.");
            if(!merge && !inventory.itemDetailsChanged(before,form)) throw invalid("변경한 내용이 없어.");
        }
        long edited=id;
        if(partial) {
            // First copy unchanged state; the ordinary edit then records the actual metadata changes.
            edited=splits.split(id,new FoodSplitService.Command(form.quantityAmount(),before.storageType(),
                    before.freezeType(),before.frozenAt(),false),quantities.state(id).versionNo(),UUID.randomUUID());
        }
        var current=inventory.findById(edited);
        if(partial || merge) inventory.update(edited,form,current.updatedAt());
        else inventory.updateCategorized(edited,form,expected,major,minor);
        if(merge) {
            moves.move(new ItemMoveService.Command(ItemMoveService.Mode.EXISTING,targetId,null,null,
                    sourceId,masters.find(sourceId).versionNo(),targetVersion,List.of(edited),false),UUID.randomUUID());
        }
        return edited;
    }
    private static InvalidFoodException invalid(String message) { return new InvalidFoodException(Map.of("",message)); }
}
