/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.enums;

public enum SkinVariant {

    CLASSIC("classic"),
    SLIM("slim"),
    ALL("all");

    private final String name;

    SkinVariant(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
