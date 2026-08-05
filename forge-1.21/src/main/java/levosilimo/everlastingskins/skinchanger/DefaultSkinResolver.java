/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Resolves a default skin from the configured DefaultSkins list
 * (SkinsRestorer storage.defaultSkins pattern):
 * <ul>
 *   <li>single-entry list - always that entry</li>
 *   <li>list contains the "&lt;random&gt;" token - random Mojang username via
 *       {@link RandomMojangSkin} resolved through the Mojang API</li>
 *   <li>multiple entries - uniform random pick</li>
 *   <li>empty or null list - null (caller falls back to the static
 *       default-skin.properties)</li>
 * </ul>
 */
public final class DefaultSkinResolver {

    public static final String RANDOM_TOKEN = "<random>";

    private DefaultSkinResolver() {}

    @Nullable
    public static Property resolveDefault(List<? extends String> list, MojangAPI mojangAPI) {
        return resolveDefault(list, mojangAPI, DefaultSkinResolver::randomUsername);
    }

    /** Test seam: injects the random-username supplier instead of {@link RandomMojangSkin}. */
    @Nullable
    static Property resolveDefault(List<? extends String> list, MojangAPI mojangAPI, Supplier<String> randomNameSupplier) {
        if (list == null || list.isEmpty()) return null;
        String pick = pickEntry(list);
        if (RANDOM_TOKEN.equals(pick)) {
            String randomName = randomNameSupplier.get();
            if (randomName == null) return null;
            return skinProperty(mojangAPI, randomName);
        }
        return skinProperty(mojangAPI, pick);
    }

    /** Uniform pick; a single-entry list always yields that entry. */
    static String pickEntry(List<? extends String> list) {
        return list.size() == 1 ? list.get(0) : list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    @Nullable
    private static String randomUsername() {
        try {
            return RandomMojangSkin.randomUsername(false, SkinVariant.CLASSIC);
        } catch (IOException e) {
            EverlastingSkins.logger.warn("Failed to pick random default-skin username", e);
            return null;
        }
    }

    @Nullable
    private static Property skinProperty(MojangAPI mojangAPI, String name) {
        return mojangAPI.getSkin(name)
                .map(MojangSkinDataResult::skinProperty)
                .map(CustomSkinProperty::getOriginalProperty)
                .orElse(null);
    }
}
