package com.euiseon.friger.history.dao;

import com.euiseon.friger.history.entity.FoodHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HistoryDao {
    default int insert(FoodHistory history) { return insertWithCategory(history, null, null); }
    int insertWithCategory(@org.apache.ibatis.annotations.Param("history") FoodHistory history,
            @org.apache.ibatis.annotations.Param("categoryBefore") com.euiseon.friger.inventory.entity.FoodMaster categoryBefore,
            @org.apache.ibatis.annotations.Param("categoryAfter") com.euiseon.friger.inventory.entity.FoodMaster categoryAfter);
    int insertUpdate(@org.apache.ibatis.annotations.Param("history") FoodHistory history,
                     @org.apache.ibatis.annotations.Param("before") com.euiseon.friger.inventory.entity.FoodItem before,
                     @org.apache.ibatis.annotations.Param("after") com.euiseon.friger.inventory.entity.FoodItem after,
                     @org.apache.ibatis.annotations.Param("categoryBefore") com.euiseon.friger.inventory.entity.FoodMaster categoryBefore,
                     @org.apache.ibatis.annotations.Param("categoryAfter") com.euiseon.friger.inventory.entity.FoodMaster categoryAfter);
    default java.util.List<com.euiseon.friger.history.dto.HistoryEntry> findRecent(int limit) {
        return findEntries(limit, "");
    }
    default java.util.List<com.euiseon.friger.history.dto.HistoryEntry> findEntries(Integer limit, String query) {
        return findFiltered(limit, query, null, null);
    }
    java.util.List<com.euiseon.friger.history.dto.HistoryEntry> findFiltered(
            @org.apache.ibatis.annotations.Param("limit") Integer limit,
            @org.apache.ibatis.annotations.Param("query") String query,
            @org.apache.ibatis.annotations.Param("from") java.time.OffsetDateTime from,
            @org.apache.ibatis.annotations.Param("until") java.time.OffsetDateTime until);
}
