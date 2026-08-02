/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.skinchanger.SkinCommandTest.FakeMojangAPI;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultSkinResolver}.
 *
 * <p>The 3-argument overload injects the random-username supplier so the
 * "&lt;random&gt;" token path is exercised without the network-backed
 * {@link RandomMojangSkin}.
 */
class DefaultSkinResolverTest {

    private static final String STEVE = "Steve";
    private static final String ALEX = "Alex";
    private static final String NOTCH = "Notch";
    private static final String FAKE_VALUE = "validTextureValue";
    private static final String FAKE_SIG = "validSignature";

    private static FakeMojangAPI apiWithNames(String... names) {
        FakeMojangAPI api = new FakeMojangAPI();
        for (String name : names) {
            api.addSkin(name, new CustomSkinProperty(FAKE_VALUE, FAKE_SIG, name));
        }
        return api;
    }

    @Nested
    @DisplayName("list guards")
    class ListGuards {

        @Test
        @DisplayName("null list returns null")
        void nullList_returnsNull() {
            assertNull(DefaultSkinResolver.resolveDefault(null, new FakeMojangAPI()));
        }

        @Test
        @DisplayName("empty list returns null")
        void emptyList_returnsNull() {
            assertNull(DefaultSkinResolver.resolveDefault(java.util.Collections.emptyList(), new FakeMojangAPI()));
        }
    }

    @Nested
    @DisplayName("single entry")
    class SingleEntry {

        @Test
        @DisplayName("always picks the only entry")
        void singleEntry_alwaysThatEntry() {
            for (int i = 0; i < 100; i++) {
                assertEquals(NOTCH, DefaultSkinResolver.pickEntry(java.util.Arrays.asList(NOTCH)));
            }
        }

        @Test
        @DisplayName("resolves the single entry through the Mojang API")
        void singleEntry_resolves() {
            Property prop = DefaultSkinResolver.resolveDefault(java.util.Arrays.asList(NOTCH), apiWithNames(NOTCH));
            assertNotNull(prop);
            assertEquals(FAKE_VALUE, prop.getValue());
        }

        @Test
        @DisplayName("unknown single entry returns null")
        void singleEntry_unknown_returnsNull() {
            assertNull(DefaultSkinResolver.resolveDefault(java.util.Arrays.asList("Nobody_xyz"), new FakeMojangAPI()));
        }
    }

    @Nested
    @DisplayName("multi entry")
    class MultiEntry {

        @Test
        @DisplayName("picks entries roughly uniformly")
        void multiEntry_uniformDistribution() {
            List<String> list = java.util.Arrays.asList(STEVE, ALEX, NOTCH);
            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < 3000; i++) {
                String pick = DefaultSkinResolver.pickEntry(list);
                counts.merge(pick, 1, Integer::sum);
            }
            assertEquals(3, counts.size());
            // expected 1000 per entry; ±200 is a ~7.7 sigma window
            for (String entry : list) {
                int n = counts.getOrDefault(entry, 0);
                assertTrue(n >= 800 && n <= 1200, entry + " picked " + n + " times (expected ~1000)");
            }
        }

        @Test
        @DisplayName("random token is sometimes picked in a multi-entry list")
        void randomToken_sometimesPicked() {
            List<String> list = java.util.Arrays.asList(STEVE, DefaultSkinResolver.RANDOM_TOKEN, ALEX);
            int randomPicks = 0;
            for (int i = 0; i < 3000; i++) {
                if (DefaultSkinResolver.RANDOM_TOKEN.equals(DefaultSkinResolver.pickEntry(list))) {
                    randomPicks++;
                }
            }
            // expected ~1000; even 500 is a ~19 sigma floor
            assertTrue(randomPicks >= 500, "<random> picked only " + randomPicks + " times");
        }
    }

    @Nested
    @DisplayName("random token resolution")
    class RandomToken {

        @Test
        @DisplayName("resolves the supplier's username through the Mojang API")
        void randomToken_resolvesSupplierUsername() {
            Property prop = DefaultSkinResolver.resolveDefault(
                    java.util.Arrays.asList(DefaultSkinResolver.RANDOM_TOKEN), apiWithNames(NOTCH), () -> NOTCH);
            assertNotNull(prop);
            assertEquals(FAKE_VALUE, prop.getValue());
        }

        @Test
        @DisplayName("supplier returning null yields null")
        void randomToken_nullSupplier_returnsNull() {
            assertNull(DefaultSkinResolver.resolveDefault(
                    java.util.Arrays.asList(DefaultSkinResolver.RANDOM_TOKEN), new FakeMojangAPI(), () -> null));
        }

        @Test
        @DisplayName("supplier is only invoked when the random token is picked")
        void supplierOnlyInvokedOnRandomToken() {
            AtomicInteger calls = new AtomicInteger();
            Property prop = DefaultSkinResolver.resolveDefault(
                    java.util.Arrays.asList(STEVE), apiWithNames(STEVE), () -> {
                        calls.incrementAndGet();
                        return NOTCH;
                    });
            assertNotNull(prop);
            assertEquals(0, calls.get(), "supplier must not run for a plain username pick");

            prop = DefaultSkinResolver.resolveDefault(
                    java.util.Arrays.asList(DefaultSkinResolver.RANDOM_TOKEN), apiWithNames(NOTCH), () -> {
                        calls.incrementAndGet();
                        return NOTCH;
                    });
            assertNotNull(prop);
            assertEquals(1, calls.get(), "supplier must run exactly once for <random>");
        }

        @Test
        @DisplayName("unknown random username yields null")
        void randomToken_unknownUsername_returnsNull() {
            assertNull(DefaultSkinResolver.resolveDefault(
                    java.util.Arrays.asList(DefaultSkinResolver.RANDOM_TOKEN), new FakeMojangAPI(), () -> "Nobody_xyz"));
        }
    }
}
