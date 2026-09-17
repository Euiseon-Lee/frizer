package com.euiseon.friger.inventory.dao;

import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FoodSplitDao {
    record Receipt(String requestPayload, Long childId) {}
    @Insert("INSERT INTO food_split_receipt(user_id,request_id,request_payload,source_id) VALUES (#{_userId,jdbcType=BIGINT},#{token},#{payload},#{id}) ON CONFLICT DO NOTHING")
    int claim(UUID token, String payload, long id);
    @Select("SELECT request_payload,child_id FROM food_split_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token}")
    Receipt receipt(@Param("token") UUID token);
    @Update("UPDATE food_split_receipt SET child_id=#{child} WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token} AND child_id IS NULL")
    int complete(UUID token, long child);
}
