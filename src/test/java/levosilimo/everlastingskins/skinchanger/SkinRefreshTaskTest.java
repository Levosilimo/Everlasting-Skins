/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Broadcast/cascade ordering contract of the 1.12.2 refresh pipeline,
 * exercised against the package-private {@link SkinRefreshTask} entry point
 * (no command pipeline, fully synchronous).
 *
 * <p>Contract pinned here:
 * <ul>
 *   <li>REMOVE is sent strictly before ADD (global PlayerList broadcast and
 *       every per-connection stream), and the two are neighbors in the
 *       captured stream — the 1.12.2 equivalent of the 1.21 bundle
 *       atomicity (1.12.2 has no ClientboundBundlePacket; see DIVERGENCE).</li>
 *   <li>Cascade order on the target connection:
 *       respawn &lt; position &lt; difficulty &lt; permission-level &lt;
 *       abilities. Each of those packet types is sent exactly once per
 *       refresh.</li>
 *   <li>Tracker untrack/re-track runs once per refresh when
 *       {@code Config.refreshViaEntityTracker} is enabled, zero times when
 *       disabled.</li>
 * </ul>
 *
 * <p>DIVERGENCE DOCUMENTED vs the 1.21 contract (SkinRefreshHandlerTest):
 * <ul>
 *   <li>The 1.21 tracker untrack/retrack runs before the respawn packet; on
 *       1.12.2 the EntityTracker untrack/track/updateVisibility runs at the
 *       END of the respawn cascade, after the abilities packet. Production
 *       order is pinned here, not the 1.21 order.</li>
 *   <li>The position packet (SPacketPlayerPosLook) is emitted by vanilla
 *       {@code NetHandlerPlayServer.setPlayerLocation}, which the harness
 *       connection mock stubs; a faithful seam re-emits the packet at that
 *       call site (the exact point vanilla would send it).</li>
 *   <li>1.12.2 has no BROADCAST_USE_BUNDLE and no DIMENSION_SCOPED_BROADCAST
 *       config: the broadcast is always two standalone packets to ALL
 *       players. Cross-dimension observers receive it (see
 *       {@code crossDimension_observerReceives_on1122}).</li>
 * </ul>
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
    private EntityPlayerMP observer1;
    private EntityPlayerMP observer2;
    private EntityPlayerMP netherObserver;
    private WorldServer nether;

    private List<Packet<?>> global;
    private PacketLog targetLog;
    private PacketLog observer1Log;
    private PacketLog observer2Log;
    private PacketLog netherLog;
    private List<String> trackerCalls;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        SkinMetrics.INSTANCE.reset();
        target = ctx.newPlayer("Target");
        observer1 = ctx.newPlayer("ObserverOne");
        observer2 = ctx.newPlayer("ObserverTwo");
        nether = ctx.newWorld(-1);
        netherObserver = ctx.newPlayer("NetherObserver", nether);
        ctx.attachPermissionLevelSeam(2);

        // sendPacketToAllPlayers seam: records into the global sink and
        // delivers to every online player's connection (vanilla loop).
        // NOTE: ctx.recordAndDeliverBroadcast() cannot be used here because
        // TestServerContext.newPlayer(name, world) does not register the
        // cross-dimension player in its private onlinePlayers list, so the
        // harness seam would never deliver to it.
        global = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            Packet<?> packet = (Packet<?>) inv.getArgument(0);
            global.add(packet);
            for (EntityPlayerMP online : Arrays.asList(target, observer1, observer2, netherObserver)) {
                online.connection.sendPacket(packet);
            }
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));

        targetLog = attachLog(target);
        observer1Log = attachLog(observer1);
        observer2Log = attachLog(observer2);
        netherLog = attachLog(netherObserver);

        // Faithful seam for vanilla NetHandlerPlayServer.setPlayerLocation:
        // it sends SPacketPlayerPosLook through the connection at this point.
        doAnswer(inv -> {
            targetLog.record(new SPacketPlayerPosLook(
                    (Double) inv.getArgument(0), (Double) inv.getArgument(1),
                    (Double) inv.getArgument(2), (Float) inv.getArgument(3),
                    (Float) inv.getArgument(4), Collections.emptySet(), 0));
            return null;
        }).when(target.connection).setPlayerLocation(anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());

        // Tracker interaction trace (EntityTracker untrack/track/updateVisibility).
        trackerCalls = new ArrayList<>();
        EntityTracker tracker = ctx.world.getEntityTracker();
        doAnswer(inv -> { trackerCalls.add("untrack"); return null; }).when(tracker).untrack(target);
        doAnswer(inv -> { trackerCalls.add("track"); return null; }).when(tracker).track(target);
        doAnswer(inv -> { trackerCalls.add("updateVisibility"); return null; }).when(tracker).updateVisibility(target);

        Config.refreshViaEntityTracker = true;
    }

    @AfterEach
    void tearDown() throws Exception {
        // Undo any failing-storage install so the context closes on the real
        // storage (flushPending must drain real pending writes, not a mock).
        setStaticField(SkinRestorer.class, "skinStorage", ctx.storage);
        Config.refreshViaEntityTracker = true;
        ctx.close();
    }

    /* ================================================================== */
    /*  A. Packet sequence contract                                       */
    /* ================================================================== */

    @Test
    @DisplayName("REMOVE is sent strictly before ADD in the global broadcast and on each connection")
    void removeStrictlyBeforeAdd() {
        refresh(target);

        assertEquals(2, global.size(), "exactly one REMOVE + one ADD broadcast expected");
        assertEquals(SPacketPlayerListItem.Action.REMOVE_PLAYER,
            ((SPacketPlayerListItem) global.get(0)).getAction());
        assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER,
            ((SPacketPlayerListItem) global.get(1)).getAction());

        List<Packet<?>> stream = targetLog.all();
        int removeIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.REMOVE_PLAYER);
        int addIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.ADD_PLAYER);
        assertTrue(removeIdx >= 0 && addIdx > removeIdx,
            "REMOVE must strictly precede ADD; stream=" + stream);
    }

    @Test
    @DisplayName("REMOVE+ADD are neighbors in the captured stream (bundle-atomicity equivalent)")
    void removeAddAreNeighbors() {
        refresh(target);

        List<Packet<?>> stream = targetLog.all();
        int removeIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.REMOVE_PLAYER);
        int addIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.ADD_PLAYER);
        assertEquals(removeIdx + 1, addIdx,
            "no packet may sit between REMOVE and ADD; stream=" + stream);
    }

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
    @DisplayName("Each broadcast/cascade packet type is sent exactly once per refresh")
    void eachPacketTypeExactlyOnce() {
        refresh(target);
        refresh(target);

        // Two refreshes: each packet type appears exactly twice in total
        // (the "exactly once per refresh" contract, no matcher-bugfix
        // duplicates from lib-22).
        List<Packet<?>> stream = targetLog.all();
        assertEquals(2, countOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.REMOVE_PLAYER),
            "REMOVE must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.ADD_PLAYER),
            "ADD must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketRespawn.class), "respawn must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketPlayerPosLook.class), "position must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketServerDifficulty.class),
            "difficulty must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketEntityStatus.class),
            "permission-level packet must be sent exactly once per refresh");
        assertEquals(2, countOfType(stream, SPacketPlayerAbilities.class),
            "abilities must be sent exactly once per refresh");
    }

    /* ================================================================== */
    /*  B. Config flag matrix (refreshViaEntityTracker is the only 1.12.2  */
    /*     broadcast-related flag; bundle/dimension flags are 1.21-only)   */
    /* ================================================================== */

    @Test
    @DisplayName("Tracker untrack/track/updateVisibility runs once, after the cascade, when the flag is enabled")
    void trackerRunsAfterCascade_whenEnabled() {
        Config.refreshViaEntityTracker = true;
        refresh(target);

        // The leading updateVisibility is a VANILLA side effect: 1.12.2
        // EntityPlayerMP.sendPlayerAbilities() -> updatePotionMetadata()
        // calls EntityTracker.updateVisibility(player) on its own.
        assertEquals(Arrays.asList("updateVisibility", "untrack", "track", "updateVisibility"),
            trackerCalls,
            "expected the vanilla updateVisibility (from sendPlayerAbilities), then the "
                + "flag-block untrack/track/updateVisibility");
        assertEquals(1, count(trackerCalls, "untrack"), "untrack must run exactly once");
        assertEquals(1, count(trackerCalls, "track"), "track must run exactly once");

        // The tracker block runs at the END of the respawn cascade: the
        // abilities packet is sent, then untrack/track/updateVisibility.
        org.mockito.InOrder order = inOrder(ctx.world.getEntityTracker(), target.connection);
        order.verify(target.connection).sendPacket(any(SPacketPlayerAbilities.class));
        order.verify(ctx.world.getEntityTracker()).updateVisibility(target);
        order.verify(ctx.world.getEntityTracker()).untrack(target);
        order.verify(ctx.world.getEntityTracker()).track(target);
        order.verify(ctx.world.getEntityTracker()).updateVisibility(target);
        assertTrue(indexOfType(targetLog.all(), SPacketPlayerAbilities.class) >= 0,
            "1.12.2 divergence: the tracker untrack/re-track runs at the END of the respawn "
                + "cascade, after the abilities packet (1.21 runs it before the respawn)");
    }

    @Test
    @DisplayName("Tracker is never touched when refreshViaEntityTracker is disabled")
    void trackerSkipped_whenDisabled() {
        Config.refreshViaEntityTracker = false;
        refresh(target);

        // Only the vanilla sendPlayerAbilities -> updatePotionMetadata ->
        // EntityTracker.updateVisibility side effect fires; the
        // refreshViaEntityTracker block must not run.
        assertEquals(Collections.singletonList("updateVisibility"), trackerCalls,
            "flag disabled: no untrack/track/updateVisibility from the refresh block expected");
        EntityTracker tracker = ctx.world.getEntityTracker();
        verify(tracker, never()).untrack(target);
        verify(tracker, never()).track(target);

        // The cascade itself must be unaffected by the flag.
        List<Packet<?>> stream = targetLog.all();
        int respawn = indexOfType(stream, SPacketRespawn.class);
        int position = indexOfType(stream, SPacketPlayerPosLook.class);
        int difficulty = indexOfType(stream, SPacketServerDifficulty.class);
        int permission = indexOfType(stream, SPacketEntityStatus.class);
        int abilities = indexOfType(stream, SPacketPlayerAbilities.class);
        assertTrue(respawn >= 0 && position > respawn && difficulty > position
                && permission > difficulty && abilities > permission,
            "cascade order must hold with the tracker flag disabled; stream=" + stream);
    }

    /* ================================================================== */
    /*  C. Multi-observer + multi-target                                   */
    /* ================================================================== */

    @Test
    @DisplayName("Two observers in the same dimension both receive REMOVE then ADD")
    void twoObserversBothReceiveBroadcast() {
        refresh(target);

        assertRemoveAddPair(observer1Log.all(), "observer1");
        assertRemoveAddPair(observer2Log.all(), "observer2");
    }

    @Test
    @DisplayName("The target itself receives its own REMOVE+ADD broadcast (self-reception)")
    void targetReceivesOwnBroadcast() {
        refresh(target);

        // The tab-list broadcast is delivered to every online connection,
        // the target's own included, before any cascade packet.
        List<Packet<?>> stream = targetLog.all();
        int removeIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.REMOVE_PLAYER);
        int addIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.ADD_PLAYER);
        int respawn = indexOfType(stream, SPacketRespawn.class);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1 && respawn > addIdx,
            "self-reception: REMOVE+ADD must reach the target's own connection before the respawn; stream=" + stream);
    }

    @Test
    @DisplayName("Cross-dimension observers receive the broadcast (1.12.2 global sendPacketToAllPlayers)")
    void crossDimension_observerReceives_on1122() {
        refresh(target);

        // DIVERGENCE DOCUMENTED: 1.12.2 production broadcasts via
        // sendPacketToAllPlayers, which is global. There is no
        // DIMENSION_SCOPED_BROADCAST flag on this branch.
        assertRemoveAddPair(netherLog.all(), "netherObserver");
    }

    /* ================================================================== */
    /*  D. Cascade atomicity (persistence) contract                       */
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
    /*  Helpers                                                           */
    /* ================================================================== */

    private PacketLog attachLog(EntityPlayerMP player) {
        PacketLog log = new PacketLog();
        log.attachTo(player.connection);
        return log;
    }

    private static void refresh(EntityPlayerMP player) {
        CustomSkinProperty skin = new CustomSkinProperty("textures", TEXTURE_VALUE, TEXTURE_SIGNATURE, "Notch");
        SkinRefreshTask.task(player, skin, 0L);
    }

    private static void assertRemoveAddPair(List<Packet<?>> stream, String who) {
        int removeIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.REMOVE_PLAYER);
        int addIdx = indexOfType(stream, SPacketPlayerListItem.class, SPacketPlayerListItem.Action.ADD_PLAYER);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1,
            who + " must receive REMOVE then ADD as neighbors; stream=" + stream);
    }

    private static int indexOfType(List<Packet<?>> packets, Class<? extends Packet<?>> type) {
        for (int i = 0; i < packets.size(); i++) {
            if (type.isInstance(packets.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfType(List<Packet<?>> packets, Class<? extends Packet<?>> type,
            SPacketPlayerListItem.Action action) {
        for (int i = 0; i < packets.size(); i++) {
            Packet<?> packet = packets.get(i);
            if (type.isInstance(packet)
                    && ((SPacketPlayerListItem) packet).getAction() == action) {
                return i;
            }
        }
        return -1;
    }

    private static long countOfType(List<Packet<?>> packets, Class<? extends Packet<?>> type,
            SPacketPlayerListItem.Action action) {
        return packets.stream()
                .filter(type::isInstance)
                .map(SPacketPlayerListItem.class::cast)
                .filter(p -> p.getAction() == action)
                .count();
    }

    private static long countOfType(List<Packet<?>> packets, Class<? extends Packet<?>> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static long count(List<String> events, String marker) {
        return events.stream().filter(marker::equals).count();
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
