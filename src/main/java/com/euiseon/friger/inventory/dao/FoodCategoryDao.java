package com.euiseon.friger.inventory.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Shared, read-only reference data. No user-owned rows are exposed here. */
@Mapper
public interface FoodCategoryDao {
    record Major(String code, String label, String example, int displayOrder,
                 boolean requiresMinor, boolean active) {}
    record Minor(String code, String majorCode, String label, String example,
                 int displayOrder, boolean active) {}

    @Select("SELECT code,label,example,display_order,requires_minor,active " +
            "FROM food_category_major ORDER BY display_order,code")
    List<Major> majors();

    @Select("SELECT c.code,c.major_code,c.label,c.example,c.display_order,c.active " +
            "FROM food_category_minor c JOIN food_category_major m ON m.code=c.major_code " +
            "ORDER BY m.display_order,c.display_order,c.code")
    List<Minor> minors();
}
