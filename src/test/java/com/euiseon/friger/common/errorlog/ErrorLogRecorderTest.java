package com.euiseon.friger.common.errorlog;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErrorLogRecorderTest {
    @Test void persistsApprovedDiagnosticsWithoutRequestOrDatabaseSecrets() {
        var store = mock(ErrorLogStore.class);
        var recorder = new ErrorLogRecorder(store, "test");
        var request = new MockHttpServletRequest("POST", "/inventory/3/quantity");
        request.addParameter("password", "input-secret");
        request.addParameter("memo", "private-memo");
        ErrorDiagnostics.quantity(request, 3,
                com.euiseon.friger.inventory.service.FoodQuantityService.Action.CONSUME,
                2, null, new java.math.BigDecimal("1.25"));
        var error = new org.postgresql.util.PSQLException(new org.postgresql.util.ServerErrorMessage(
                "SERROR\0C23505\0Msecret-message\0DKey (password)=(secret-value) already exists.\0nfood_quantity_receipt_pkey\0\0"));
        recorder.mark(request, "SERVER_ERROR", 500, new RuntimeException("private-cause", error));
        recorder.complete(request, 500);
        var captured = ArgumentCaptor.forClass(ErrorLogEntry.class);
        verify(store).save(captured.capture());
        var entry = captured.getValue();
        assertThat(entry.diagnosticContext()).contains("23505", "food_quantity_receipt_pkey", "고유값 중복",
                "INVENTORY_QUANTITY", "CONSUME", "1.25", "foodId");
        assertThat(entry.toString()).doesNotContain("input-secret", "private-memo", "secret-message", "secret-value", "private-cause");
    }

    @Test void capturesOnceWithoutSecretsAndSurvivesStorageFailure() {
        var store = mock(ErrorLogStore.class);
        doThrow(new IllegalStateException("password=storage-secret")).when(store).save(any());
        var recorder = new ErrorLogRecorder(store, "test-version");
        var request = new MockHttpServletRequest("POST", "/login");
        request.addParameter("password", "input-secret");
        request.setQueryString("token=query-secret");
        request.addHeader("Cookie", "JSESSIONID=cookie-secret");
        request.setRequestedSessionId("cookie-secret");
        request.setRequestedSessionIdValid(false);
        var exception = new IllegalStateException("password=message-secret", new RuntimeException("SQL value=sql-secret"));
        recorder.mark(request, "SERVER_ERROR", 500, exception);
        assertThatCode(() -> recorder.complete(request, 500)).doesNotThrowAnyException();
        recorder.complete(request, 500);
        var captured = ArgumentCaptor.forClass(ErrorLogEntry.class);
        verify(store, times(1)).save(captured.capture());
        var entry = captured.getValue();
        assertThat(entry.toString()).doesNotContain("input-secret", "query-secret", "cookie-secret", "message-secret", "sql-secret");
        assertThat(entry.sessionState()).isEqualTo("INVALID");
        assertThat(entry.stackTrace()).contains("IllegalStateException", "Caused by: java.lang.RuntimeException", "ErrorLogRecorderTest");
    }

    @Test void normalResponsesAreNotStoredAndUnknownPathsAreRedacted() {
        var store = mock(ErrorLogStore.class);
        var recorder = new ErrorLogRecorder(store, "test");
        recorder.complete(new MockHttpServletRequest("GET", "/health"), 200);
        recorder.complete(new MockHttpServletRequest("GET", "/missing"), 404);
        verifyNoInteractions(store);
        recorder.complete(new MockHttpServletRequest("GET", "/password-reset/private-token"), 500);
        var captured = ArgumentCaptor.forClass(ErrorLogEntry.class);
        verify(store).save(captured.capture());
        assertThat(captured.getValue().requestPath()).isEqualTo("[unmapped]");
    }

    @Test void stackIsBoundedAndCircularCausesTerminate() {
        var a = new RuntimeException("secret");
        var b = new RuntimeException(a);
        a.initCause(b);
        assertThat(ErrorLogRecorder.safeStack(a)).doesNotContain("secret").hasSizeLessThanOrEqualTo(16000);
    }
}
