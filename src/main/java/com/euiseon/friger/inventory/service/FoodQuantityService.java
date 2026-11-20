package com.euiseon.friger.inventory.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.common.type.OpeningStatus;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.entity.FoodItem;
import com.euiseon.friger.inventory.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FoodQuantityService {
    public enum Action { CONSUME, DISCARD, CANCEL }
    public record Preview(FoodItem item, long version, FoodQuantityDao.Event event, boolean cancellable,
                          boolean needsRemainingOpeningChoice) {}
    private final InventoryDao inventory;
    private final FoodMasterDao masters;
    private final FoodQuantityDao quantities;
    private final FoodSplitService splits;
    private final Clock clock;
    public FoodQuantityService(InventoryDao inventory,FoodMasterDao masters,FoodQuantityDao quantities, FoodSplitService splits, Clock clock) {
        this.inventory=inventory;this.masters=masters;this.quantities=quantities;
        this.clock=clock;
        this.splits=splits;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview preview(long id) {
        var item=inventory.findById(id);
        if(item==null) throw new FoodNotFoundException(id);
        var state=quantities.state(id);
        var event=quantities.latest(id);
        return new Preview(item,state.versionNo(),event,canCancel(item,state,event),needsRemainingOpeningChoice(item));
    }
    @Transactional(readOnly=true)
    public List<QuantityChange> history(long id) {
        return quantities.quantityChanges(id);
    }
    @Transactional(readOnly=true)
    public Map<Long, FoodQuantityDao.EndedSummary> endedSummaries(long masterId) {
        return quantities.endedSummaries(masterId).stream().collect(java.util.stream.Collectors.toMap(
                FoodQuantityDao.EndedSummary::foodId, summary -> summary));
    }
    @Transactional(readOnly=true)
    public Map<Long, String> endedRegistrationQuantities() {
        Map<Long, String> result = new HashMap<>();
        for (var row : quantities.endedRegistrationQuantities())
            if (row.registrationQuantity() != null) result.put(row.foodId(), row.registrationQuantity());
        return result;
    }
    private boolean canCancel(FoodItem item,FoodQuantityDao.State state,FoodQuantityDao.Event event) {
        return item.quantityAmount()!=null && event!=null && !event.reversed()
            && state.stockRevision()==event.afterStockRevision()
            && Objects.equals(item.quantityUnit(),event.quantityUnit()) && separatedStockUnchanged(item,event);
    }
    private boolean separatedStockUnchanged(FoodItem item,FoodQuantityDao.Event event) {
        if(event.separatedFoodId()==null) return true;
        var child=inventory.findById(event.separatedFoodId());
        var state=quantities.state(event.separatedFoodId());
        // A sibling is removed on undo. Even a memo edit must prevent losing it.
        return child!=null && state!=null && state.versionNo()==event.separatedItemVersion()
            && Objects.equals(masters.masterIdForItem(item.foodId()),masters.masterIdForItem(child.foodId()));
    }
    private boolean needsRemainingOpeningChoice(FoodItem item) {
        return item.openingStatus() == OpeningStatus.UNOPENED
                || (item.openingStatus() == OpeningStatus.UNKNOWN && !quantities.hasConsumption(item.foodId()));
    }
    @Transactional
    public long apply(long id,Action action,long version,Long historyId,UUID token,BigDecimal selectedQuantity) {
        return apply(id, action, version, historyId, token, selectedQuantity, null);
    }
    @Transactional
    public long apply(long id,Action action,long version,Long historyId,UUID token,BigDecimal selectedQuantity,
                      OpeningStatus remainingOpening) {
        return apply(id, action, version, historyId, token, selectedQuantity, remainingOpening, null);
    }
    @Transactional
    public long apply(long id,Action action,long version,Long historyId,UUID token,BigDecimal selectedQuantity,
                      OpeningStatus remainingOpening, LocalDate openedAt) {
        return apply(id,action,version,historyId,token,selectedQuantity,remainingOpening,openedAt,null);
    }
    @Transactional
    public long apply(long id,Action action,long version,Long historyId,UUID token,BigDecimal selectedQuantity,
                      OpeningStatus remainingOpening, LocalDate openedAt, BigDecimal openedQuantity) {
        if(action==null || token==null) throw invalid("처리 내용을 다시 확인해줘.");
        if(selectedQuantity!=null && (action==Action.CANCEL || selectedQuantity.signum()<=0
                || selectedQuantity.stripTrailingZeros().scale()>2 || selectedQuantity.compareTo(new BigDecimal("999999999.99"))>0))
            throw invalid("수량은 0보다 크게, 소수점 둘째 자리까지 입력해줘.");
        String payload=id+":"+action+":"+version+":"+historyId
            +(selectedQuantity==null ? "" : ":"+selectedQuantity.stripTrailingZeros().toPlainString())
            +(remainingOpening==null ? "" : ":"+remainingOpening.name())
            +(action==Action.CONSUME && remainingOpening==OpeningStatus.OPENED && openedAt!=null ? ":"+openedAt : "")
            +(openedQuantity==null ? "" : ":opened="+openedQuantity.stripTrailingZeros().toPlainString());
        // Check ownership before claiming a receipt (including foreign IDs with a known token).
        if(inventory.findById(id)==null) throw new FoodNotFoundException(id);
        if(quantities.claim(token,payload,id)==0) {
            var receipt=quantities.receipt(token);
            if(receipt==null || !receipt.requestPayload().equals(payload) || receipt.historyId()==null)
                throw invalid("이미 보낸 요청이야. 처리 내용을 다시 확인해줘.");
            return receipt.historyId();
        }
        Long masterId=masters.masterIdForItem(id);
        if(masterId==null) throw new FoodNotFoundException(id);
        var master=masters.lock(masterId);
        if(master==null || !masterId.equals(masters.masterIdForItem(id)))
            throw invalid("음식이 이동되었어. 상세를 다시 확인해줘.");
        if(action!=Action.CANCEL && master.categoryMajorCode()==null)
            throw new InvalidFoodException(Map.of("categoryMajorCode","먼저 수정 화면에서 음식의 분류를 선택해줘."));
        var item=inventory.findByIdForUpdate(id);
        var before=quantities.state(id);
        if(before.versionNo()!=version) throw invalid("음식 정보가 바뀌었어. 상세에서 최신 내용을 다시 확인해줘.");
        BigDecimal processed;
        BigDecimal remaining;
        BigDecimal separatedAmount=BigDecimal.ZERO;
        Long separatedId=null;
        Long separatedVersion=null;
        OpeningStatus openingChange = null;
        if(action==Action.CANCEL) {
            var event=quantities.latest(id);
            if(!canCancel(item,before,event) || !Objects.equals(historyId,event.historyId()))
                throw invalid("이후 수량이나 보관 정보가 변경되었거나 이미 취소된 처리야. 취소할 수 없어.");
            processed=event.processedQuantityAmount();remaining=event.restoredAmount(item.quantityAmount());
            openingChange = event.openingStatusToRestore();
            separatedId=event.separatedFoodId();
            separatedAmount=event.separatedQuantityAmount();
            separatedVersion=event.separatedItemVersion();
            if(separatedId!=null) {
                // Master first, then source and its newer sibling: the existing lock order.
                inventory.findByIdForUpdate(separatedId);
                if(!separatedStockUnchanged(item,event)) throw invalid("나뉜 재고가 변경되어 취소할 수 없어.");
                if(quantities.deleteSeparatedHistory(separatedId)!=1
                        || quantities.deleteSeparatedItem(separatedId,masterId,separatedVersion)!=1)
                    throw invalid("나뉜 재고가 변경되어 취소할 수 없어.");
            }
        } else {
            if(historyId!=null || item.status()!=FoodStatus.ACTIVE) throw invalid("보관 중인 구매 항목만 처리할 수 있어.");
            if(item.quantityAmount()==null || item.quantityUnit()==null)
                throw invalid("먼저 수정 화면에서 현재 수량과 단위를 확인해 입력해줘.");
            processed=selectedQuantity==null ? item.quantityAmount() : selectedQuantity;
            if(processed.compareTo(item.quantityAmount())>0) throw invalid("기준 수량보다 많이 처리할 수 없어.");
            remaining=item.quantityAmount().subtract(processed);
            if (action == Action.CONSUME && remaining.signum() > 0 && needsRemainingOpeningChoice(item)) {
                if (remainingOpening != OpeningStatus.UNOPENED && remainingOpening != OpeningStatus.OPENED)
                    throw new InvalidFoodException(Map.of("remainingOpening", "남은 음식의 개봉 상태를 골라줘."));
                if (remainingOpening != item.openingStatus()) openingChange = remainingOpening;
                if(remainingOpening==OpeningStatus.OPENED && openedQuantity!=null) {
                    if(openedQuantity.signum()<=0 || openedQuantity.stripTrailingZeros().scale()>2
                            || openedQuantity.compareTo(remaining)>0)
                        throw new InvalidFoodException(Map.of("openedQuantity", "개봉 수량은 0보다 크고 남은 수량 이하로 입력해줘."));
                    separatedAmount=remaining.subtract(openedQuantity);
                    remaining=openedQuantity;
                }
            }
        }
        String text=remaining.stripTrailingZeros().toPlainString()+item.quantityUnit();
        LocalDate confirmedAt = openingChange == OpeningStatus.OPENED ? LocalDate.now(clock) : null;
        LocalDate openingDate = openingChange == OpeningStatus.OPENED ? openedAt : null;
        if (openingDate != null && openingDate.isAfter(confirmedAt))
            throw new InvalidFoodException(Map.of("openedAt", "미래 날짜는 입력할 수 없습니다."));
        if(action==Action.CONSUME && separatedAmount.signum()>0) {
            var now=java.time.OffsetDateTime.now(clock);
            var child=new FoodItem(null,item.foodName(),item.storageType(),item.category(),
                separatedAmount.stripTrailingZeros().toPlainString()+item.quantityUnit(),item.expiredAt(),item.purchasedAt(),null,
                item.frozenAt(),item.sourceType(),item.freezeType(),FoodStatus.ACTIVE,item.memo(),now,now,
                item.capacityText(),item.sourceMemo(),item.sellByAt(),separatedAmount,item.quantityUnit(),
                item.warningPausedUntil(),OpeningStatus.UNOPENED,null);
            separatedId=splits.createSibling(id,masterId,child);
            separatedVersion=quantities.state(separatedId).versionNo();
        }
        if(quantities.updateQuantity(id,version,remaining,text,remaining.signum()>0?"ACTIVE":"DEPLETED",openingChange,openingDate,confirmedAt)!=1)
            throw invalid("음식 정보가 바뀌었어. 다시 확인해줘.");
        masters.touch(masterId);
        var after=quantities.state(id);
        String display=processed.stripTrailingZeros().toPlainString()+item.quantityUnit();
        String changes = "수량: "+item.displayQuantity()+" → "+text;
        if (openingChange != null)
            changes += "\n개봉 상태: " + item.openingStatus().label() + " → " + openingChange.label();
        if(separatedId!=null)
            changes += "\n미개봉 " + separatedAmount.stripTrailingZeros().toPlainString()+item.quantityUnit()
                + (action==Action.CANCEL ? " 합침" : " 분리") + " (#"+separatedId+")";
        long event=quantities.operationHistory(id,action.name(),token,processed,item.quantityAmount(),display,
            changes,before,after,action==Action.CANCEL?historyId:null,null,separatedId,separatedAmount,separatedVersion);
        if(quantities.complete(token,event)!=1) throw new IllegalStateException("처리 결과를 저장하지 못했어.");
        return event;
    }
    private static InvalidFoodException invalid(String message) {return new InvalidFoodException(Map.of("",message));}
}
