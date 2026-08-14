/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.i18n;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical i18n message-key set shared by every era lane.
 *
 * <p>Version-independent key holder only (AGENTS.md rule 1): the keys,
 * the locale list and the canonical English text live here so the pre-1.13
 * lanes (forge-1.7.10 and the later 1.6.4/1.5.2/1.4.7 ports) and the
 * modern Forge line resolve the same message surface. The reference copy
 * was forge-1.21's {@code I18nUtils} {@code DEFAULT_ENGLISH} /
 * {@code LOCALE_FILES}; the {@code messages_*} Config override mapping is
 * deliberately NOT extracted (it binds to the per-version Config API).
 *
 * <p>Java 8 floor ({@code --release 8}): no {@code Map.of}/{@code List.of}.
 */
public final class LanguageKeys {

    public static final String CHANGE = "change";
    public static final String FULFILLED = "fulfilled";
    public static final String TIMEOUT = "timeout";
    public static final String ERROR = "error";
    public static final String RESTORED_FROM = "restored_from";
    public static final String CLEARED_NO_PROFILE = "cleared_no_profile";
    public static final String STORED_FROM_OTHER_USERNAME = "stored_from_other_username";
    public static final String NO_SOURCE = "no_source";
    public static final String PLAYER_ONLY = "player_only";
    public static final String PERMISSION_DENIED = "permission_denied";
    public static final String COOLDOWN = "cooldown";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String NO_SKIN_FOUND = "no_skin_found";
    public static final String NO_SKIN_FOUND_PLAIN = "no_skin_found_plain";
    public static final String MINESKIN_REJECTED = "mineskin_rejected";
    public static final String NO_RANDOM_USERNAME = "no_random_username";
    public static final String PROVIDER_NO_RESULT = "provider_no_result";
    public static final String METRICS_TOP_PLAYERS = "metrics_top_players";
    public static final String METRICS_REFRESHES = "metrics_refreshes";
    public static final String METRICS_NO_REFRESHES = "metrics_no_refreshes";
    public static final String METRICS_CLEANUP = "metrics_cleanup";
    public static final String METRICS_RESET = "metrics_reset";
    public static final String DISCORD_ANNOUNCE = "discord_announce";

    /** Locale codes that ship translation resources (mirrors the 1.21 lang dir). */
    public static final List<String> LOCALES = Collections.unmodifiableList(Arrays.asList(
            "en", "ru", "uk",
            "zh_cn", "es_es", "pt_br", "de_de", "fr_fr", "ja_jp", "ko_kr", "it_it"));

    /**
     * Canonical English text per message key; mirrors forge-1.21's
     * {@code I18nUtils.DEFAULT_ENGLISH}. A lane's {@code <locale>.lang}
     * fallback chain always ends here.
     */
    public static final Map<String, String> DEFAULT_ENGLISH = Collections.unmodifiableMap(english(
            CHANGE, "Skin change queued",
            FULFILLED, "Skin has been applied.",
            TIMEOUT, "Skin fetch timed out.",
            ERROR, "Skin fetch failed.",
            RESTORED_FROM, "Skin restored from %s",
            CLEARED_NO_PROFILE, "Skin cleared (no Mojang profile found)",
            STORED_FROM_OTHER_USERNAME,
            "Skin already stored from Mojang as %s; run /skin clear to switch usernames",
            NO_SOURCE, "No source available",
            PLAYER_ONLY, "Player only command",
            PERMISSION_DENIED, "Permission denied",
            COOLDOWN, "Please wait %ds before using /skin again",
            RATE_LIMITED, "Too many /skin commands. Try again later.",
            NO_SKIN_FOUND, "No skin found for \"%s\"",
            NO_SKIN_FOUND_PLAIN, "No skin found",
            MINESKIN_REJECTED, "MineSkin rejected the URL",
            NO_RANDOM_USERNAME, "No random username available",
            PROVIDER_NO_RESULT, "Provider returned no result",
            METRICS_TOP_PLAYERS, "Top players by refresh count:",
            METRICS_REFRESHES, " refreshes",
            METRICS_NO_REFRESHES, "(no refreshes recorded)",
            METRICS_CLEANUP, "Metrics cleanup: pruned %d stale player entries",
            METRICS_RESET, "Metrics reset",
            DISCORD_ANNOUNCE, "**%s** changed their skin to: `%s`"));

    private static final Set<String> KNOWN_KEYS =
            Collections.unmodifiableSet(new HashSet<String>(DEFAULT_ENGLISH.keySet()));

    /** True when {@code key} is part of the canonical message set. */
    public static boolean isKnownKey(String key) {
        return key != null && KNOWN_KEYS.contains(key);
    }

    private static Map<String, String> english(String... keyValuePairs) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    private LanguageKeys() {}
}
