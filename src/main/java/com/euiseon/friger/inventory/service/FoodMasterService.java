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
    @Transactional(readOnly=true)
    public List<FoodMasterDao.RegistrationChoice> registrationChoices() { return masters.registrationChoices(); }
    @Transactional(readOnly=true)
    public FoodMaster find(long id) {
        var master=masters.find(id);
        if(master==null) throw new FoodNotFoundException(id);
        return master;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Group> groups(StorageType storage,boolean ended) {
        // Keep the existing recent-item ordering, but group by explicit identity only.
        var result=new LinkedHashMap<Long,List<FoodItem>>();
        var identities=ended ? inventory.endedLinks().stream().collect(java.util.stream.Collectors.toMap(InventoryDao.ItemMaster::foodId,InventoryDao.ItemMaster::masterId)) : inventory.activeMasterIds();
        for(var item:ended ? inventory.findEnded() : inventory.findActive()) {
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
}
