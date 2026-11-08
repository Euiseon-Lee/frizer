package com.euiseon.friger.common.type;

public enum StorageType {
    FRIDGE, FREEZER, ROOM;

    public static java.util.Map<StorageType,String> labels() {
        var labels = new java.util.LinkedHashMap<StorageType,String>();
        labels.put(ROOM,"실온");
        labels.put(FRIDGE,"냉장실");
        labels.put(FREEZER,"냉동실");
        return java.util.Collections.unmodifiableMap(labels);
    }
}
