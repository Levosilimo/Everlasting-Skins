/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.permission;

import levosilimo.everlastingskins.permission.forge.ForgePermissionService;

import java.util.UUID;

/**
 * Deterministic permission-manager control for the lane's tests: the
 * manager's {@code reset()} is package-private, so cross-package tests
 * (e.g. the skinchanger command tests) drive the fail-closed manager and a
 * fixed-op-level stub backend through this support class.
 */
public final class PermissionTestSupport {

    private PermissionTestSupport() {
    }

    /** Stub backend with a fixed player op level (protected resolveOpLevel seam). */
    public static final class StubForgePermissionService extends ForgePermissionService {
        private final int opLevel;

        public StubForgePermissionService(int opLevel) {
            this.opLevel = opLevel;
        }

        @Override
        protected int resolveOpLevel(UUID uuid) {
            return opLevel;
        }
    }

    /** Drops all registered backends (fail-closed until the next register). */
    public static void resetManager() {
        PermissionServiceManager.reset();
    }

    /** Resets the manager and registers a stub granting the given op level. */
    public static void grantOpLevel(int level) {
        resetManager();
        PermissionServiceManager.registerService(new StubForgePermissionService(level));
    }
}
