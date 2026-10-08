package com.euiseon.friger.inventory.dao;

import com.euiseon.friger.inventory.entity.FoodMaster;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FoodCategoryHistoryDao {
    @Insert("INSERT INTO food_category_history(user_id,master_id,food_name,before_category,before_major_code,before_minor_code," +
            "after_category,after_major_code,after_minor_code) VALUES (#{_userId,jdbcType=BIGINT},#{before.masterId},#{before.foodName}," +
            "#{before.category,jdbcType=VARCHAR},#{before.categoryMajorCode,jdbcType=VARCHAR},#{before.categoryMinorCode,jdbcType=VARCHAR}," +
            "#{label},#{major},#{minor,jdbcType=VARCHAR})")
    int insert(@Param("before") FoodMaster before, String major, String minor, String label);
}
