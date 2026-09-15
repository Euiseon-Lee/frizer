package com.euiseon.friger.inventory.dao;

import java.util.List;
import com.euiseon.friger.inventory.entity.FoodItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryDao {
    record ItemMaster(long foodId, long masterId) {}
    @org.apache.ibatis.annotations.Select("SELECT food_id,master_id FROM food_item WHERE status='ACTIVE'")
    List<ItemMaster> activeLinks();
    default java.util.Map<Long,Long> activeMasterIds() {
        return activeLinks().stream().collect(java.util.stream.Collectors.toMap(ItemMaster::foodId,ItemMaster::masterId));
    }
    List<FoodItem> findByMaster(long id);
    long insert(FoodItem food);
    long insertForMaster(@org.apache.ibatis.annotations.Param("food") FoodItem food,
                         @org.apache.ibatis.annotations.Param("masterId") long masterId);
    List<FoodItem> findActive();
    List<FoodItem> findEnded();
    @org.apache.ibatis.annotations.Select("SELECT food_id,master_id FROM food_item WHERE status='DEPLETED'")
    List<ItemMaster> endedLinks();
    FoodItem findById(long id);
    FoodItem findByIdForUpdate(long id);
    int update(FoodItem food);
}
