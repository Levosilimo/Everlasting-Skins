/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

/**
 * Test bootstrap for the shared {@code :common} PermissionServiceManager.
 * Production registers candidates in {@code EverlastingSkins.init()} (vanilla,
 * LuckPerms if present, then Forge's PermissionAPI); the manager itself is
 * fail-closed when nothing is registered. Tests that exercise permission
 * gates install the vanilla backend here instead of relying on the removed
 * uninitialized fallback. Same package as the manager so it can reach the
 * package-private {@code reset()}.
 */
public final class PermissionTestSupport {

    private PermissionTestSupport() {
    }

    /** Registers the vanilla op-level backend (priority 0). */
    public static void installVanilla() {
        PermissionServiceManager.registerService(new VanillaPermissionService());
    }

    /** Unregisters all backends; call from @AfterEach / context close. */
    public static void uninstall() {
        PermissionServiceManager.reset();
    }
}
