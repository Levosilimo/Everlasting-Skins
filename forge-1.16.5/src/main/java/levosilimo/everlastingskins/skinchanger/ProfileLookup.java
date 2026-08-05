/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import java.util.UUID;

/**
 * Resolved username + UUID pair for profile lookups.
 * Replaces {@code Tuple<String, Optional<UUID>>} which leaked the Minecraft
 * type and forced unsafe {@code Optional.get()} calls.
 */
public record ProfileLookup(String username, UUID uuid) {}
