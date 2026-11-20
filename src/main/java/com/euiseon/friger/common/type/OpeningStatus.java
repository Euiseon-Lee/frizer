package com.euiseon.friger.common.type;

public enum OpeningStatus {
    NOT_APPLICABLE("해당 없음"), UNKNOWN("개봉 여부 불확실"), UNOPENED("미개봉"), OPENED("개봉함");

    private final String label;

    OpeningStatus(String label) { this.label = label; }
    public String label() { return label; }
}
