/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api.util;

public enum Tristate {
    TRUE, FALSE, UNDEFINED;

    public boolean asBoolean() {
        return this == TRUE;
    }
}
