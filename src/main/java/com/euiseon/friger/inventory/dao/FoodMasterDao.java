package com.euiseon.friger.inventory.dao;

import java.util.List;
import java.util.UUID;
import com.euiseon.friger.inventory.entity.FoodMaster;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FoodMasterDao {
    record RegistrationChoice(long masterId, String foodName, String category, long versionNo,
                              String defaultQuantityUnit, int itemCount) {}
    @Select("SELECT m.master_id,m.food_name,m.category,m.version_no,m.default_quantity_unit," +
            "(SELECT count(*) FROM food_item i WHERE i.master_id=m.master_id AND i.status='ACTIVE') AS item_count " +
            "FROM food_master m ORDER BY m.food_name,m.master_id")
    List<RegistrationChoice> registrationChoices();
    @Select("SELECT master_id,food_name,category,version_no FROM food_master WHERE master_id=#{id}")
    FoodMaster find(long id);
    @Select("SELECT master_id FROM food_item WHERE food_id=#{id}")
    Long masterIdForItem(long id);
    @Select("SELECT master_id,food_name,category,version_no FROM food_master ORDER BY food_name,master_id")
    List<FoodMaster> all();
    @Select("SELECT master_id,food_name,category,version_no FROM food_master WHERE master_id=#{id} FOR UPDATE")
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    FoodMaster lock(long id);
    @Update("UPDATE food_master SET food_name=#{name},category=#{category},version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE master_id=#{id}")
    int update(long id, String name, String category);
    @Update("UPDATE food_master SET version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE master_id=#{id}")
    int touch(long id);
    @Update("UPDATE food_item SET updated_at=GREATEST(clock_timestamp(),updated_at+interval '1 microsecond') WHERE master_id=#{id} AND food_id<>#{exceptId}")
    int invalidateOtherItems(long id, long exceptId);
    @Update("UPDATE food_item SET master_id=#{target},updated_at=GREATEST(clock_timestamp(),updated_at+interval '1 microsecond') WHERE master_id=#{source}")
    int transfer(long source, long target);
    @Delete("DELETE FROM food_master WHERE master_id=#{id}")
    int delete(long id);
    @Select("SELECT count(*) FROM food_item WHERE master_id=#{id}")
    int countItems(long id);
    @Select("SELECT (SELECT count(*) FROM food_history h JOIN food_item i ON i.food_id=h.food_id WHERE i.master_id=#{id}) + (SELECT count(*) FROM food_item_move_receipt r JOIN food_item i ON i.food_id=r.food_id WHERE i.master_id=#{id})")
    int countHistory(long id);
    @Select("SELECT target_id FROM food_merge_receipt WHERE request_id=#{token} AND source_id=#{source} AND target_id=#{target} AND source_version=#{sourceVersion} AND target_version=#{targetVersion}")
    Long completed(UUID token, long source, long target, long sourceVersion, long targetVersion);
    @Select("SELECT count(*) FROM food_merge_receipt WHERE request_id=#{token}")
    int hasToken(@Param("token") UUID token);
    @Insert("INSERT INTO food_merge_receipt(request_id,source_id,target_id,source_version,target_version,source_name,target_name,item_count) VALUES(#{token},#{source.masterId},#{target.masterId},#{source.versionNo},#{target.versionNo},#{source.foodName},#{target.foodName},#{count})")
    int receipt(UUID token, FoodMaster source, FoodMaster target, int count);
}
