package com.euiseon.friger.inventory.dao;

import java.util.List;
import com.euiseon.friger.inventory.entity.FoodItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryDao {
    record ItemMaster(long foodId, long masterId) {}
    @org.apache.ibatis.annotations.Select("SELECT food_id,master_id FROM food_item WHERE user_id=#{_userId,jdbcType=BIGINT} AND status='ACTIVE'")
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
    @org.apache.ibatis.annotations.Select("SELECT food_id,master_id FROM food_item WHERE user_id=#{_userId,jdbcType=BIGINT} AND status='DEPLETED'")
    List<ItemMaster> endedLinks();
    FoodItem findById(long id);
    FoodItem findByIdForUpdate(long id);
    int update(FoodItem food);
    @org.apache.ibatis.annotations.Update("UPDATE food_item child SET cooked_at=source.cooked_at FROM food_item source WHERE child.user_id=#{_userId,jdbcType=BIGINT} AND source.user_id=child.user_id AND child.food_id=#{childId} AND source.food_id=#{sourceId} AND source.cooked_at IS NOT NULL")
    int copyCookingDate(long sourceId,long childId);
}
