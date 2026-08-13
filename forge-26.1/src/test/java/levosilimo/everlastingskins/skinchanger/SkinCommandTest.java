/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.forge26_1.skinchanger.SkinRefreshHandler;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkinCommandTest {

    private static final String PLAYER_NAME = "Steve";
    private static final String STORED_SOURCE = "Notch";
    private static final String FAKE_VALUE = "validTextureValue";
    private static final String FAKE_SIG = "validSignature";

    static class FakeMojangAPI implements MojangAPI {
        final Map<String, CustomSkinProperty> skins = new HashMap<>();

        void addSkin(String name, CustomSkinProperty skin) {
            skins.put(name.toLowerCase(), skin);
        }

        @Override
        public Optional<MojangSkinDataResult> getSkin(String nameOrUniqueId) {
            CustomSkinProperty skin = skins.get(nameOrUniqueId.toLowerCase());
            if (skin == null) return Optional.empty();
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

    @Nested
    @DisplayName("tryRestoreFromMojang")
    class TryRestoreFromMojang {

        @Test
        @DisplayName("returns skin when Mojang has a profile for storedSource")
        void restoreFromStoredSource() {
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(STORED_SOURCE, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, STORED_SOURCE));

            SkinRefreshHandler.MojangRestoreResult result = SkinRefreshHandler.tryRestoreFromMojang(api, STORED_SOURCE, PLAYER_NAME);

            assertNotNull(result);
            assertEquals(FAKE_VALUE, result.skin.getOriginalProperty().value());
            assertEquals(STORED_SOURCE, result.licensedUsername);
        }

        @Test
        @DisplayName("uses playerName when storedSource is null")
        void fallbackFromNullSource() {
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(PLAYER_NAME, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, PLAYER_NAME));

            SkinRefreshHandler.MojangRestoreResult result = SkinRefreshHandler.tryRestoreFromMojang(api, null, PLAYER_NAME);

            assertNotNull(result);
            assertEquals(FAKE_VALUE, result.skin.getOriginalProperty().value());
            assertEquals(PLAYER_NAME, result.licensedUsername);
        }

        @Test
        @DisplayName("uses playerName when storedSource is empty")
        void fallbackFromEmptySource() {
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(PLAYER_NAME, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, PLAYER_NAME));

            SkinRefreshHandler.MojangRestoreResult result = SkinRefreshHandler.tryRestoreFromMojang(api, "", PLAYER_NAME);

            assertNotNull(result);
            assertEquals(FAKE_VALUE, result.skin.getOriginalProperty().value());
            assertEquals(PLAYER_NAME, result.licensedUsername);
        }

        @Test
        @DisplayName("uses storedSource over playerName when both are present")
        void storedSourceTakesPriority() {
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(STORED_SOURCE, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, STORED_SOURCE));

            SkinRefreshHandler.MojangRestoreResult result = SkinRefreshHandler.tryRestoreFromMojang(api, STORED_SOURCE, PLAYER_NAME);

            assertNotNull(result);
            assertEquals(STORED_SOURCE, result.licensedUsername);
        }

        @Test
        @DisplayName("returns null when Mojang has no profile for the username")
        void mojangNoProfile() {
            FakeMojangAPI api = new FakeMojangAPI();

            SkinRefreshHandler.MojangRestoreResult result = SkinRefreshHandler.tryRestoreFromMojang(api, STORED_SOURCE, PLAYER_NAME);

            assertNull(result);
        }

        @Test
        @DisplayName("returns null when Mojang skin isEmpty (default skin value)")
        void mojangEmptySkin() {
            CustomSkinProperty emptySkin = new CustomSkinProperty("textures", "", "", STORED_SOURCE);
            FakeMojangAPI api = new FakeMojangAPI();
            api.addSkin(STORED_SOURCE, emptySkin);

            SkinRefreshHandler.MojangRestoreResult result = SkinRefreshHandler.tryRestoreFromMojang(api, STORED_SOURCE, PLAYER_NAME);

            assertNull(result);
        }
    }
}
