package com.euiseon.friger.inventory.dao;

import java.math.BigDecimal;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FoodQuantityDao {
    record State(long versionNo, long stockRevision, String snapshot) {}
    record Event(long historyId, String actionType, BigDecimal processedQuantityAmount, String quantityUnit,
                 long afterStockRevision, java.time.OffsetDateTime createdAt, boolean reversed) {
        public String actionLabel() { return actionType.equals("CONSUME") ? "소비" : "폐기"; }
        public String quantity() { return processedQuantityAmount.stripTrailingZeros().toPlainString()+quantityUnit; }
    }
    record Receipt(String requestPayload, Long historyId) {}
    record Version(long foodId,long versionNo,long masterId) {}
    @Select("SELECT food_id,version_no,master_id FROM food_item WHERE user_id=#{_userId,jdbcType=BIGINT} AND status='ACTIVE'")
    java.util.List<Version> activeVersions();
    @Select("SELECT i.version_no,i.stock_revision,(to_jsonb(i)||jsonb_build_object('food_name',m.food_name,'category',m.category,'category_major_code',m.category_major_code,'category_minor_code',m.category_minor_code))::text AS snapshot FROM food_item i JOIN food_master m USING(master_id) WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}")
    State state(long id);
    @Select("SELECT h.history_id,h.action_type,h.processed_quantity_amount,h.quantity_unit,h.after_stock_revision,h.created_at,EXISTS(SELECT 1 FROM food_history c WHERE c.reversal_of_history_id=h.history_id) AS reversed FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=#{id} AND h.action_type IN ('CONSUME','DISCARD') AND h.operation_id IS NOT NULL ORDER BY h.history_id DESC LIMIT 1")
    Event latest(long id);
    @Select("SELECT h.history_id,h.action_type,h.before_quantity_amount,h.after_quantity_amount,h.before_quantity_unit,h.quantity_unit,h.quantity_text,h.created_at,h.cooking_id,c.food_name AS cooking_name,h.changes_text FROM food_history h LEFT JOIN cooking_record c ON c.user_id=h.user_id AND c.cooking_id=h.cooking_id WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=#{id} AND (h.action_type IN ('CREATE','SPLIT_IN','COOK_IN') OR (h.action_type IN ('CONSUME','DISCARD','CANCEL','SPLIT_OUT','COOK_OUT') AND h.operation_id IS NOT NULL) OR (h.action_type='UPDATE' AND (h.cooking_id IS NOT NULL OR h.before_quantity_amount IS DISTINCT FROM h.after_quantity_amount OR h.before_quantity_unit IS DISTINCT FROM h.quantity_unit))) ORDER BY h.history_id DESC")
    java.util.List<QuantityChange> quantityChanges(long id);
    record EndedSummary(long foodId, String registrationQuantity, java.time.OffsetDateTime endedAt) {}
    record RegistrationQuantity(long foodId, String registrationQuantity) {}
    @Select("SELECT i.food_id, (SELECT h.quantity_text FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=i.food_id AND h.action_type IN ('CREATE','SPLIT_IN','COOK_IN') ORDER BY h.history_id LIMIT 1) AS registration_quantity FROM food_item i WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND i.status='DEPLETED'")
    java.util.List<RegistrationQuantity> endedRegistrationQuantities();
    @Select("SELECT i.food_id, (SELECT h.quantity_text FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=i.food_id AND h.action_type IN ('CREATE','SPLIT_IN','COOK_IN') ORDER BY h.history_id LIMIT 1) AS registration_quantity, (SELECT h.created_at FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=i.food_id AND h.action_type IN ('CONSUME','DISCARD','COOK_OUT','UPDATE') AND h.after_quantity_amount=0 AND h.operation_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM food_history c WHERE c.reversal_of_history_id=h.history_id) ORDER BY h.history_id DESC LIMIT 1) AS ended_at FROM food_item i WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND i.master_id=#{masterId} AND i.status='DEPLETED'")
    java.util.List<EndedSummary> endedSummaries(long masterId);
    @Insert("INSERT INTO food_quantity_receipt(user_id,request_id,request_payload,food_id) VALUES (#{_userId,jdbcType=BIGINT},#{token},#{payload},#{id}) ON CONFLICT DO NOTHING")
    int claim(UUID token,String payload,long id);
    @Select("SELECT request_payload,history_id FROM food_quantity_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token}")
    Receipt receipt(@Param("token") UUID token);
    @Update("UPDATE food_quantity_receipt SET history_id=#{history} WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token} AND history_id IS NULL")
    int complete(UUID token,long history);
    @Update("UPDATE food_item SET quantity_amount=#{amount},quantity_text=#{text},status=#{status},updated_at=clock_timestamp() WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} AND version_no=#{version}")
    int quantity(long id,long version,BigDecimal amount,String text,String status);
    @Select(value="INSERT INTO food_history(user_id,food_id,action_type,previous_storage_type,new_storage_type,quantity_text,changes_text,recorded_food_name,operation_id,processed_quantity_amount,quantity_unit,before_quantity_amount,after_quantity_amount,before_quantity_unit,before_snapshot,after_snapshot,snapshot_version,before_item_version,after_item_version,after_stock_revision,reversal_of_history_id,cooking_id,created_at) " +
        "SELECT #{_userId,jdbcType=BIGINT},i.food_id,#{action},i.storage_type,i.storage_type,#{display},#{changes},m.food_name,#{token},#{processed},i.quantity_unit,#{beforeAmount},i.quantity_amount,i.quantity_unit,CAST(#{before.snapshot} AS jsonb),CAST(#{after.snapshot} AS jsonb),1,#{before.versionNo},#{after.versionNo},#{after.stockRevision},#{reversal,jdbcType=BIGINT},#{cookingId,jdbcType=OTHER},clock_timestamp() FROM food_item i JOIN food_master m USING(master_id) WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} RETURNING history_id", affectData=true)
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    long linkedHistory(long id,String action,UUID token,BigDecimal processed,BigDecimal beforeAmount,String display,String changes,State before,State after,Long reversal,UUID cookingId);
    default long history(long id,String action,UUID token,BigDecimal processed,BigDecimal beforeAmount,String display,String changes,State before,State after,Long reversal) {
        return linkedHistory(id,action,token,processed,beforeAmount,display,changes,before,after,reversal,null);
    }
}
