package com.euiseon.friger.inventory.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import com.euiseon.friger.common.type.*;
import com.euiseon.friger.history.dao.HistoryDao;
import com.euiseon.friger.history.entity.FoodHistory;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.entity.FoodItem;
import com.euiseon.friger.inventory.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Splits deduct the source item and create one sibling; never a consumption or new stock. */
@Service
public class FoodSplitService {
    public record Command(BigDecimal amount, StorageType storage, FreezeType freezeType,
                          LocalDate frozenAt, boolean freezeToday) {}
    private final InventoryDao inventory;
    private final FoodMasterDao masters;
    private final FoodQuantityDao quantities;
    private final FoodSplitDao splits;
    private final HistoryDao history;
    private final Clock clock;
    public FoodSplitService(InventoryDao inventory, FoodMasterDao masters, FoodQuantityDao quantities,
                            FoodSplitDao splits, HistoryDao history, Clock clock) {
        this.inventory=inventory; this.masters=masters; this.quantities=quantities;
        this.splits=splits; this.history=history; this.clock=clock;
    }
    @Transactional
    public long split(long id, Command c, long version, UUID token) {
        if(token==null || c.amount()==null || c.storage()==null) throw invalid("분리 내용을 다시 확인해줘.");
        var amount=c.amount();
        if(amount.signum()<=0 || amount.stripTrailingZeros().scale()>2 || amount.compareTo(new BigDecimal("999999999.99"))>0)
            throw invalid("수량은 0보다 크게, 소수점 둘째 자리까지 입력해줘.");
        LocalDate today=LocalDate.now(clock);
        FreezeType freeze=FreezeType.NONE;
        LocalDate frozenAt=null;
        if(c.storage()==StorageType.FREEZER) {
            freeze=c.freezeType()==null || c.freezeType()==FreezeType.NONE ? FreezeType.HOME_FROZEN : c.freezeType();
            frozenAt=c.freezeToday() ? today : c.frozenAt();
            if(!c.freezeToday() && frozenAt!=null && frozenAt.isAfter(today)) throw invalid("미래 날짜는 입력할 수 없어.");
        }
        String payload=id+":"+amount.stripTrailingZeros().toPlainString()+":"+c.storage()+":"+freeze+":"+frozenAt+":"+version;
        // Check ownership before claiming a receipt (including foreign IDs with a known token).
        if(inventory.findById(id)==null) throw new FoodNotFoundException(id);
        if(splits.claim(token,payload,id)==0) {
            var receipt=splits.receipt(token);
            if(receipt==null || !receipt.requestPayload().equals(payload) || receipt.childId()==null)
                throw invalid("이미 보낸 요청이야. 분리 내용을 다시 확인해줘.");
            return receipt.childId();
        }
        Long masterId=masters.masterIdForItem(id);
        if(masterId==null) throw new FoodNotFoundException(id);
        if(masters.lock(masterId)==null || !masterId.equals(masters.masterIdForItem(id)))
            throw invalid("음식이 병합되었어. 상세를 다시 확인해줘.");
        var item=inventory.findByIdForUpdate(id);
        var before=quantities.state(id);
        if(before.versionNo()!=version) throw invalid("음식 정보가 바뀌었어. 상세에서 최신 내용을 다시 확인해줘.");
        if(item.status()!=FoodStatus.ACTIVE) throw invalid("보관 중인 구매 항목만 나눌 수 있어.");
        if(item.quantityAmount()==null || item.quantityUnit()==null)
            throw invalid("먼저 수정 화면에서 현재 수량과 단위를 확인해 입력해줘.");
        if(amount.compareTo(item.quantityAmount())>=0)
            throw invalid("분리량은 현재 수량보다 적어야 해. 전량을 바꾸려면 수정이나 병합을 사용해줘.");
        // Same forced rule as registration: delivery leftovers in the freezer are home-frozen.
        if(c.storage()==StorageType.FREEZER && item.sourceType()==FoodSourceType.DELIVERY_LEFTOVER) freeze=FreezeType.HOME_FROZEN;
        var remaining=item.quantityAmount().subtract(amount);
        String text=remaining.stripTrailingZeros().toPlainString()+item.quantityUnit();
        if(quantities.quantity(id,version,remaining,text,"ACTIVE")!=1) throw invalid("음식 정보가 바뀌었어. 다시 확인해줘.");
        masters.touch(masterId);
        var after=quantities.state(id);
        String amountText=amount.stripTrailingZeros().toPlainString()+item.quantityUnit();
        // The history tab shows the split as this one row; the child's SPLIT_IN stays off that list.
        quantities.history(id,"SPLIT_OUT",token,amount,item.quantityAmount(),amountText,
            "수량: "+item.displayQuantity()+" → "+text+"\n새 항목: +"+amountText,before,after,null);
        OffsetDateTime now=OffsetDateTime.now(clock);
        var child=new FoodItem(null,item.foodName(),c.storage(),item.category(),amountText,
            item.expiredAt(),item.purchasedAt(),item.openedAt(),frozenAt,item.sourceType(),freeze,
            FoodStatus.ACTIVE,item.memo(),now,now,item.capacityText(),item.sourceMemo(),item.sellByAt(),
            amount,item.quantityUnit());
        long childId=inventory.insertForMaster(child,masterId);
        if(history.insert(new FoodHistory(null,childId,FoodActionType.SPLIT_IN,null,c.storage(),amountText,now,null))!=1)
            throw new IllegalStateException("분리 이력을 저장하지 못했습니다.");
        if(splits.complete(token,childId)!=1) throw new IllegalStateException("분리 결과를 저장하지 못했습니다.");
        return childId;
    }
    private static InvalidFoodException invalid(String message) {return new InvalidFoodException(Map.of("",message));}
}
