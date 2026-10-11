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

    /** Read labels for persisted codes, including retired choices. */
    public List<Choice> storedChoices() {
        var minors=categories.minors();
        return categories.majors().stream().map(major->new Choice(major.code(),major.label(),major.example(),major.requiresMinor(),
                minors.stream().filter(minor->minor.majorCode().equals(major.code())).toList())).toList();
    }

    public record Labels(String major, String minor, boolean minorRequired) {
        public String minorDisplay() { return major!=null && !minorRequired ? "중분류 없음" : minor; }
        public String displayLabel() { return major==null?null:minor==null?major:major+" › "+minor; }
    }
    public Labels labels(String majorCode, String minorCode) {
        return labels(storedChoices(),majorCode,minorCode);
    }
    private Labels labels(List<Choice> catalog,String majorCode,String minorCode) {
        var major=catalog.stream().filter(choice->choice.code().equals(majorCode)).findFirst().orElse(null);
        return new Labels(major==null?null:major.label(),major==null?null:major.minors().stream()
                .filter(minor->minor.code().equals(minorCode)).map(minor->minor.label()).findFirst().orElse(null),major!=null && major.requiresMinor());
    }
    public List<com.euiseon.friger.inventory.dao.FoodMasterDao.RegistrationChoice> displayChoices(
            List<com.euiseon.friger.inventory.dao.FoodMasterDao.RegistrationChoice> choices) {
        var catalog=storedChoices();
        return choices.stream().map(c->new com.euiseon.friger.inventory.dao.FoodMasterDao.RegistrationChoice(
            c.masterId(),c.foodName(),labels(catalog,c.categoryMajorCode(),c.categoryMinorCode()).displayLabel(),
            c.versionNo(),c.defaultQuantityUnit(),c.itemCount(),c.categoryMajorCode(),c.categoryMinorCode())).toList();
    }
    public String normalizeMinor(String majorCode,String minorCode) {
        boolean leaf=categories.majors().stream().anyMatch(major->major.code().equals(clean(majorCode)) && !major.requiresMinor());
        return leaf?null:clean(minorCode);
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
