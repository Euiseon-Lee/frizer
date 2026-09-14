package com.euiseon.friger.inventory.service;

import java.util.*;
import com.euiseon.friger.inventory.dao.FoodMasterDao;
import com.euiseon.friger.inventory.dao.InventoryDao;
import com.euiseon.friger.inventory.entity.*;
import com.euiseon.friger.inventory.exception.*;
import com.euiseon.friger.common.type.StorageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodMasterService {
    private final FoodMasterDao masters;
    private final InventoryDao inventory;
    public FoodMasterService(FoodMasterDao masters, InventoryDao inventory) {
        this.masters=masters; this.inventory=inventory;
    }
    public record Group(FoodMaster master, List<FoodItem> items) {}
    public record Preview(FoodMaster source, FoodMaster target, int itemCount, int historyCount) {}
    @Transactional(readOnly=true)
    public List<FoodMasterDao.RegistrationChoice> registrationChoices() { return masters.registrationChoices(); }
    @Transactional(readOnly=true)
    public FoodMaster find(long id) {
        var master=masters.find(id);
        if(master==null) throw new FoodNotFoundException(id);
        return master;
    }
    @Transactional(readOnly=true)
    public List<FoodMaster> choices(long source) {
        return masters.all().stream().filter(m -> m.masterId()!=source).toList();
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Group> groups(StorageType storage) {
        // Keep the existing recent-item ordering, but group by explicit identity only.
        var result=new LinkedHashMap<Long,List<FoodItem>>();
        var identities=inventory.activeMasterIds();
        for(var item:inventory.findActive()) {
            if(storage==null || item.storageType()==storage)
                result.computeIfAbsent(identities.get(item.foodId()), ignored -> new ArrayList<>()).add(item);
        }
        return result.entrySet().stream().map(e -> new Group(find(e.getKey()),List.copyOf(e.getValue()))).toList();
    }
    @Transactional(readOnly=true)
    public List<FoodItem> items(long id) { find(id); return inventory.findByMaster(id); }
    @Transactional(readOnly=true)
    public long masterId(long item) {
        var id=masters.masterIdForItem(item);
        if(id==null) throw new FoodNotFoundException(item);
        return id;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Preview preview(long source,long target) {
        if(source==target) throw invalid("같은 음식끼리는 합칠 수 없어.");
        return new Preview(find(source),find(target),masters.countItems(source),masters.countHistory(source));
    }
    @Transactional
    public long merge(long source,long target,long sourceVersion,long targetVersion,UUID token) {
        if(source==target || token==null) throw invalid("합칠 음식을 다시 선택해 줘.");
        var done=masters.completed(token,source,target,sourceVersion,targetVersion);
        if(done!=null) return done;
        // Consistent lock order also covers reciprocal merges. Item edits take this lock first.
        var first=masters.lock(Math.min(source,target));
        var second=masters.lock(Math.max(source,target));
        done=masters.completed(token,source,target,sourceVersion,targetVersion);
        if(done!=null) return done;
        if(masters.hasToken(token)>0) throw invalid("이미 사용한 요청이야. 다시 확인해 줘.");
        if(first==null || second==null) throw invalid("음식이 변경되었어. 목록에서 다시 확인해 줘.");
        var from=source==first.masterId()?first:second;
        var to=target==first.masterId()?first:second;
        if(from.versionNo()!=sourceVersion || to.versionNo()!=targetVersion)
            throw invalid("확인 후 개별 구매 정보가 바뀌었어. 합치기 내용을 다시 확인해 줘.");
        int count=masters.transfer(source,target);
        masters.touch(target);
        masters.receipt(token,from,to,count);
        if(masters.delete(source)!=1) throw new IllegalStateException("음식 병합에 실패했습니다.");
        return target;
    }
    private static InvalidFoodException invalid(String message) {return new InvalidFoodException(Map.of("",message));}
}
