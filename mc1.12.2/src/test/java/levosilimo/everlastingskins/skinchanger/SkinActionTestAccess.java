/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.util.I18nUtils;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Test-only reset seam for SkinAction's private static guard state (the
 * per-player cooldown and debounce windows) and the shared i18n table.
 * The production classes expose no reset methods; tests that re-enable the
 * rate limit or debounce must clear the maps so windows start empty, and
 * suites relying on the raw-key i18n fallback must see an empty table after
 * a test initialized it. All reflection lives here, not in test bodies, and
 * failures fail fast with the owning class and field named.
 */
public final class SkinActionTestAccess {

    private SkinActionTestAccess() {
    }

    /** Empties the per-player rate-limit and debounce windows. */
    public static void clearGuardState() {
        clearMap(SkinAction.class, "lastCommandByPlayer");
        clearMap(SkinAction.class, "commandTimestampsByPlayer");
        clearMap(SkinAction.class, "lastRefreshByPlayer");
    }

    /** Empties the i18n table, restoring the raw-key fallback for other suites. */
    public static void clearI18n() {
        clearMap(I18nUtils.class, "localizedStrings");
    }

    private static void clearMap(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).clear();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Cannot clear " + owner.getSimpleName() + "." + name + "; the field layout changed?", e);
        }
    }
}
