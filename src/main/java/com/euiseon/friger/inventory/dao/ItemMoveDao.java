package com.euiseon.friger.inventory.dao;

import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ItemMoveDao {
    record Receipt(String fingerprint, long targetId, boolean sourceRemoved) {}
    @Select("SELECT fingerprint,target_id,source_removed FROM food_item_move_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token} LIMIT 1")
    Receipt completed(@Param("token") UUID token);
    @Select("SELECT (SELECT count(*) FROM food_history WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}) + (SELECT count(*) FROM food_item_move_receipt r WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} AND NOT EXISTS (SELECT 1 FROM food_history h WHERE h.user_id=r.user_id AND h.food_id=r.food_id AND h.operation_id=r.request_id AND h.action_type='MERGE'))")
    int historyCount(long id);
    @Select(value="INSERT INTO food_master(user_id,food_name,category,default_quantity_unit) VALUES (#{_userId,jdbcType=BIGINT},#{name},#{category},#{unit}) RETURNING master_id", affectData=true)
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    long createMaster(String name, String category, String unit);
    @Update("UPDATE food_item SET master_id=#{target},updated_at=GREATEST(clock_timestamp(),updated_at+interval '1 microsecond') WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{item} AND master_id=#{source}")
    int transfer(long item, long source, long target);
    @Insert("INSERT INTO food_item_move_receipt(user_id,request_id,fingerprint,food_id,source_id,target_id,source_name,target_name,target_category,source_removed) VALUES (#{_userId,jdbcType=BIGINT},#{token},#{fingerprint},#{item},#{source},#{target},#{sourceName},#{targetName},#{category},#{removed})")
    int receipt(UUID token, String fingerprint, long item, long source, long target,
                String sourceName, String targetName, String category, boolean removed);

    @Insert("INSERT INTO food_history(user_id,food_id,action_type,previous_storage_type,new_storage_type,quantity_text,recorded_food_name,operation_id,changes_text,category_before,category_after,before_snapshot) " +
            "SELECT i.user_id,i.food_id,'MERGE',i.storage_type,i.storage_type,i.quantity_text,#{name},#{token},#{changes},CAST(#{before} AS jsonb),CAST(#{after} AS jsonb),CAST(#{context} AS jsonb) " +
            "FROM food_item i WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND i.food_id=#{item}")
    int history(long item, UUID token, String name, String changes, String before, String after, String context);
}
