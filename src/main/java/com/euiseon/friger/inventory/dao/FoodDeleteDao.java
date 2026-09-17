package com.euiseon.friger.inventory.dao;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FoodDeleteDao {
    record Receipt(String fingerprint, long sourceId, int itemCount, int historyCount, boolean masterRemoved) {}
    record Target(long foodId, long versionNo) {}
    @Select("SELECT fingerprint,source_id,item_count,history_count,master_removed FROM food_delete_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token} LIMIT 1")
    Receipt completed(@Param("token") UUID token);
    @Select("SELECT food_id,version_no FROM food_item WHERE user_id=#{_userId,jdbcType=BIGINT} AND master_id=#{masterId}")
    List<Target> targets(long masterId);
    @Select("SELECT food_id,version_no FROM food_item WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} AND master_id=#{masterId} FOR UPDATE")
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    Target lockTarget(long id, long masterId);
    @Select("SELECT count(*) FROM food_history WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}")
    int historyCount(long id);
    // Cancel rows reference their original row through a RESTRICT self FK, so they go first.
    @Delete("DELETE FROM food_history WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} AND reversal_of_history_id IS NOT NULL")
    int deleteCancelHistories(long id);
    @Delete("DELETE FROM food_history WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}")
    int deleteHistories(long id);
    @Delete("DELETE FROM food_item WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} AND master_id=#{masterId}")
    int deleteItem(long id, long masterId);
    @Insert("INSERT INTO food_delete_receipt(user_id,request_id,fingerprint,source_id,source_name,item_count,history_count,master_removed) VALUES (#{_userId,jdbcType=BIGINT},#{token},#{fingerprint},#{sourceId},#{sourceName},#{itemCount},#{historyCount},#{removed})")
    int receipt(UUID token, String fingerprint, long sourceId, String sourceName,
                int itemCount, int historyCount, boolean removed);
}
