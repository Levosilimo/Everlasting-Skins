/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import com.mojang.authlib.GameProfile;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct test of the production {@link VanillaSkinBroadcaster}: pins the
 * packet sequence the broadcaster emits for each combination of the
 * explicit-observer vs broadcast-all variants and for the tracker flag.
 * These contract guarantees are what {@link
 * levosilimo.everlastingskins.skinchanger.SkinRefreshTask#task} inherits
 * by calling the broadcaster; the task test asserts the call shape, this
 * one asserts the packets.
 *
 * <p>1.12.2 has no ClientboundBundlePacket (bundle-mode is documented as
 * 1.21-only): the broadcast is always two standalone
 * SPacketPlayerListItem packets to ALL online players.
 */
class VanillaSkinBroadcasterTest {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;
    private EntityPlayerMP target;
    private EntityPlayerMP observer1;
    private EntityPlayerMP netherObserver;
    private WorldServer nether;

    private List<Packet<?>> global;
    private PacketLog targetLog;
    private PacketLog observer1Log;
    private PacketLog netherLog;

    private VanillaSkinBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        SkinMetrics.INSTANCE.reset();
        target = ctx.newPlayer("Target");
        observer1 = ctx.newPlayer("ObserverOne");
        nether = ctx.newWorld(-1);
        netherObserver = ctx.newPlayer("NetherObserver", nether);
        ctx.attachPermissionLevelSeam(2);

        global = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            Packet<?> packet = (Packet<?>) inv.getArgument(0);
            global.add(packet);
            for (EntityPlayerMP online : Arrays.asList(target, observer1, netherObserver)) {
                online.connection.sendPacket(packet);
            }
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));

        targetLog = attachLog(target);
        observer1Log = attachLog(observer1);
        netherLog = attachLog(netherObserver);

        Config.refreshViaEntityTracker = true;
        broadcaster = new VanillaSkinBroadcaster();
    }

    @AfterEach
    void tearDown() {
        Config.refreshViaEntityTracker = true;
        ctx.close();
    }

    /* ================================================================== */
    /*  Packet sequence contract                                           */
    /* ================================================================== */

    @Test
    @DisplayName("REMOVE is emitted strictly before ADD via sendPacketToAllPlayers")
    void removeStrictlyBeforeAdd() {
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        assertEquals(2, global.size(), "exactly one REMOVE + one ADD");
        assertEquals(SPacketPlayerListItem.Action.REMOVE_PLAYER,
            ((SPacketPlayerListItem) global.get(0)).getAction());
        assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER,
            ((SPacketPlayerListItem) global.get(1)).getAction());

        List<Packet<?>> stream = targetLog.all();
        int removeIdx = indexOfType(stream, SPacketPlayerListItem.Action.REMOVE_PLAYER);
        int addIdx = indexOfType(stream, SPacketPlayerListItem.Action.ADD_PLAYER);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1,
            "REMOVE+ADD must be neighbors; stream=" + stream);
    }

    @Test
    @DisplayName("REMOVE+ADD reach the target's own connection (self-reception)")
    void targetReceivesOwnBroadcast() {
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        List<Packet<?>> stream = targetLog.all();
        int removeIdx = indexOfType(stream, SPacketPlayerListItem.Action.REMOVE_PLAYER);
        int addIdx = indexOfType(stream, SPacketPlayerListItem.Action.ADD_PLAYER);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1,
            "target must receive REMOVE then ADD; stream=" + stream);
    }

    @Test
    @DisplayName("Cross-dimension observers receive the global broadcast (1.12.2 has no DIMENSION_SCOPED_BROADCAST)")
    void crossDimension_observerReceives() {
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        int removeIdx = indexOfType(netherLog.all(), SPacketPlayerListItem.Action.REMOVE_PLAYER);
        int addIdx = indexOfType(netherLog.all(), SPacketPlayerListItem.Action.ADD_PLAYER);
        assertTrue(removeIdx >= 0 && addIdx == removeIdx + 1,
            "nether observer must receive REMOVE then ADD; stream=" + netherLog.all());
    }

    @Test
    @DisplayName("Explicit-observer variant sends REMOVE+ADD only to the given observers")
    void explicitObserver_variantReachesOnlyGivenSet() {
        EntityPlayerMP[] onlyOverworld = new EntityPlayerMP[]{target, observer1};
        broadcaster.broadcastProfileChange(target.getGameProfile(), target, onlyOverworld);

        assertTrue(!observer1Log.all().isEmpty(),
            "observer1 must receive the broadcast; stream=" + observer1Log.all());
        assertEquals(0, netherLog.all().size(),
            "nether observer must receive nothing when not in the explicit set; stream=" + netherLog.all());
    }

    @Test
    @DisplayName("ADD packet carries the player's GameProfile textures")
    void addPacket_carriesTargetProfile() {
        // Add a textures property to the target's profile so the assertion
        // is non-trivial.
        target.getGameProfile().getProperties().put("textures",
            new com.mojang.authlib.properties.Property("textures", "cGF5bG9hZA==", "c2lnbmF0dXJl"));
        broadcaster.broadcastProfileChange(target.getGameProfile(), target);

        SPacketPlayerListItem add = null;
        for (Packet<?> p : targetLog.all()) {
            if (p instanceof SPacketPlayerListItem
                && ((SPacketPlayerListItem) p).getAction() == SPacketPlayerListItem.Action.ADD_PLAYER) {
                add = (SPacketPlayerListItem) p;
                break;
            }
        }
        assertNotNull(add, "ADD packet missing from stream=" + targetLog.all());
        List<?> entries = add.getEntries();
        assertNotNull(entries, "ADD packet must have entries");
        assertEquals(1, entries.size(), "ADD packet must have exactly one entry");
        // AddPlayerData is an inner class; access via reflection to dodge the
        // bad RuntimeInvisibleParameterAnnotations attribute in the deobf jar.
        Object entry = entries.get(0);
        GameProfile profile;
        try {
            java.lang.reflect.Method getProfile = entry.getClass().getMethod("getProfile");
            profile = (GameProfile) getProfile.invoke(entry);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("AddPlayerData.getProfile() reflection failed", e);
        }
        assertNotNull(profile, "ADD entry must carry the GameProfile");
        boolean hasTexture = profile.getProperties().get("textures").stream()
            .anyMatch(prop -> "cGF5bG9hZA==".equals(prop.getValue()));
        assertTrue(hasTexture, "ADD entry must carry the textures property");
    }

    /* ================================================================== */
    /*  Tracker untrack/retrack                                            */
    /* ================================================================== */

    @Test
    @DisplayName("trackerUntrackRetrack drives untrack/track/updateVisibility when flag is on")
    void trackerUntrackRetrack_runsWhenEnabled() {
        net.minecraft.entity.EntityTracker tracker = ctx.world.getEntityTracker();
        List<String> calls = new ArrayList<>();
        doAnswer(inv -> { calls.add("untrack"); return null; }).when(tracker).untrack(target);
        doAnswer(inv -> { calls.add("track"); return null; }).when(tracker).track(target);
        doAnswer(inv -> { calls.add("updateVisibility"); return null; }).when(tracker).updateVisibility(target);

        broadcaster.trackerUntrackRetrack(target);

        assertEquals(Arrays.asList("untrack", "track", "updateVisibility"), calls,
            "tracker.untrack then track then updateVisibility expected");
    }

    @Test
    @DisplayName("trackerUntrackRetrack is a no-op when refreshViaEntityTracker is off")
    void trackerUntrackRetrack_skippedWhenDisabled() {
        Config.refreshViaEntityTracker = false;
        net.minecraft.entity.EntityTracker tracker = ctx.world.getEntityTracker();

        broadcaster.trackerUntrackRetrack(target);

        verify(tracker, never()).untrack(target);
        verify(tracker, never()).track(target);
        verify(tracker, never()).updateVisibility(target);
    }

    @Test
    @DisplayName("trackerUntrackRetrack ignores non-EntityPlayerMP entities")
    void trackerUntrackRetrack_ignoresNonPlayer() {
        net.minecraft.entity.Entity other = mock(net.minecraft.entity.Entity.class);
        net.minecraft.entity.EntityTracker tracker = ctx.world.getEntityTracker();

        broadcaster.trackerUntrackRetrack(other);

        verify(tracker, never()).untrack(other);
        verify(tracker, never()).track(other);
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    private PacketLog attachLog(EntityPlayerMP player) {
        PacketLog log = new PacketLog();
        log.attachTo(player.connection);
        return log;
    }

    private int indexOfType(List<Packet<?>> packets, SPacketPlayerListItem.Action action) {
        for (int i = 0; i < packets.size(); i++) {
            Packet<?> packet = packets.get(i);
            if (packet instanceof SPacketPlayerListItem
                && ((SPacketPlayerListItem) packet).getAction() == action) {
                return i;
            }
        }
        return -1;
    }
}
