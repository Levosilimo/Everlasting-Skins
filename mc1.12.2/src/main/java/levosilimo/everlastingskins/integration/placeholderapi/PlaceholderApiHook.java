/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration.placeholderapi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PlaceholderApiHook {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean registered = false;

    public static void tryRegister() {
        if (registered) return;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            boolean enabled = (boolean) papiClass.getMethod("isPlaceholderAPIOnServer").invoke(null);
            if (!enabled) {
                LOGGER.debug("PlaceholderAPI not enabled; skipping");
                return;
            }
            boolean ok = new EverlastingSkinsExpansion().register();
            if (!ok) {
                LOGGER.warn("PlaceholderAPI expansion registration returned false");
                return;
            }
            registered = true;
            LOGGER.info("PlaceholderAPI expansion registered: %everlastingskins_*");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("PlaceholderAPI not present; skipping");
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.debug("PlaceholderAPI reflection failed: {}", e.getMessage());
        }
    }

    static boolean isRegistered() { return registered; }

    /** Reset internal state — exposed for testing via reflection. */
    static void resetForTest() { registered = false; }
}
