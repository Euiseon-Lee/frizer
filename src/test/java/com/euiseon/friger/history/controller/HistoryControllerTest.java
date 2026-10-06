package com.euiseon.friger.history.controller;

import com.euiseon.friger.history.dao.HistoryDao;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HistoryControllerTest {
    private final HistoryDao dao=mock(HistoryDao.class);
    // UTC October 4 is already October 5 in Seoul.
    private final HistoryController controller=new HistoryController(dao,
            Clock.fixed(Instant.parse("2026-10-04T16:00:00Z"),ZoneOffset.UTC));

    @Test void additionalDisplayLimitsReachTheQuery() {
        for (String limit : new String[]{"50", "300"}) {
            var model=new ExtendedModelMap();
            controller.history(limit,"두부","","","",model);
            assertThat(model.get("selectedLimit")).isEqualTo(limit);
            verify(dao).findFiltered(Integer.valueOf(limit),"두부",null,null);
        }
    }
    @Test void presetsIncludeTodayAndRetainNameAndLimit() {
        for(var days:new int[]{7,30}) {
            var model=new ExtendedModelMap();
            controller.history("500"," 두부 ","invalid","invalid",""+days,model);
            var start=LocalDate.of(2026,10,5).minusDays(days-1);
            assertThat(model.get("start")).isEqualTo(start.toString());
            assertThat(model.get("end")).isEqualTo("2026-10-05");
            assertThat(model.get("selectedPeriod")).isEqualTo(String.valueOf(days));
            verify(dao).findFiltered(500,"두부",start.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                    OffsetDateTime.parse("2026-10-06T00:00:00+09:00"));
        }
    }
    @Test void resettingPeriodRetainsOtherFilters() {
        var model=new ExtendedModelMap();
        controller.history("all","두부","2026-10-01","2026-10-05","all",model);
        verify(dao).findFiltered(null,"두부",null,null);
        assertThat(model.get("selectedPeriod")).isEqualTo("all");
    }
    @Test void customSelectionEnablesEditingAnExistingPreset() {
        var model=new ExtendedModelMap();
        controller.history("100","","2026-09-29","2026-10-05","custom",model);
        assertThat(model.get("selectedPeriod")).isEqualTo("custom");
    }
    @Test void oneSidedDateRangesAreSupported() {
        controller.history("100","","2026-10-05","","",new ExtendedModelMap());
        verify(dao).findFiltered(100,"",OffsetDateTime.parse("2026-10-05T00:00:00+09:00"),null);
        controller.history("100","","","2026-10-05","",new ExtendedModelMap());
        verify(dao).findFiltered(100,"",null,OffsetDateTime.parse("2026-10-06T00:00:00+09:00"));
    }
}
