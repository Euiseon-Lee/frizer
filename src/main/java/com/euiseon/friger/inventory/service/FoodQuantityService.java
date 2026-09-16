package com.euiseon.friger.inventory.service;

import java.math.BigDecimal;
import java.util.*;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.entity.FoodItem;
import com.euiseon.friger.inventory.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FoodQuantityService {
    public enum Action { CONSUME, DISCARD, CANCEL }
    public record Preview(FoodItem item, long version, FoodQuantityDao.Event event, boolean cancellable) {}
    private final InventoryDao inventory;
    private final FoodMasterDao masters;
    private final FoodQuantityDao quantities;
    public FoodQuantityService(InventoryDao inventory,FoodMasterDao masters,FoodQuantityDao quantities) {
        this.inventory=inventory;this.masters=masters;this.quantities=quantities;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview preview(long id) {
        var item=inventory.findById(id);
        if(item==null) throw new FoodNotFoundException(id);
        var state=quantities.state(id);
        var event=quantities.latest(id);
        return new Preview(item,state.versionNo(),event,canCancel(item,state,event));
    }
    @Transactional(readOnly=true)
    public List<QuantityChange> history(long id) {
        return quantities.quantityChanges(id);
    }
    @Transactional(readOnly=true)
    public String registrationQuantity(long id) {
        return quantities.registrationQuantity(id);
    }
    @Transactional(readOnly=true)
    public Map<Long, FoodQuantityDao.EndedSummary> endedSummaries(long masterId) {
        return quantities.endedSummaries(masterId).stream().collect(java.util.stream.Collectors.toMap(
                FoodQuantityDao.EndedSummary::foodId, summary -> summary));
    }
    private boolean canCancel(FoodItem item,FoodQuantityDao.State state,FoodQuantityDao.Event event) {
        return item.quantityAmount()!=null && event!=null && !event.reversed()
            && state.stockRevision()==event.afterStockRevision()
            && Objects.equals(item.quantityUnit(),event.quantityUnit());
    }
    @Transactional
    public long apply(long id,Action action,long version,Long historyId,UUID token) {
        return apply(id,action,version,historyId,token,null);
    }
    @Transactional
    public long apply(long id,Action action,long version,Long historyId,UUID token,BigDecimal selectedQuantity) {
        if(action==null || token==null) throw invalid("처리 내용을 다시 확인해줘.");
        if(selectedQuantity!=null && (action==Action.CANCEL || selectedQuantity.signum()<=0
                || selectedQuantity.stripTrailingZeros().scale()>2 || selectedQuantity.compareTo(new BigDecimal("999999999.99"))>0))
            throw invalid("수량은 0보다 크게, 소수점 둘째 자리까지 입력해줘.");
        String payload=id+":"+action+":"+version+":"+historyId
            +(selectedQuantity==null ? "" : ":"+selectedQuantity.stripTrailingZeros().toPlainString());
        if(quantities.claim(token,payload,id)==0) {
            var receipt=quantities.receipt(token);
            if(receipt==null || !receipt.requestPayload().equals(payload) || receipt.historyId()==null)
                throw invalid("이미 보낸 요청이야. 처리 내용을 다시 확인해줘.");
            return receipt.historyId();
        }
        Long masterId=masters.masterIdForItem(id);
        if(masterId==null) throw new FoodNotFoundException(id);
        if(masters.lock(masterId)==null || !masterId.equals(masters.masterIdForItem(id)))
            throw invalid("음식이 이동되었어. 상세를 다시 확인해줘.");
        var item=inventory.findByIdForUpdate(id);
        var before=quantities.state(id);
        if(before.versionNo()!=version) throw invalid("음식 정보가 바뀌었어. 상세에서 최신 내용을 다시 확인해줘.");
        BigDecimal processed;
        BigDecimal remaining;
        if(action==Action.CANCEL) {
            var event=quantities.latest(id);
            if(!canCancel(item,before,event) || !Objects.equals(historyId,event.historyId()))
                throw invalid("이후 수량이나 보관 정보가 변경되었거나 이미 취소된 처리야. 취소할 수 없어.");
            processed=event.processedQuantityAmount();remaining=item.quantityAmount().add(processed);
        } else {
            if(historyId!=null || item.status()!=FoodStatus.ACTIVE) throw invalid("보관 중인 구매 항목만 처리할 수 있어.");
            if(item.quantityAmount()==null || item.quantityUnit()==null)
                throw invalid("먼저 수정 화면에서 현재 수량과 단위를 확인해 입력해줘.");
            processed=selectedQuantity==null ? item.quantityAmount() : selectedQuantity;
            if(processed.compareTo(item.quantityAmount())>0) throw invalid("기준 수량보다 많이 처리할 수 없어.");
            remaining=item.quantityAmount().subtract(processed);
        }
        String text=remaining.stripTrailingZeros().toPlainString()+item.quantityUnit();
        if(quantities.quantity(id,version,remaining,text,remaining.signum()>0?"ACTIVE":"DEPLETED")!=1)
            throw invalid("음식 정보가 바뀌었어. 다시 확인해줘.");
        masters.touch(masterId);
        var after=quantities.state(id);
        String display=processed.stripTrailingZeros().toPlainString()+item.quantityUnit();
        long event=quantities.history(id,action.name(),token,processed,item.quantityAmount(),display,
            "수량: "+item.displayQuantity()+" → "+text,before,after,action==Action.CANCEL?historyId:null);
        if(quantities.complete(token,event)!=1) throw new IllegalStateException("처리 결과를 저장하지 못했어.");
        return event;
    }
    private static InvalidFoodException invalid(String message) {return new InvalidFoodException(Map.of("",message));}
}
