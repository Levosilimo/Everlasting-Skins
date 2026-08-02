/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package net.luckperms.api;

public final class LuckPermsProvider {
    private static LuckPerms instance = null;

    public static LuckPerms get() {
        if (instance == null) throw new IllegalStateException("Not loaded");
        return instance;
    }

    public static void register(LuckPerms inst) {
        instance = inst;
    }

    public static void unregister() {
        instance = null;
    }
}
