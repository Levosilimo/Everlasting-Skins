/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.enums;

public enum LanguageEnum {

    English("en"),
    Russian("ru"),
    Ukrainian("uk");

    private final String name;
    LanguageEnum(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
