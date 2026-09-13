package com.euiseon.friger.history.dao;

import com.euiseon.friger.history.entity.FoodHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HistoryDao {
    int insert(FoodHistory history);
    java.util.List<com.euiseon.friger.history.dto.HistoryEntry> findRecent(int limit);
}
