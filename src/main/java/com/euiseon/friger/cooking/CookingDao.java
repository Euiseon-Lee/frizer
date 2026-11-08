package com.euiseon.friger.cooking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CookingDao {
    record Record(UUID cookingId, Long resultFoodId, String foodName, BigDecimal quantityAmount,
            BigDecimal originalQuantityAmount, String quantityUnit, LocalDate cookedAt, long versionNo,
            Long resultHistoryId, OffsetDateTime createdAt, String requestPayload, boolean resultExists) {
        public String quantity() { return format(quantityAmount,quantityUnit); }
    }
    record Ingredient(long foodId, String foodName, String quantityUnit, BigDecimal usedAmount,
            BigDecimal originalUsedAmount, BigDecimal beforeAmount, BigDecimal afterAmount,
            long historyId, boolean itemExists) {
        public String used() { return format(usedAmount,quantityUnit); }
        public String before() { return format(beforeAmount,quantityUnit); }
        public String after() { return format(afterAmount,quantityUnit); }
    }
    record Receipt(String requestPayload, Long historyId) {}
    static String format(BigDecimal amount,String unit) { return amount.stripTrailingZeros().toPlainString()+unit; }

    @Select("SELECT c.*,EXISTS(SELECT 1 FROM food_item i WHERE i.user_id=c.user_id AND i.food_id=c.result_food_id) AS result_exists FROM cooking_record c WHERE c.user_id=#{_userId,jdbcType=BIGINT} AND c.cooking_id=#{id}")
    Record find(@Param("id") UUID id);
    // Receipt inserts hold FK key-share locks; corrections never change the referenced key.
    @Select("SELECT c.*,EXISTS(SELECT 1 FROM food_item i WHERE i.user_id=c.user_id AND i.food_id=c.result_food_id) AS result_exists FROM cooking_record c WHERE c.user_id=#{_userId,jdbcType=BIGINT} AND c.cooking_id=#{id} FOR NO KEY UPDATE OF c")
    @Options(flushCache=Options.FlushCachePolicy.TRUE)
    Record lock(@Param("id") UUID id);
    @Select("SELECT c.*,EXISTS(SELECT 1 FROM food_item i WHERE i.user_id=c.user_id AND i.food_id=c.food_id) AS item_exists FROM cooking_ingredient c WHERE c.user_id=#{_userId,jdbcType=BIGINT} AND c.cooking_id=#{id} ORDER BY c.food_id")
    List<Ingredient> ingredients(@Param("id") UUID id);
    @Select("SELECT EXISTS(SELECT 1 FROM cooking_record WHERE user_id=#{_userId,jdbcType=BIGINT} AND result_food_id=#{foodId}) OR EXISTS(SELECT 1 FROM cooking_ingredient WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{foodId})")
    boolean hasReference(long foodId);
    @Insert("INSERT INTO cooking_record(user_id,cooking_id,request_payload,food_name,quantity_amount,original_quantity_amount,quantity_unit,cooked_at) VALUES (#{_userId,jdbcType=BIGINT},#{form.requestId},#{payload},#{name},#{form.quantityAmount},#{form.quantityAmount},#{form.quantityUnit},#{form.cookedAt}) ON CONFLICT DO NOTHING")
    int claim(CookingForm form,String payload,String name);
    @Update("UPDATE cooking_record SET result_food_id=#{foodId},result_history_id=#{historyId} WHERE user_id=#{_userId,jdbcType=BIGINT} AND cooking_id=#{id} AND result_food_id IS NULL")
    int complete(UUID id,long foodId,long historyId);
    @Insert("INSERT INTO cooking_ingredient(user_id,cooking_id,food_id,food_name,quantity_unit,used_amount,original_used_amount,before_amount,after_amount,history_id) VALUES (#{_userId,jdbcType=BIGINT},#{id},#{foodId},#{name},#{unit},#{used},#{used},#{before},#{after},#{history})")
    int ingredient(UUID id,long foodId,String name,String unit,BigDecimal used,BigDecimal before,BigDecimal after,long history);
    @Update("UPDATE food_item SET cooked_at=#{date} WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}")
    int setCookedAt(long id,LocalDate date);
    @Select("SELECT cooked_at FROM food_item WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{id}")
    LocalDate cookedAt(long id);
    long resultHistory(UUID id,long foodId,String changes,
        com.euiseon.friger.inventory.entity.FoodMaster categoryBefore,
        com.euiseon.friger.inventory.entity.FoodMaster categoryAfter);
    @Select("SELECT count(*) FROM food_history WHERE user_id=#{_userId,jdbcType=BIGINT} AND food_id=#{foodId} AND history_id>#{afterHistory} AND (action_type NOT IN ('CONSUME','DISCARD','CANCEL','COOK_OUT','MOVE','FREEZE','MERGE','UPDATE') OR (action_type='UPDATE' AND (before_quantity_amount IS NULL OR after_quantity_amount IS NULL OR before_quantity_amount IS DISTINCT FROM after_quantity_amount OR before_quantity_unit IS DISTINCT FROM quantity_unit)) OR quantity_unit IS DISTINCT FROM #{unit} OR (action_type IN ('CONSUME','DISCARD','CANCEL','COOK_OUT') AND operation_id IS NULL))")
    int ambiguousChanges(long foodId,long afterHistory,String unit);
    @Insert("INSERT INTO cooking_correction_receipt(user_id,request_id,cooking_id,request_payload) VALUES (#{_userId,jdbcType=BIGINT},#{token},#{id},#{payload}) ON CONFLICT DO NOTHING")
    int claimCorrection(UUID token,UUID id,String payload);
    @Select("SELECT request_payload,history_id FROM cooking_correction_receipt WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token}")
    Receipt correctionReceipt(@Param("token") UUID token);
    @Update("UPDATE cooking_correction_receipt SET history_id=#{history} WHERE user_id=#{_userId,jdbcType=BIGINT} AND request_id=#{token}")
    int completeCorrection(UUID token,long history);
    @Update("UPDATE cooking_ingredient SET used_amount=#{amount},history_id=#{history} WHERE user_id=#{_userId,jdbcType=BIGINT} AND cooking_id=#{id} AND food_id=#{foodId}")
    int correctIngredient(UUID id,long foodId,BigDecimal amount,long history);
    @Update("UPDATE cooking_record SET quantity_amount=#{amount},result_history_id=#{history},version_no=version_no+1 WHERE user_id=#{_userId,jdbcType=BIGINT} AND cooking_id=#{id}")
    int correctResult(UUID id,BigDecimal amount,long history);
    @Update("UPDATE cooking_record SET version_no=version_no+1 WHERE user_id=#{_userId,jdbcType=BIGINT} AND cooking_id=#{id}")
    int touch(@Param("id") UUID id);
}
