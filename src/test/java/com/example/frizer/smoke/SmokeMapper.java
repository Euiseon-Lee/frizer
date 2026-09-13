package com.example.frizer.smoke;

import java.time.LocalDate;

import com.example.frizer.history.domain.FoodHistory;
import com.example.frizer.inventory.domain.FoodItem;
import org.apache.ibatis.annotations.Param;

/** Test-only XML mapper; never packaged in the application. */
public interface SmokeMapper {
    int selectOne();

    long insertFood(@Param("name") String name, @Param("storage") String storage,
            @Param("freeze") String freeze, @Param("frozenAt") LocalDate frozenAt,
            @Param("status") String status);

    long insertDefaults(@Param("name") String name);

    long insertMappedFood(FoodItem food);

    FoodItem findFood(@Param("id") long id);

    long insertHistory(@Param("foodId") long foodId, @Param("action") String action,
            @Param("previous") String previous, @Param("next") String next);

    FoodHistory findHistory(@Param("id") long id);

    int deleteFood(@Param("id") long id);
}
