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
    @Select("SELECT i.version_no,i.stock_revision,(to_jsonb(i)||jsonb_build_object('food_name',m.food_name,'category',m.category))::text AS snapshot FROM food_item i JOIN food_master m USING(master_id) WHERE food_id=#{id}")
    State state(long id);
    @Select("SELECT h.history_id,h.action_type,h.processed_quantity_amount,h.quantity_unit,h.after_stock_revision,h.created_at,EXISTS(SELECT 1 FROM food_history c WHERE c.reversal_of_history_id=h.history_id) AS reversed FROM food_history h WHERE h.food_id=#{id} AND h.action_type IN ('CONSUME','DISCARD') AND h.operation_id IS NOT NULL ORDER BY h.history_id DESC LIMIT 1")
    Event latest(long id);
    @Insert("INSERT INTO food_quantity_receipt(request_id,request_payload,food_id) VALUES(#{token},#{payload},#{id}) ON CONFLICT DO NOTHING")
    int claim(UUID token,String payload,long id);
    @Select("SELECT request_payload,history_id FROM food_quantity_receipt WHERE request_id=#{token}")
    Receipt receipt(@Param("token") UUID token);
    @Update("UPDATE food_quantity_receipt SET history_id=#{history} WHERE request_id=#{token} AND history_id IS NULL")
    int complete(UUID token,long history);
    @Update("UPDATE food_item SET quantity_amount=#{amount},quantity_text=#{text},status=#{status},updated_at=clock_timestamp() WHERE food_id=#{id} AND version_no=#{version}")
    int quantity(long id,long version,BigDecimal amount,String text,String status);
    @Select(value="INSERT INTO food_history(food_id,action_type,previous_storage_type,new_storage_type,quantity_text,changes_text,recorded_food_name,operation_id,processed_quantity_amount,quantity_unit,before_quantity_amount,after_quantity_amount,before_snapshot,after_snapshot,snapshot_version,before_item_version,after_item_version,after_stock_revision,reversal_of_history_id,created_at) " +
        "SELECT i.food_id,#{action},i.storage_type,i.storage_type,#{display},#{changes},m.food_name,#{token},#{processed},i.quantity_unit,#{beforeAmount},i.quantity_amount,CAST(#{before.snapshot} AS jsonb),CAST(#{after.snapshot} AS jsonb),1,#{before.versionNo},#{after.versionNo},#{after.stockRevision},#{reversal,jdbcType=BIGINT},clock_timestamp() FROM food_item i JOIN food_master m USING(master_id) WHERE food_id=#{id} RETURNING history_id", affectData=true)
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    long history(long id,String action,UUID token,BigDecimal processed,BigDecimal beforeAmount,String display,String changes,State before,State after,Long reversal);
}
