/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.enums;

public enum SkinActionType {
    clear("clear"),
    url("url"),
    username("username"),
    random("random"),
    NEW("new");

    private final String name;

    SkinActionType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
