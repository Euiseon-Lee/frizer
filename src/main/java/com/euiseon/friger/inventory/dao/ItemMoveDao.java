package com.euiseon.friger.inventory.dao;

import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ItemMoveDao {
    @Select("SELECT fingerprint FROM food_item_move_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token}")
    String completed(@Param("token") UUID token);
    @Select("SELECT (SELECT count(*) FROM food_history WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}) + (SELECT count(*) FROM food_item_move_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id})")
    int historyCount(long id);
    @Select(value="INSERT INTO food_master(user_id,food_name,category,default_quantity_unit) VALUES (#{_userId,jdbcType=BIGINT},#{name},#{category},#{unit}) RETURNING master_id", affectData=true)
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    long createMaster(String name, String category, String unit);
    @Update("UPDATE food_item SET master_id=#{target},updated_at=GREATEST(clock_timestamp(),updated_at+interval '1 microsecond') WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{item} AND master_id=#{source} AND status='ACTIVE'")
    int transfer(long item, long source, long target);
    @Insert("INSERT INTO food_item_move_receipt(user_id,request_id,fingerprint,food_id,source_id,target_id,source_name,target_name,target_category,source_removed) VALUES (#{_userId,jdbcType=BIGINT},#{token},#{fingerprint},#{item},#{source},#{target},#{sourceName},#{targetName},#{category},#{removed})")
    int receipt(UUID token, String fingerprint, long item, long source, long target,
                String sourceName, String targetName, String category, boolean removed);
}
