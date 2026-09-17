package com.euiseon.friger.inventory.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.entity.*;
import com.euiseon.friger.inventory.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Physically removes mistaken registrations. Each selected item and its whole
 * history vanish in one transaction; the delete receipt stays behind without a
 * FK so the same request can never delete or recreate anything twice. Consumed
 * or discarded stock keeps its records — this tool is only for entries that
 * should never have existed, and the group is removed only when the user asked.
 */
@Service
public class FoodDeleteService {
    public record Command(long sourceId, long sourceVersion, List<Long> itemIds,
                          List<Long> itemVersions, boolean deleteMaster) {}
    public record Selection(FoodMaster source, List<FoodItem> items, Map<Long,Long> versions,
                            boolean whole, int historyCount) {}
    public record DeleteResult(int itemCount, int historyCount, boolean masterRemoved) {}
    private final FoodMasterDao masters;
    private final InventoryDao inventory;
    private final FoodDeleteDao deletes;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public FoodDeleteService(FoodMasterDao masters, InventoryDao inventory, FoodDeleteDao deletes,
                             com.fasterxml.jackson.databind.ObjectMapper json) {
        this.masters=masters; this.inventory=inventory; this.deletes=deletes; this.json=json;
    }
    private static InvalidFoodException invalid(String message) {
        return new InvalidFoodException(Map.of("",message));
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Selection selection(long masterId, List<Long> itemIds) {
        var source=masters.find(masterId);
        if(source==null) throw new FoodNotFoundException(masterId);
        if(itemIds==null || itemIds.isEmpty()) throw invalid("삭제할 구매 항목을 먼저 선택해줘.");
        var byId=new LinkedHashMap<Long,FoodItem>();
        for(var item:inventory.findByMaster(masterId)) byId.put(item.foodId(),item);
        var items=new ArrayList<FoodItem>();
        for(long id:itemIds.stream().distinct().toList()) {
            var item=byId.get(id);
            if(item==null) throw invalid("선택한 구매 항목이 변경되었어. 목록에서 다시 확인해줘.");
            items.add(item);
        }
        var versions=new LinkedHashMap<Long,Long>();
        for(var target:deletes.targets(masterId)) versions.put(target.foodId(),target.versionNo());
        boolean whole=items.size()==byId.size();
        int historyCount=items.stream().mapToInt(item->deletes.historyCount(item.foodId())).sum();
        return new Selection(source,List.copyOf(items),versions,whole,historyCount);
    }
    private Command normalize(Command c) {
        if(c==null || c.itemIds()==null || c.itemIds().isEmpty()) throw invalid("삭제할 구매 항목을 먼저 선택해줘.");
        if(c.itemVersions()==null || c.itemVersions().size()!=c.itemIds().size())
            throw invalid("삭제 내용을 다시 확인해줘.");
        var pairs=new TreeMap<Long,Long>();
        for(int i=0;i<c.itemIds().size();i++)
            if(pairs.put(c.itemIds().get(i),c.itemVersions().get(i))!=null)
                throw invalid("삭제 내용을 다시 확인해줘.");
        return new Command(c.sourceId(),c.sourceVersion(),
                List.copyOf(pairs.keySet()),List.copyOf(pairs.values()),c.deleteMaster());
    }
    @Transactional
    public DeleteResult delete(Command raw, UUID token) {
        var c=normalize(raw);
        if(token==null) throw invalid("삭제 내용을 다시 확인해줘.");
        String fingerprint;
        try {fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                json.writeValueAsString(c).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error) {throw new IllegalStateException(error);}
        var done=completed(token,fingerprint);
        if(done!=null) return done;
        // Same ascending master-then-item lock order as moves and edits.
        var source=masters.lock(c.sourceId());
        done=completed(token,fingerprint);
        if(done!=null) return done;
        if(source==null || source.versionNo()!=c.sourceVersion())
            throw invalid("확인 후 음식 정보가 바뀌었어. 최신 삭제 내용을 다시 확인해줘.");
        for(int i=0;i<c.itemIds().size();i++) {
            var target=deletes.lockTarget(c.itemIds().get(i),c.sourceId());
            if(target==null || target.versionNo()!=c.itemVersions().get(i))
                throw invalid("확인 후 구매 항목이 바뀌었어. 최신 삭제 내용을 다시 확인해줘.");
        }
        int historyCount=0;
        for(long id:c.itemIds()) {
            historyCount+=deletes.deleteCancelHistories(id);
            historyCount+=deletes.deleteHistories(id);
            if(deletes.deleteItem(id,c.sourceId())!=1)
                throw invalid("확인 후 구매 항목이 바뀌었어. 최신 삭제 내용을 다시 확인해줘.");
        }
        boolean removed=false;
        if(c.deleteMaster()) {
            if(masters.countItems(c.sourceId())>0)
                throw invalid("음식 그룹에 다른 구매 항목이 남아 있어. 최신 삭제 내용을 다시 확인해줘.");
            masters.delete(c.sourceId());
            removed=true;
        } else masters.touch(c.sourceId());
        try {
            deletes.receipt(token,fingerprint,c.sourceId(),source.foodName(),
                    c.itemIds().size(),historyCount,removed);
        } catch(org.springframework.dao.DuplicateKeyException error) {
            throw invalid("이미 보낸 요청이야. 삭제 내용을 다시 확인해줘.");
        }
        return new DeleteResult(c.itemIds().size(),historyCount,removed);
    }
    private DeleteResult completed(UUID token,String fingerprint) {
        var receipt=deletes.completed(token);
        if(receipt==null) return null;
        if(!receipt.fingerprint().equals(fingerprint)) throw invalid("이미 보낸 요청이야. 삭제 내용을 다시 확인해줘.");
        return new DeleteResult(receipt.itemCount(),receipt.historyCount(),receipt.masterRemoved());
    }
}
