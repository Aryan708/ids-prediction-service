package com.aryan.ids.domain;

import java.util.Locale;

public enum Label {
    NORMAL,
    ATTACK;

    public static Label from(String value) {
        return Label.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
