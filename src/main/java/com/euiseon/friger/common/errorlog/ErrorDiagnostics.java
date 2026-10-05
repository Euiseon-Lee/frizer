package com.euiseon.friger.common.errorlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.euiseon.friger.inventory.service.FoodQuantityService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import org.postgresql.util.PSQLException;
import org.springframework.security.authentication.*;

/** Only typed, explicitly approved fields enter the log; never copies request parameters. */
public final class ErrorDiagnostics {
    private static final String QUANTITY = ErrorDiagnostics.class.getName() + ".quantity";
    private static final ObjectMapper JSON = new ObjectMapper();
    private ErrorDiagnostics() { }

    public static void quantity(HttpServletRequest request, long id, FoodQuantityService.Action action,
                                long version, Long historyId, BigDecimal amount) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("operation", "INVENTORY_QUANTITY");
        fields.put("foodId", id);
        fields.put("action", action.name());
        fields.put("version", version);
        if (historyId != null) fields.put("historyId", historyId);
        if (amount != null && amount.precision() <= 11 && amount.scale() >= 0 && amount.scale() <= 2)
            fields.put("quantity", amount);
        request.setAttribute(QUANTITY, fields);
    }

    static String collect(HttpServletRequest request, Throwable error) {
        var fields = new LinkedHashMap<String, Object>();
        if (request.getAttribute(QUANTITY) instanceof Map<?, ?> quantity) fields.put("inventory", quantity);
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (int depth = 0; error != null && depth < 8 && seen.add(error); depth++, error = error.getCause()) {
            if (error instanceof BadCredentialsException) fields.put("authenticationFailure", "BAD_CREDENTIALS");
            else if (error instanceof DisabledException) fields.put("authenticationFailure", "ACCOUNT_DISABLED");
            else if (error instanceof LockedException) fields.put("authenticationFailure", "ACCOUNT_LOCKED");
            else if (error instanceof AuthenticationServiceException) fields.put("authenticationFailure", "AUTHENTICATION_SERVICE_ERROR");
            if (error instanceof SQLException sql && !fields.containsKey("sqlState")) {
                String state = sql.getSQLState();
                if (state != null && state.matches("[A-Z0-9]{5}")) {
                    fields.put("sqlState", state);
                    fields.put("databaseReason", switch (state) {
                        case "23505" -> "고유값 중복";
                        case "23503" -> "참조 대상 불일치";
                        case "23502" -> "필수값 누락";
                        case "23514" -> "데이터 제약조건 위반";
                        case "40001" -> "동시 처리 충돌";
                        case "40P01" -> "교착 상태";
                        case "57014" -> "쿼리 취소 또는 제한 시간 초과";
                        default -> "SQLSTATE로 원인 확인";
                    });
                }
                if (sql instanceof PSQLException pg && pg.getServerErrorMessage() != null) {
                    String constraint = pg.getServerErrorMessage().getConstraint();
                    if (constraint != null && constraint.matches("[a-zA-Z_][a-zA-Z0-9_]{0,62}"))
                        fields.put("constraint", constraint);
                }
            }
        }
        try {
            String json = JSON.writeValueAsString(fields);
            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 3000 ? json : "{}";
        } catch (JsonProcessingException ignored) { return "{}"; }
    }
}
