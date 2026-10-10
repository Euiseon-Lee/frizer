package com.euiseon.friger.inventory.service;

import java.util.List;
import java.util.Map;
import com.euiseon.friger.inventory.dao.FoodCategoryDao;
import com.euiseon.friger.inventory.exception.InvalidFoodException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** One catalog/validation contract for forms, merges and workbook imports. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FoodCategoryService {
    private final FoodCategoryDao categories;

    public FoodCategoryService(FoodCategoryDao categories) { this.categories = categories; }

    public record Choice(String code, String label, String example, boolean requiresMinor,
                         List<FoodCategoryDao.Minor> minors) {
        public Choice { minors = List.copyOf(minors); }
    }

    public record Selection(String majorCode, String minorCode, String majorLabel,
                            String minorLabel, String example) {
        public String displayLabel() {
            return minorLabel == null ? majorLabel : majorLabel + " › " + minorLabel;
        }
    }

    public List<Choice> choices() {
        return choicesWithCurrent(null, null);
    }

    /** Adds only the persisted choice when it has been retired, so an edit can keep it. */
    public List<Choice> choicesWithCurrent(String majorCode, String minorCode) {
        var majors = categories.majors();
        var minors = categories.minors();
        return majors.stream().filter(major -> major.active() || major.code().equals(majorCode))
                .map(major -> new Choice(major.code(), major.label(), major.example(), major.requiresMinor(),
                        minors.stream().filter(minor -> minor.majorCode().equals(major.code())
                                && ((major.active() && minor.active())
                                || (major.code().equals(majorCode) && minor.code().equals(minorCode))))
                                .toList()))
                .filter(choice -> !choice.requiresMinor() || !choice.minors().isEmpty())
                .toList();
    }

    /** Validates a new choice. Never guesses a code from legacy free-text labels. */
    public Selection requireSelection(String majorCode, String minorCode) {
        String majorValue = clean(majorCode), minorValue = clean(minorCode);
        if (majorValue == null) throw invalid("categoryMajorCode", "대분류를 선택해줘.");
        var major = categories.majors().stream().filter(row -> row.code().equals(majorValue) && row.active())
                .findFirst().orElseThrow(() -> invalid("categoryMajorCode", "선택할 수 없는 대분류야. 다시 선택해줘."));
        if (!major.requiresMinor()) {
            if (minorValue != null) throw invalid("categoryMinorCode", "이 대분류에는 중분류가 없어. 분류를 다시 선택해줘.");
            return new Selection(major.code(), null, major.label(), null, major.example());
        }
        if (minorValue == null) throw invalid("categoryMinorCode", "중분류를 선택해줘.");
        var minor = categories.minors().stream()
                .filter(row -> row.code().equals(minorValue) && row.majorCode().equals(major.code()) && row.active())
                .findFirst().orElseThrow(() -> invalid("categoryMinorCode", "대분류에 맞는 중분류를 다시 선택해줘."));
        return new Selection(major.code(), minor.code(), major.label(), minor.label(), minor.example());
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static InvalidFoodException invalid(String field, String message) {
        return new InvalidFoodException(Map.of(field, message));
    }
}
