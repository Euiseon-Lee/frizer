package com.euiseon.friger.inventory.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.euiseon.friger.common.type.FoodStatus;
import com.euiseon.friger.inventory.dao.*;
import com.euiseon.friger.inventory.entity.*;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ItemMoveService {
    public enum Mode { EXISTING, NEW }
    public record Command(Mode mode, Long targetId, String newName, String newCategory,
                          long sourceId, long sourceVersion, Long targetVersion) {}
    public record Preview(FoodItem item, FoodMaster source, String targetName, String targetCategory,
                          int historyCount, int remainingCount, Command command, UUID requestId) {}
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
    private FoodItem active(long id) {
        var item=inventory.findById(id);
        if(item==null || item.status()!=FoodStatus.ACTIVE) throw invalid("이동할 구매 항목을 다시 확인해줘.");
        return item;
    }
    private Command normalize(Command c) {
        if(c==null || c.mode()==null) throw invalid("이동 방법을 선택해줘.");
        if(c.mode()==Mode.EXISTING) {
            if(c.targetId()==null || c.targetId()==c.sourceId()) throw invalid("다른 음식을 선택해줘.");
            return new Command(c.mode(),c.targetId(),null,null,c.sourceId(),c.sourceVersion(),c.targetVersion());
        }
        String name=c.newName()==null?"":c.newName().strip();
        String category=c.newCategory()==null || c.newCategory().isBlank()?null:c.newCategory().strip();
        if(name.isEmpty() || name.length()>100) throw invalid("음식명은 1~100자로 입력해줘.");
        if(category!=null && category.length()>50) throw invalid("분류는 50자 이내로 입력해줘.");
        return new Command(c.mode(),null,name,category,c.sourceId(),c.sourceVersion(),null);
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Preview preview(long id, Mode mode, Long targetId, String name, String category) {
        var item=active(id);
        var source=masters.find(masters.masterIdForItem(id));
        var command=normalize(new Command(mode,targetId,name,category,source.masterId(),source.versionNo(),null));
        FoodMaster target=mode==Mode.EXISTING?masters.find(targetId):null;
        if(mode==Mode.EXISTING && target==null) throw invalid("대상 음식이 변경되었어. 다시 선택해줘.");
        if(target!=null) command=new Command(mode,targetId,null,null,source.masterId(),source.versionNo(),target.versionNo());
        return new Preview(item,source,target==null?command.newName():target.foodName(),
                target==null?command.newCategory():target.category(),moves.historyCount(id),
                masters.countItems(source.masterId())-1,command,UUID.randomUUID());
    }
    @Transactional
    public long move(long id, Command raw, UUID token) {
        var c=normalize(raw);
        if(token==null) throw invalid("이동 내용을 다시 확인해줘.");
        String fingerprint;
        try {fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (id+":"+json.writeValueAsString(c)).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error) {throw new IllegalStateException(error);}
        if(completed(token,fingerprint)) return id;
        // Same ascending master lock order as registration, edits and whole-food moves.
        FoodMaster source, target=null;
        if(c.mode()==Mode.EXISTING) {
            var first=masters.lock(Math.min(c.sourceId(),c.targetId()));
            var second=masters.lock(Math.max(c.sourceId(),c.targetId()));
            source=c.sourceId()<c.targetId()?first:second;
            target=c.sourceId()<c.targetId()?second:first;
        } else source=masters.lock(c.sourceId());
        if(completed(token,fingerprint)) return id;
        if(source==null || source.versionNo()!=c.sourceVersion()
                || !Objects.equals(masters.masterIdForItem(id),c.sourceId())
                || (c.mode()==Mode.EXISTING && (target==null || c.targetVersion()==null || target.versionNo()!=c.targetVersion())))
            throw invalid("확인 후 음식 정보가 바뀌었어. 최신 이동 내용을 다시 확인해줘.");
        var item=inventory.findByIdForUpdate(id);
        if(item==null || item.status()!=FoodStatus.ACTIVE) throw invalid("이동할 구매 항목을 다시 확인해줘.");
        if(target==null) {
            long newId=moves.createMaster(c.newName(),c.newCategory(),item.quantityUnit());
            target=masters.find(newId);
        }
        if(moves.transfer(id,source.masterId(),target.masterId())!=1) throw invalid("구매 항목이 변경되었어. 다시 확인해줘.");
        masters.touch(target.masterId());
        boolean removed=masters.countItems(source.masterId())==0;
        if(removed) masters.delete(source.masterId()); else masters.touch(source.masterId());
        try {moves.receipt(token,fingerprint,id,source.masterId(),target.masterId(),source.foodName(),
                target.foodName(),target.category(),removed);}
        catch(org.springframework.dao.DuplicateKeyException error) {throw invalid("이미 보낸 요청이야. 이동 내용을 다시 확인해줘.");}
        return id;
    }
    private boolean completed(UUID token,String fingerprint) {
        var receipt=moves.completed(token);
        if(receipt==null) return false;
        if(!receipt.equals(fingerprint)) throw invalid("이미 보낸 요청이야. 이동 내용을 다시 확인해줘.");
        return true;
    }
}
