package com.euiseon.friger.inventory.dao;

import java.util.List;
import com.euiseon.friger.inventory.entity.FoodItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryDao {
    long insert(FoodItem food);
    List<FoodItem> findActive();
    FoodItem findById(long id);
    FoodItem findByIdForUpdate(long id);
    int update(FoodItem food);
}
