/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.broadcast.FakeSkinBroadcaster;
import levosilimo.everlastingskins.broadcast.VanillaSkinBroadcaster;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Refresh-pipeline call-shape contract for the 1.12.2 task, exercised
 * against the package-private {@link SkinRefreshTask} entry point (no
 * command pipeline, fully synchronous). The per-viewer packet fan-out
 * (REMOVE+ADD) is delegated to the {@link
 * levosilimo.everlastingskins.broadcast.SkinBroadcaster} seam; this test
 * injects a {@link FakeSkinBroadcaster} to assert the task's call shape
 * without rebuilding the REMOVE+ADD+cascade pipeline.
 *
 * <p>Contract pinned here:
 * <ul>
 *   <li>The task invokes {@code broadcastProfileChange} once per refresh
 *       and then {@code trackerUntrackRetrack} when
 *       {@code Config.refreshViaEntityTracker} is enabled, in that order.</li>
 *   <li>Cascade order on the target connection:
 *       respawn &lt; position &lt; difficulty &lt; permission-level &lt;
 *       abilities. Each of those packet types is sent exactly once per
 *       refresh.</li>
 * </ul>
 *
 * <p>The packet-level REMOVE-before-ADD and self-reception guarantees
 * are pinned by {@link VanillaSkinBroadcasterTest} on the production
 * broadcaster itself; this test only asserts the task's call shape.
 *
 * <p>Cascade atomicity invariant (R3): SkinRefreshTask must enqueue the disk
 * flush BEFORE mutating the GameProfile, so a failed enqueue leaves the
 * applied profile untouched — applied and on-disk skin stay consistent and
 * the change cannot silently revert on the next server restart.
 */
class SkinRefreshTaskTest {

    private static final String TEXTURE_VALUE = "cGF5bG9hZA==";
    private static final String TEXTURE_SIGNATURE = "c2lnbmF0dXJl";

    private static final Property OLD_PROPERTY = new Property("textures", "oldValue", "oldSignature");
    private static final CustomSkinProperty NEW_SKIN =
            new CustomSkinProperty("textures", "newValue", "newSignature", "MojangAPI");

    @TempDir
    Path tempDir;

    private TestServerContext ctx;
    private EntityPlayerMP target;

    private PacketLog targetLog;

    private FakeSkinBroadcaster fakeBroadcaster;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        SkinMetrics.INSTANCE.reset();
        target = ctx.newPlayer("Target");
        ctx.attachPermissionLevelSeam(2);

        targetLog = new PacketLog();
        targetLog.attachTo(target.connection);

        // Faithful seam for vanilla NetHandlerPlayServer.setPlayerLocation:
        // it sends SPacketPlayerPosLook through the connection at this point.
        doAnswer(inv -> {
            targetLog.record(new SPacketPlayerPosLook(
                    (Double) inv.getArgument(0), (Double) inv.getArgument(1),
                    (Double) inv.getArgument(2), (Float) inv.getArgument(3),
                    (Float) inv.getArgument(4), java.util.Collections.emptySet(), 0));
            return null;
        }).when(target.connection).setPlayerLocation(anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());

        Config.refreshViaEntityTracker = true;
        fakeBroadcaster = new FakeSkinBroadcaster();
        SkinRefreshTask.setBroadcaster(fakeBroadcaster);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Undo any failing-storage install so the context closes on the real
        // storage (flushPending must drain real pending writes, not a mock).
        setStaticField(SkinRestorer.class, "skinStorage", ctx.storage);
        Config.refreshViaEntityTracker = true;
        SkinRefreshTask.setBroadcaster(new VanillaSkinBroadcaster());
        ctx.close();
    }

    /* ================================================================== */
    /*  A. Broadcaster call shape                                          */
    /* ================================================================== */

    @Test
    @DisplayName("task invokes broadcastProfileChange then trackerUntrackRetrack (tracker flag enabled)")
    void taskCalls_broadcastThenTracker() {
        refresh(target);

        assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
            "exactly one broadcastProfileChange per refresh; calls=" + fakeBroadcaster.broadcastCalls);
        assertEquals(1, fakeBroadcaster.trackerCalls.size(),
            "exactly one trackerUntrackRetrack per refresh; calls=" + fakeBroadcaster.trackerCalls);
        assertEquals(target, fakeBroadcaster.broadcastCalls.get(0).target(),
            "broadcast target must be the refreshed player");
        assertEquals(target, fakeBroadcaster.trackerCalls.get(0),
            "tracker call must target the refreshed player");
    }

    @Test
    @DisplayName("task always invokes trackerUntrackRetrack; flag check lives in the broadcaster")
    void taskCalls_trackerEvenWhenFlagDisabled() {
        Config.refreshViaEntityTracker = false;
        refresh(target);

        assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
            "broadcast still runs; calls=" + fakeBroadcaster.broadcastCalls);
        assertEquals(1, fakeBroadcaster.trackerCalls.size(),
            "task still calls broadcaster.trackerUntrackRetrack; the flag is the broadcaster's concern");
    }

    @Test
    @DisplayName("broadcaster is invoked once per refresh for every refreshViaEntityTracker value")
    void configMatrix_taskInvokesBroadcasterOnce() {
        for (boolean tracker : new boolean[]{false, true}) {
            Config.refreshViaEntityTracker = tracker;
            refresh(target);

            String combo = "tracker=" + tracker;
            assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
                combo + ": exactly one broadcast");
            assertEquals(1, fakeBroadcaster.trackerCalls.size(),
                combo + ": task always delegates tracker call");
        }
    }

    /* ================================================================== */
    /*  B. Cascade order on the target connection                           */
    /* ================================================================== */

    @Test
    @DisplayName("Cascade order on the target: respawn < position < difficulty < permission < abilities")
    void cascadeOrderRespawnPositionDifficultyPermissionAbilities() {
        refresh(target);

        List<Packet<?>> stream = targetLog.all();
        int respawn = indexOfType(stream, SPacketRespawn.class);
        int position = indexOfType(stream, SPacketPlayerPosLook.class);
        int difficulty = indexOfType(stream, SPacketServerDifficulty.class);
        int permission = indexOfType(stream, SPacketEntityStatus.class);
        int abilities = indexOfType(stream, SPacketPlayerAbilities.class);
        assertTrue(respawn >= 0 && position > respawn && difficulty > position
                && permission > difficulty && abilities > permission,
            "expected respawn < position < difficulty < permission < abilities; stream=" + stream);
    }

    @Test
    @DisplayName("Each cascade packet type is sent exactly once per refresh")
    void eachPacketTypeExactlyOnce() {
        refresh(target);
        refresh(target);

        List<Packet<?>> stream = targetLog.all();
        assertEquals(2, countOfType(stream, SPacketRespawn.class),
            "respawn must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketPlayerPosLook.class),
            "position must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketServerDifficulty.class),
            "difficulty must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketEntityStatus.class),
            "permission-level packet must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketPlayerAbilities.class),
            "abilities must be sent exactly once per refresh");
    }

    /* ================================================================== */
    /*  C. Cascade atomicity (persistence) contract                       */
    /* ================================================================== */

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
        assertEquals(0, fakeBroadcaster.broadcastCalls.size(),
            "broadcast must not run when persistence fails");
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

    @Test
    @DisplayName("restart equivalence: after the cascade the on-disk skin equals the in-memory skin")
    void afterCascade_ondiskSkinEqualsInMemorySkin() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        SkinRefreshTask.task(alice, NEW_SKIN, 0L);
        ctx.storage.flushPending();

        CustomSkinProperty fromDisk = ctx.storage.loadSkin(alice.getUniqueID());
        assertNotNull(fromDisk, "the cascade must have persisted the skin to disk");
        assertEquals(NEW_SKIN, fromDisk,
                "the on-disk skin after the cascade must equal the stored in-memory skin");
        assertEquals(ctx.storage.getSkin(alice.getUniqueID()), fromDisk,
                "a restart reloading from disk must reproduce the in-memory skin");
    }

    /* ================================================================== */
    /*  D. /skin clear with no Mojang profile                              */
    /* ================================================================== */

    @Test
    @DisplayName("clear path also invokes the broadcaster and the cascade")
    void clearPath_invokesBroadcasterAndCascade() {
        // Storage is null for target by default; textureless profile = no-op.
        // Add an applied texture so the clear actually does work.
        target.getGameProfile().getProperties().put("textures", OLD_PROPERTY);
        SkinRefreshTask.task(target, null, 0L);

        assertEquals(1, fakeBroadcaster.broadcastCalls.size(),
            "clear path also broadcasts when textures were applied; calls=" + fakeBroadcaster.broadcastCalls);
        assertEquals(1, fakeBroadcaster.trackerCalls.size(),
            "clear path also retracks");
        // Cascade tail must still hold: respawn < position < difficulty < permission < abilities.
        List<Packet<?>> stream = targetLog.all();
        int respawn = indexOfType(stream, SPacketRespawn.class);
        int position = indexOfType(stream, SPacketPlayerPosLook.class);
        int difficulty = indexOfType(stream, SPacketServerDifficulty.class);
        int permission = indexOfType(stream, SPacketEntityStatus.class);
        int abilities = indexOfType(stream, SPacketPlayerAbilities.class);
        assertTrue(respawn >= 0 && position > respawn && difficulty > position
                && permission > difficulty && abilities > permission,
            "clear cascade order must match the apply cascade; stream=" + stream);
    }

    /* ================================================================== */
    /*  E. Wire-up verification                                            */
    /* ================================================================== */

    @Test
    @DisplayName("getBroadcaster returns the wired instance")
    void broadcasterGetterReturnsWired() {
        assertEquals(fakeBroadcaster, SkinRefreshTask.getBroadcaster(),
            "the broadcaster the task uses must be the one we injected");
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    private static void refresh(EntityPlayerMP player) {
        CustomSkinProperty skin = new CustomSkinProperty("textures", TEXTURE_VALUE, TEXTURE_SIGNATURE, "Notch");
        SkinRefreshTask.task(player, skin, 0L);
    }

    private static int indexOfType(List<Packet<?>> packets, Class<? extends Packet<?>> type) {
        for (int i = 0; i < packets.size(); i++) {
            if (type.isInstance(packets.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static long countOfType(List<Packet<?>> packets, Class<? extends Packet<?>> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Collection<Property> texturesOf(EntityPlayerMP player) {
        return player.getGameProfile().getProperties().get("textures");
    }
}
