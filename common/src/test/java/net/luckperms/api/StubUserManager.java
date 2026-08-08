/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package net.luckperms.api;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.util.Tristate;

/**
 * Public LuckPerms API stub for unit tests. Must be a PUBLIC top-level class:
 * LuckPermsPermissionService reflects on {@code userManager.getClass()} and
 * invokes {@code isLoaded} through it — a package-private anonymous class is
 * not accessible from the {@code levosilimo.*} package (IllegalAccessException
 * at Method.invoke, swallowed as a denied permission).
 */
public class StubUserManager implements UserManager {

    private final boolean loaded;
    private final boolean nullUser;
    private final Map<String, Tristate> permissions;

    public StubUserManager(boolean loaded) {
        this(loaded, false, java.util.Collections.emptyMap());
    }

    public StubUserManager(boolean loaded, boolean nullUser) {
        this(loaded, nullUser, java.util.Collections.emptyMap());
    }

    public StubUserManager(boolean loaded, boolean nullUser, Map<String, Tristate> permissions) {
        this.loaded = loaded;
        this.nullUser = nullUser;
        this.permissions = permissions;
    }

    @Override
    public boolean isLoaded(UUID uuid) {
        return loaded;
    }

    @Override
    public User getUser(UUID uuid) {
        return (loaded && !nullUser) ? new StubUser(permissions) : null;
    }

    @Override
    public CompletableFuture<User> loadUser(UUID uuid) {
        return CompletableFuture.completedFuture(new StubUser(permissions));
    }
}
