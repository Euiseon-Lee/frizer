package com.euiseon.friger.inventory.dao;

import java.math.BigDecimal;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FoodQuantityDao {
    record State(long versionNo, long stockRevision, String snapshot) {}
    record Event(long historyId, String actionType, BigDecimal processedQuantityAmount, String quantityUnit,
                 long afterStockRevision, java.time.OffsetDateTime createdAt, boolean reversed) {
        public String label() { return actionType.equals("CONSUME") ? "소비 완료" : "폐기 완료"; }
        public String actionLabel() { return actionType.equals("CONSUME") ? "소비" : "폐기"; }
        public String quantity() { return processedQuantityAmount.stripTrailingZeros().toPlainString()+quantityUnit; }
    }
    record Receipt(String requestPayload, Long historyId) {}
    @Select("SELECT i.version_no,i.stock_revision,(to_jsonb(i)||jsonb_build_object('food_name',m.food_name,'category',m.category))::text AS snapshot FROM food_item i JOIN food_master m USING(master_id) WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}")
    State state(long id);
    @Select("SELECT h.history_id,h.action_type,h.processed_quantity_amount,h.quantity_unit,h.after_stock_revision,h.created_at,EXISTS(SELECT 1 FROM food_history c WHERE c.reversal_of_history_id=h.history_id) AS reversed FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=#{id} AND h.action_type IN ('CONSUME','DISCARD') AND h.operation_id IS NOT NULL ORDER BY h.history_id DESC LIMIT 1")
    Event latest(long id);
    @Select("SELECT h.history_id,h.action_type,h.before_quantity_amount,h.after_quantity_amount,h.before_quantity_unit,h.quantity_unit,h.quantity_text,h.created_at FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=#{id} AND (h.action_type='CREATE' OR (h.action_type IN ('CONSUME','DISCARD','CANCEL') AND h.operation_id IS NOT NULL) OR (h.action_type='UPDATE' AND (h.before_quantity_amount IS DISTINCT FROM h.after_quantity_amount OR h.before_quantity_unit IS DISTINCT FROM h.quantity_unit))) ORDER BY h.history_id DESC")
    java.util.List<QuantityChange> quantityChanges(long id);
    @Select("SELECT quantity_text FROM food_history WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} AND action_type='CREATE' ORDER BY history_id LIMIT 1")
    String registrationQuantity(long id);
    record EndedSummary(long foodId, String registrationQuantity, java.time.OffsetDateTime endedAt) {}
    @Select("SELECT i.food_id, (SELECT h.quantity_text FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=i.food_id AND h.action_type='CREATE' ORDER BY h.history_id LIMIT 1) AS registration_quantity, (SELECT h.created_at FROM food_history h WHERE h.user_id=#{_userId,jdbcType=BIGINT} AND h.food_id=i.food_id AND h.action_type IN ('CONSUME','DISCARD') AND h.after_quantity_amount=0 AND h.operation_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM food_history c WHERE c.reversal_of_history_id=h.history_id) ORDER BY h.history_id DESC LIMIT 1) AS ended_at FROM food_item i WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND i.master_id=#{masterId} AND i.status='DEPLETED'")
    java.util.List<EndedSummary> endedSummaries(long masterId);
    @Insert("INSERT INTO food_quantity_receipt(user_id,request_id,request_payload,food_id) VALUES (#{_userId,jdbcType=BIGINT},#{token},#{payload},#{id}) ON CONFLICT DO NOTHING")
    int claim(UUID token,String payload,long id);
    @Select("SELECT request_payload,history_id FROM food_quantity_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token}")
    Receipt receipt(@Param("token") UUID token);
    @Update("UPDATE food_quantity_receipt SET history_id=#{history} WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token} AND history_id IS NULL")
    int complete(UUID token,long history);
    @Update("UPDATE food_item SET quantity_amount=#{amount},quantity_text=#{text},status=#{status},updated_at=clock_timestamp() WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} AND version_no=#{version}")
    int quantity(long id,long version,BigDecimal amount,String text,String status);
    @Select(value="INSERT INTO food_history(user_id,food_id,action_type,previous_storage_type,new_storage_type,quantity_text,changes_text,recorded_food_name,operation_id,processed_quantity_amount,quantity_unit,before_quantity_amount,after_quantity_amount,before_snapshot,after_snapshot,snapshot_version,before_item_version,after_item_version,after_stock_revision,reversal_of_history_id,created_at) " +
        "SELECT #{_userId,jdbcType=BIGINT},i.food_id,#{action},i.storage_type,i.storage_type,#{display},#{changes},m.food_name,#{token},#{processed},i.quantity_unit,#{beforeAmount},i.quantity_amount,CAST(#{before.snapshot} AS jsonb),CAST(#{after.snapshot} AS jsonb),1,#{before.versionNo},#{after.versionNo},#{after.stockRevision},#{reversal,jdbcType=BIGINT},clock_timestamp() FROM food_item i JOIN food_master m USING(master_id) WHERE i.user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id} RETURNING history_id", affectData=true)
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    long history(long id,String action,UUID token,BigDecimal processed,BigDecimal beforeAmount,String display,String changes,State before,State after,Long reversal);
}
