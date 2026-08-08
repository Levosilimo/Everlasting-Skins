/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api;

/**
 * Public LuckPerms API stub: a concrete LuckPerms impl returning a fixed
 * StubUserManager and a fixed API version. Must be public top-level so
 * tryCreate()'s reflection (getUserManager) and getActiveBackendName()'s
 * reflection (getAPIVersion) can access the methods.
 */
public class StubLuckPerms implements LuckPerms {

    private final StubUserManager userManager;
    private final String apiVersion;

    public StubLuckPerms(StubUserManager userManager, String apiVersion) {
        this.userManager = userManager;
        this.apiVersion = apiVersion;
    }

    @Override
    public UserManager getUserManager() {
        return userManager;
    }

    @Override
    public String getAPIVersion() {
        return apiVersion;
    }
}
