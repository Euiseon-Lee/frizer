package com.euiseon.friger.history;

import com.euiseon.friger.history.dto.HistoryEntry;
import com.euiseon.friger.common.type.FoodActionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HistoryEntryTest {
    private HistoryEntry update(String changes) {
        return new HistoryEntry(1L, 1L, "두부", FoodActionType.UPDATE, null, null, null, null,
                changes, null, null, null, null, null, false, null, null);
    }

    @ParameterizedTest
    @CsvSource({"- → 영구 적용,경고 알림 제외 설정했어,해제 → 영구 적용", "- → 2026-10-20,경고 알림 제외 설정했어,해제 → 2026-10-20",
            "영구 적용 → 2026-10-20,경고 알림 제외 설정했어,영구 적용 → 2026-10-20", "영구 적용 → -,경고 알림 설정했어,영구 적용 → 해제",
            "2026-10-20 → -,경고 알림 설정했어,2026-10-20 → 해제"})
    void warningSummaryAndDetailsUseTheirOwnDisplayWording(String change, String summary, String detail) {
        var entry=update("경고 알림 제외 종료일: "+change);
        assertThat(entry.homeSummary()).isEqualTo(summary);
        assertThat(entry.detailFields()).containsExactly(
                new HistoryEntry.DetailField("경고 알림", detail));
        assertThat(entry.changesText()).isEqualTo("경고 알림 제외 종료일: "+change);
    }

    @Test void aMemoMentioningWarningsIsNotTreatedAsASetting() {
        var entry=update("메모: - → 경고 알림 제외 종료일: 영구 적용 → -");
        assertThat(entry.homeSummary()).isEqualTo(entry.changesText());
        assertThat(entry.detailFields()).containsExactly(
                new HistoryEntry.DetailField("메모", "- → 경고 알림 제외 종료일: 영구 적용 → -"));
    }

    @Test void openingStatusAndDateHaveSeparateHistoryLabels() {
        var entry=update("개봉 상태: 개봉함 → 미개봉\n개봉 상태 확인일: 2026-10-05 → -\n개봉일: 2025-01-01 → -");
        assertThat(entry.detailFields()).containsExactly(
                new HistoryEntry.DetailField("개봉 상태", "개봉함 → 미개봉"),
                new HistoryEntry.DetailField("개봉 확인일", "2026-10-05 → -"),
                new HistoryEntry.DetailField("개봉일", "2025-01-01 → -"));
    }

    @Test void openingConfirmationDateAloneDoesNotUseContentLabel() {
        assertThat(update("개봉 상태 확인일: - → 2026-10-05").detailFields()).containsExactly(
                new HistoryEntry.DetailField("개봉 확인일", "- → 2026-10-05"));
    }

    @Test void frozenDateUsesShortLabelWithoutChangingStoredText() {
        var entry=update("냉동 보관 시작일: - → 2026-10-05");
        assertThat(entry.detailFields()).containsExactly(
                new HistoryEntry.DetailField("냉동 보관일", "- → 2026-10-05"));
        assertThat(entry.changesText()).isEqualTo("냉동 보관 시작일: - → 2026-10-05");
    }
}
