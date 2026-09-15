package com.euiseon.friger.history.dao;

import com.euiseon.friger.history.entity.FoodHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HistoryDao {
    int insert(FoodHistory history);
    int insertUpdate(@org.apache.ibatis.annotations.Param("history") FoodHistory history,
                     @org.apache.ibatis.annotations.Param("before") com.euiseon.friger.inventory.entity.FoodItem before,
                     @org.apache.ibatis.annotations.Param("after") com.euiseon.friger.inventory.entity.FoodItem after);
    java.util.List<com.euiseon.friger.history.dto.HistoryEntry> findRecent(int limit);
}
