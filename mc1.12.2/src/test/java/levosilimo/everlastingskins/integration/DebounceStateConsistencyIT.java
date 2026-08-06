/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Stored/applied consistency of the 1.12.2 refresh pipeline. A debounced
 * request must not persist a skin that was never applied to the GameProfile;
 * once the window expires, the same request applies normally. A clear with no
 * Mojang profile must delete the stored skin AND drop the applied textures
 * property so the two halves of the invariant stay equal.
 */
class DebounceStateConsistencyIT {

    private static final String NOTCH_VALUE = TestProperties.NOTCH.getOriginalProperty().getValue();
    private static final String JEB_VALUE = "jebTextureValue";

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
        // TestServerContext disables the debounce; each test opts into a window.
        Config.RATE_LIMIT_ENABLED = false;
    }

    @AfterEach
    void tearDown() {
        SkinCommandTestAccess.resetAPIs();
        Config.DEBOUNCE_MILLIS = 0;
        ctx.close();
    }

    @Test
    void debouncedRequest_keepsStoredEqualToApplied() {
        Config.DEBOUNCE_MILLIS = 60_000;
        FakeMojangAPI api = new FakeMojangAPI(TestProperties.NOTCH)
            .addSkin("Jeb_", new CustomSkinProperty("textures", JEB_VALUE, "jebSignature", "Jeb_"));
        SkinCommandTestAccess.setMojangAPI(api);
        EntityPlayerMP player = ctx.newPlayer("DebAlice");
        ctx.makeOp(player);

        long baseCompleted = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();
        ctx.commandManager.executeCommand(player, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(player.getUniqueID()) != null),
            "first request must store the skin");
        assertTrue(AsyncSupport.await(5000, () -> player.getGameProfile().getProperties().get("textures").size() == 1),
            "first request must apply the skin to the GameProfile");
        assertEquals(NOTCH_VALUE, profileValue(player), "first request applies the requested skin");
        // The cascade's terminal metric (recordRefreshCompleted) lands AFTER the
        // profile mutation the awaits above observe; sampling the baseline before
        // it arrives would count the first request against the debounce assertion
        // below. Await the terminal metric so the baseline is stable.
        assertTrue(AsyncSupport.await(5000,
            () -> SkinMetrics.INSTANCE.snapshot().refreshesCompleted() > baseCompleted),
            "first request must complete its profile refresh");

        long debouncedBefore = SkinMetrics.INSTANCE.snapshot().refreshesDebounced();
        long completedBefore = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();
        PacketLog log = new PacketLog();
        log.attachTo(player.connection);

        // Second request lands inside the debounce window: the refresh is
        // skipped, so the storage must keep the applied skin untouched.
        ctx.commandManager.executeCommand(player, "/skin set mojang Jeb_");
        assertTrue(AsyncSupport.await(5000,
            () -> SkinMetrics.INSTANCE.snapshot().refreshesDebounced() > debouncedBefore),
            "second request must be recorded as debounced");
        assertEquals("Notch", ctx.storage.getSource(player.getUniqueID()),
            "debounced request must not overwrite the stored source");
        assertEquals(NOTCH_VALUE, ctx.storage.getSkin(player.getUniqueID()).getOriginalProperty().getValue(),
            "debounced request must not overwrite the stored skin");
        assertEquals(NOTCH_VALUE, profileValue(player),
            "debounced request must not change the applied GameProfile");
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesCompleted() - completedBefore,
            "debounced request must not run a profile refresh");
        assertTrue(log.ofType(SPacketChat.class).stream()
            .noneMatch(c -> c.getChatComponent().getUnformattedText().contains("fulfilled")),
            "debounced request must not claim fulfilment");
    }

    @Test
    void expiredDebounceWindow_appliesNormally() throws InterruptedException {
        Config.DEBOUNCE_MILLIS = 100;
        FakeMojangAPI api = new FakeMojangAPI(TestProperties.NOTCH)
            .addSkin("Jeb_", new CustomSkinProperty("textures", JEB_VALUE, "jebSignature", "Jeb_"));
        SkinCommandTestAccess.setMojangAPI(api);
        EntityPlayerMP player = ctx.newPlayer("DebBob");
        ctx.makeOp(player);

        long baseCompleted = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();
        ctx.commandManager.executeCommand(player, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(player.getUniqueID()) != null),
            "first request must store the skin");
        assertTrue(AsyncSupport.await(5000, () -> player.getGameProfile().getProperties().get("textures").size() == 1),
            "first request must apply the skin to the GameProfile");
        // Same settle barrier as debouncedRequest_keepsStoredEqualToApplied: the
        // terminal metric is the last cascade step, so its arrival proves the
        // first pipeline fully drained before the baselines below are sampled.
        assertTrue(AsyncSupport.await(5000,
            () -> SkinMetrics.INSTANCE.snapshot().refreshesCompleted() > baseCompleted),
            "first request must complete its profile refresh");

        Thread.sleep(300);
        long debouncedBefore = SkinMetrics.INSTANCE.snapshot().refreshesDebounced();
        long completedBefore = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();

        ctx.commandManager.executeCommand(player, "/skin set mojang Jeb_");
        assertTrue(AsyncSupport.await(5000,
            () -> "Jeb_".equals(ctx.storage.getSource(player.getUniqueID()))),
            "request after the window must store the new skin");
        assertTrue(AsyncSupport.await(5000,
            () -> SkinMetrics.INSTANCE.snapshot().refreshesCompleted() > completedBefore),
            "request after the window must run a profile refresh");
        assertEquals(0, SkinMetrics.INSTANCE.snapshot().refreshesDebounced() - debouncedBefore,
            "request after the window must not be debounced");
        assertEquals(JEB_VALUE, profileValue(player), "request after the window must apply the new skin");
    }

    @Test
    void clearNoProfile_clearsStoredAndApplied() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP target = ctx.newPlayer("ClearAlice");
        ctx.makeOp(target);

        // Capture the global tab-list broadcast like ObserverPacketIT.
        List<Packet<?>> global = new ArrayList<Packet<?>>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));
        PacketLog targetLog = new PacketLog();
        targetLog.attachTo(target.connection);

        // Phase 1: apply a skin so there IS something to clear.
        long baseCompleted = SkinMetrics.INSTANCE.snapshot().refreshesCompleted();
        ctx.commandManager.executeCommand(target, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(target.getUniqueID()) != null),
            "first request must store the skin");
        assertTrue(AsyncSupport.await(5000,
            () -> target.getGameProfile().getProperties().get("textures").size() == 1),
            "first request must apply the skin to the GameProfile");
        assertTrue(AsyncSupport.await(5000, () -> global.size() == 2),
            "first request must broadcast REMOVE+ADD");
        // The broadcasts above fire mid-cascade; saveSkinAsync (and its
        // recordSaveSubmitted metric) is the next step, after respawn. Await the
        // terminal metric so no phase-1 save can land after savesBefore is
        // sampled and masquerade as a save enqueued by the clear.
        assertTrue(AsyncSupport.await(5000,
            () -> SkinMetrics.INSTANCE.snapshot().refreshesCompleted() > baseCompleted),
            "first request must complete its profile refresh");
        global.clear();
        targetLog.clear();

        // Phase 2: /skin clear with a provider that has no Mojang profile to
        // restore. Storage must be deleted AND the applied profile must drop
        // its textures property, so stored (null) equals the applied profile.
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI());
        long savesBefore = SkinMetrics.INSTANCE.snapshot().savesSubmitted();
        ctx.commandManager.executeCommand(target, "/skin clear");

        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(target.getUniqueID()) == null),
            "clear with no Mojang profile must delete the stored skin");
        assertTrue(AsyncSupport.await(5000,
            () -> target.getGameProfile().getProperties().get("textures").isEmpty()),
            "clear with no Mojang profile must drop the applied textures property");

        // The clear re-broadcasts REMOVE then ADD so observers re-learn the
        // profile; the ADD entry must not carry stale textures.
        assertTrue(AsyncSupport.await(5000, () -> global.size() == 2),
            "clear must broadcast REMOVE+ADD to observers");
        assertEquals(SPacketPlayerListItem.Action.REMOVE_PLAYER,
            ((SPacketPlayerListItem) global.get(0)).getAction());
        assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER,
            ((SPacketPlayerListItem) global.get(1)).getAction());
        assertEquals(0, texturesOnWire((SPacketPlayerListItem) global.get(1)),
            "ADD broadcast must not carry stale textures");

        // The target's own view gets the respawn cascade so it reverts to the
        // default skin, with no disk save enqueued for the cleared state.
        assertTrue(AsyncSupport.await(5000, () -> targetLog.ofType(SPacketRespawn.class).size() >= 1),
            "clear must run the respawn cascade for the target's own view");
        assertTrue(targetLog.ofType(SPacketServerDifficulty.class).size() >= 1,
            "clear cascade must resend server difficulty");
        assertTrue(targetLog.ofType(SPacketPlayerAbilities.class).size() >= 1,
            "clear cascade must resend player abilities");
        assertEquals(savesBefore, SkinMetrics.INSTANCE.snapshot().savesSubmitted(),
            "clear with no Mojang profile must not enqueue a disk save");
    }

    /**
     * Serialized property count of the ADD entry: what the client actually
     * receives. Bypasses the recompiled AddPlayerData inner class, whose
     * forgeBin class file has a broken parameter-annotations attribute.
     */
    private static int texturesOnWire(SPacketPlayerListItem packet) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writePacketData(buf);
            buf.readEnumValue(SPacketPlayerListItem.Action.class);
            if (buf.readVarInt() == 0) return -1;
            buf.readUniqueId();
            buf.skipBytes(buf.readVarInt()); // player name
            return buf.readVarInt();         // textures properties count
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize tab-list packet", e);
        } finally {
            buf.release();
        }
    }

    private static String profileValue(EntityPlayerMP player) {
        return player.getGameProfile().getProperties().get("textures").iterator().next().getValue();
    }
}
