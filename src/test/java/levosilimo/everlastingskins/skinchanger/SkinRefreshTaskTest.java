/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Cascade atomicity invariant (R3): SkinRefreshTask must enqueue the disk
 * flush BEFORE mutating the GameProfile, so a failed enqueue leaves the
 * applied profile untouched — applied and on-disk skin stay consistent and
 * the change cannot silently revert on the next server restart.
 */
class SkinRefreshTaskTest {

    private static final Property OLD_PROPERTY = new Property("textures", "oldValue", "oldSignature");
    private static final CustomSkinProperty NEW_SKIN =
            new CustomSkinProperty("textures", "newValue", "newSignature", "MojangAPI");

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        SkinMetrics.INSTANCE.reset();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Undo any failing-storage install so the context closes on the real
        // storage (flushPending must drain real pending writes, not a mock).
        setStaticField(SkinRestorer.class, "skinStorage", ctx.storage);
        ctx.close();
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Collection<Property> texturesOf(EntityPlayerMP player) {
        return player.getGameProfile().getProperties().get("textures");
    }

    @Test
    @DisplayName("saveSkinAsync failure leaves the applied GameProfile textures untouched")
    void saveSkinAsyncFailure_leavesProfileUntouched() throws Exception {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        alice.getGameProfile().getProperties().put("textures", OLD_PROPERTY);
        SkinStorage failing = mock(SkinStorage.class);
        doThrow(new RuntimeException("simulated disk enqueue failure"))
                .when(failing).saveSkinAsync(any(UUID.class), any(CustomSkinProperty.class));
        setStaticField(SkinRestorer.class, "skinStorage", failing);

        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();
        long savesBefore = SkinMetrics.INSTANCE.snapshot().savesSubmitted();

        SkinRefreshTask.task(alice, NEW_SKIN, 0L);

        Collection<Property> textures = texturesOf(alice);
        assertEquals(1, textures.size(), "a failed save must not mutate the profile");
        assertEquals(OLD_PROPERTY, textures.iterator().next(),
                "the applied profile must keep the previous textures when persistence fails");
        assertEquals(failedBefore + 1, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
                "the partial cascade failure must be recorded");
        assertEquals(savesBefore, SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
                "a failed enqueue must not count as a submitted save");
    }

    @Test
    @DisplayName("successful save applies the stored skin to the GameProfile")
    void saveSkinAsyncSuccess_appliesStoredSkin() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        alice.getGameProfile().getProperties().put("textures", OLD_PROPERTY);

        long failedBefore = SkinMetrics.INSTANCE.snapshot().refreshesFailed();
        long completedBefore = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();
        long savesBefore = SkinMetrics.INSTANCE.snapshot().savesSubmitted();

        SkinRefreshTask.task(alice, NEW_SKIN, 0L);
        ctx.storage.flushPending();

        Collection<Property> textures = texturesOf(alice);
        assertEquals(1, textures.size());
        assertEquals(NEW_SKIN.getOriginalProperty(), textures.iterator().next(),
                "the applied profile must match the saved skin value");
        assertEquals(failedBefore, SkinMetrics.INSTANCE.snapshot().refreshesFailed(),
                "a successful refresh must not record a failure");
        assertEquals(completedBefore + 1, SkinMetrics.INSTANCE.snapshot().refreshesCompleted(),
                "a successful cascade must record a completed refresh");
        assertEquals(savesBefore + 1, SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
                "the cascade must enqueue exactly one save");
    }

}
