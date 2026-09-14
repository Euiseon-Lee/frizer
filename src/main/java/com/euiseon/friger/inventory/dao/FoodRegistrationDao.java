package com.euiseon.friger.inventory.dao;

import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FoodRegistrationDao {
    record Receipt(String requestPayload, Long foodId) {}

    @Insert("INSERT INTO food_registration_receipt(request_id,request_payload) VALUES(#{token},#{payload}) ON CONFLICT(request_id) DO NOTHING")
    int claim(UUID token, String payload);

    @Select("SELECT request_payload,food_id FROM food_registration_receipt WHERE request_id=#{token}")
    Receipt find(@Param("token") UUID token);

    @Update("UPDATE food_registration_receipt SET food_id=#{foodId} WHERE request_id=#{token} AND food_id IS NULL")
    int complete(UUID token, long foodId);
}
