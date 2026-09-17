package com.euiseon.friger.inventory.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.entity.*;
import com.euiseon.friger.inventory.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Merges selected purchase items into another food. A whole selection takes the
 * ended records along and retires the source food; a partial selection leaves
 * unselected items and ended records behind. Every item gets its own receipt so
 * history always shows one MOVE entry per item.
 */
@Service
public class ItemMoveService {
    public enum Mode { EXISTING, NEW }
    public record Command(Mode mode, Long targetId, String newName, String newCategory,
                          long sourceId, long sourceVersion, Long targetVersion,
                          List<Long> itemIds, boolean whole) {}
    public record Selection(FoodMaster source, List<FoodItem> items, boolean whole, int endedCount, int activeCount) {}
    public record Preview(FoodMaster source, List<FoodItem> items, boolean whole, int endedCount, int remainingCount,
                          String targetName, String targetCategory, int historyCount, Command command, UUID requestId) {}
    public record MoveResult(long targetId, boolean sourceRemoved) {}
    private final FoodMasterDao masters;
    private final InventoryDao inventory;
    private final ItemMoveDao moves;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public ItemMoveService(FoodMasterDao masters, InventoryDao inventory, ItemMoveDao moves,
                           com.fasterxml.jackson.databind.ObjectMapper json) {
        this.masters=masters; this.inventory=inventory; this.moves=moves; this.json=json;
    }
    private static InvalidFoodException invalid(String message) {
        return new InvalidFoodException(Map.of("",message));
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Selection selection(long masterId, List<Long> itemIds) {
        var source=masters.find(masterId);
        if(source==null) throw new FoodNotFoundException(masterId);
        if(itemIds==null || itemIds.isEmpty()) throw invalid("병합할 구매 항목을 먼저 선택해줘.");
        var byId=new LinkedHashMap<Long,FoodItem>();
        for(var item:inventory.findByMaster(masterId)) byId.put(item.foodId(),item);
        var items=new ArrayList<FoodItem>();
        for(long id:itemIds.stream().distinct().toList()) {
            var item=byId.get(id);
            if(item==null) throw invalid("선택한 구매 항목이 변경되었어. 목록에서 다시 확인해줘.");
            items.add(item);
        }
        long activeTotal=byId.values().stream().filter(i->i.status()==FoodStatus.ACTIVE).count();
        boolean whole=activeTotal>0 && items.size()==activeTotal
                && items.stream().allMatch(i->i.status()==FoodStatus.ACTIVE);
        int endedCount=(int)byId.values().stream().filter(i->i.status()==FoodStatus.DEPLETED).count();
        return new Selection(source,List.copyOf(items),whole,endedCount,(int)activeTotal);
    }
    private Command normalize(Command c) {
        if(c==null || c.mode()==null) throw invalid("병합 방법을 선택해줘.");
        if(c.itemIds()==null || c.itemIds().isEmpty()) throw invalid("병합할 구매 항목을 먼저 선택해줘.");
        var ids=c.itemIds().stream().distinct().sorted().toList();
        if(c.mode()==Mode.EXISTING) {
            if(c.targetId()==null || c.targetId()==c.sourceId()) throw invalid("다른 음식을 선택해줘.");
            return new Command(c.mode(),c.targetId(),null,null,c.sourceId(),c.sourceVersion(),c.targetVersion(),ids,c.whole());
        }
        String name=c.newName()==null?"":c.newName().strip();
        String category=c.newCategory()==null || c.newCategory().isBlank()?null:c.newCategory().strip();
        if(name.isEmpty() || name.length()>100) throw invalid("음식명은 1~100자로 입력해줘.");
        if(category!=null && category.length()>50) throw invalid("분류는 50자 이내로 입력해줘.");
        return new Command(c.mode(),null,name,category,c.sourceId(),c.sourceVersion(),null,ids,c.whole());
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Preview preview(long masterId, List<Long> itemIds, Mode mode, Long targetId, String name, String category) {
        var selection=selection(masterId,itemIds);
        var source=selection.source();
        var selectedIds=selection.items().stream().map(FoodItem::foodId).toList();
        // The preview only informs; a still-empty new name is validated at submit time.
        var command=mode==Mode.NEW && (name==null || name.isBlank())
                ? new Command(mode,null,null,category,source.masterId(),source.versionNo(),null,
                        selectedIds.stream().sorted().toList(),selection.whole())
                : normalize(new Command(mode,targetId,name,category,source.masterId(),source.versionNo(),null,
                        selectedIds,selection.whole()));
        FoodMaster target=mode==Mode.EXISTING?masters.find(targetId):null;
        if(mode==Mode.EXISTING && target==null) throw invalid("대상 음식이 변경되었어. 다시 선택해줘.");
        if(target!=null) command=new Command(mode,targetId,null,null,source.masterId(),source.versionNo(),
                target.versionNo(),command.itemIds(),command.whole());
        int historyCount=selection.whole() ? masters.countHistory(source.masterId())
                : selection.items().stream().mapToInt(item->moves.historyCount(item.foodId())).sum();
        int remaining=masters.countItems(source.masterId())-selection.items().size()
                -(selection.whole()?selection.endedCount():0);
        return new Preview(source,selection.items(),selection.whole(),selection.endedCount(),remaining,
                target==null?command.newName():target.foodName(),
                target==null?command.newCategory():target.category(),
                historyCount,command,UUID.randomUUID());
    }
    @Transactional
    public MoveResult move(Command raw, UUID token) {
        var c=normalize(raw);
        if(token==null) throw invalid("병합 내용을 다시 확인해줘.");
        String fingerprint;
        try {fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                json.writeValueAsString(c).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error) {throw new IllegalStateException(error);}
        var done=completed(token,fingerprint);
        if(done!=null) return done;
        // Same ascending master lock order as registration and edits.
        FoodMaster source, target=null;
        if(c.mode()==Mode.EXISTING) {
            var first=masters.lock(Math.min(c.sourceId(),c.targetId()));
            var second=masters.lock(Math.max(c.sourceId(),c.targetId()));
            source=c.sourceId()<c.targetId()?first:second;
            target=c.sourceId()<c.targetId()?second:first;
        } else source=masters.lock(c.sourceId());
        done=completed(token,fingerprint);
        if(done!=null) return done;
        if(source==null || source.versionNo()!=c.sourceVersion()
                || (c.mode()==Mode.EXISTING && (target==null || c.targetVersion()==null || target.versionNo()!=c.targetVersion())))
            throw invalid("확인 후 음식 정보가 바뀌었어. 최신 병합 내용을 다시 확인해줘.");
        var moving=c.itemIds();
        if(c.whole()) {
            // Confirmed whole move: ended records follow and the food retires afterwards.
            var all=inventory.findByMaster(c.sourceId());
            var activeIds=all.stream().filter(item->item.status()==FoodStatus.ACTIVE)
                    .map(FoodItem::foodId).sorted().toList();
            if(!activeIds.equals(c.itemIds())) throw invalid("확인 후 음식 정보가 바뀌었어. 최신 병합 내용을 다시 확인해줘.");
            moving=all.stream().map(FoodItem::foodId).sorted().toList();
        }
        for(long itemId:moving)
            if(!Long.valueOf(c.sourceId()).equals(masters.masterIdForItem(itemId)))
                throw invalid("선택한 구매 항목이 변경되었어. 목록에서 다시 확인해줘.");
        if(target==null) {
            long newId=moves.createMaster(c.newName(),c.newCategory(),
                    inventory.findById(c.itemIds().getFirst()).quantityUnit());
            target=masters.find(newId);
        }
        for(long itemId:moving)
            if(moves.transfer(itemId,source.masterId(),target.masterId())!=1)
                throw invalid("구매 항목이 변경되었어. 다시 확인해줘.");
        masters.touch(target.masterId());
        boolean removed=masters.countItems(source.masterId())==0;
        if(removed) masters.delete(source.masterId()); else masters.touch(source.masterId());
        try {
            for(long itemId:moving)
                moves.receipt(token,fingerprint,itemId,source.masterId(),target.masterId(),
                        source.foodName(),target.foodName(),target.category(),removed);
        } catch(org.springframework.dao.DuplicateKeyException error) {
            throw invalid("이미 보낸 요청이야. 병합 내용을 다시 확인해줘.");
        }
        return new MoveResult(target.masterId(),removed);
    }
    private MoveResult completed(UUID token,String fingerprint) {
        var receipt=moves.completed(token);
        if(receipt==null) return null;
        if(!receipt.fingerprint().equals(fingerprint)) throw invalid("이미 보낸 요청이야. 병합 내용을 다시 확인해줘.");
        return new MoveResult(receipt.targetId(),receipt.sourceRemoved());
    }
}
