/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic {@link MojangAPI} fake for unit tests. Was a nested class of
 * the per-version {@code SkinCommandTest}; promoted to a top-level
 * test-support class in the common module.
 */
class FakeMojangAPI implements MojangAPI {

    final Map<String, CustomSkinProperty> skins = new HashMap<String, CustomSkinProperty>();

    void addSkin(String name, CustomSkinProperty skin) {
        skins.put(name.toLowerCase(), skin);
    }

    @Override
    public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
        CustomSkinProperty skin = skins.get(nameOrUniqueId.toLowerCase());
        if (skin == null) {
            return Optional.empty();
        }
        return Optional.of(new MojangSkinDataResult(UUID.randomUUID(), skin));
    }

    @Override
    public Optional<UUID> getUUID(String playerName) {
        return Optional.empty();
    }

    @Override
    public Optional<CustomSkinProperty> getProfile(ProfileLookup lookup) {
        return Optional.empty();
    }
}
