package com.euiseon.friger.inventory.service;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.euiseon.friger.inventory.dao.FoodRegistrationDao;
import com.euiseon.friger.inventory.dto.FoodCreateForm;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodRegistrationService {
    private final FoodRegistrationDao requests;
    private final InventoryService inventory;
    private final ObjectMapper json;

    public FoodRegistrationService(FoodRegistrationDao requests, InventoryService inventory, ObjectMapper json) {
        this.requests = requests;
        this.inventory = inventory;
        this.json = json;
    }

    @Transactional
    public long create(FoodCreateForm form, UUID token) {
        return register(form, token, () -> inventory.create(form));
    }

    private record CategorizedRequest(String contract, FoodCreateForm food, Long masterId, Long masterVersion,
                                      String majorCode, String minorCode) {}

    /** Raw request fingerprint remains stable after the target group has changed or been merged. */
    @Transactional
    public long createCategorized(FoodCreateForm form, Long masterId, Long masterVersion,
                                 String majorCode, String minorCode, UUID token) {
        return register(new CategorizedRequest("category-v1", form, masterId, masterVersion, majorCode, minorCode),
                token, () -> inventory.createCategorized(form, masterId, masterVersion, majorCode, minorCode));
    }

    private long register(Object request, UUID token, java.util.function.LongSupplier write) {
        if (token == null) throw new InvalidFoodException(Map.of("", "등록 화면을 다시 열어줘."));
        final String payload;
        try {
            payload = json.writeValueAsString(request);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("등록 요청을 확인하지 못했습니다.", failure);
        }
        // PostgreSQL waits for a concurrent claim to commit or roll back.
        // Claim, food, history and completion are committed in the same transaction.
        if (requests.claim(token, payload) == 0) {
            var receipt = requests.find(token);
            if (receipt == null || receipt.foodId() == null || !receipt.requestPayload().equals(payload))
                throw new InvalidFoodException(Map.of("", "이미 처리한 등록 요청이야. 새로 등록하려면 등록 화면을 다시 열어줘."));
            return receipt.foodId();
        }
        long id = write.getAsLong();
        if (requests.complete(token, id) != 1) throw new IllegalStateException("등록 결과를 저장하지 못했습니다.");
        return id;
    }
}
