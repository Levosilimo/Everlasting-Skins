/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.harness.WireSerializer;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Wire-level contract for SPacketPlayerListItem on 1.12.2: ADD_PLAYER carries
 * the full GameProfile (UUID + name + textures property + signature), while
 * UPDATE_DISPLAY_NAME carries only the UUID.
 */
class WireLevelBytesIT {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
    }

    @AfterEach
    void tearDown() {
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void wireSerialize_addPlayer_includesProfile() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000,
            () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
            "profile should carry the applied textures property");

        byte[] bytes = WireSerializer.serialize(
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, alice));
        // ADD_PLAYER payload is >100 bytes (profile + textures); UPDATE_DISPLAY_NAME is much smaller.
        assertTrue(bytes.length > 100,
            "ADD_PLAYER must carry the full profile (size was " + bytes.length + ")");
    }

    @Test
    void broadcastAddPlayer_decodesToCarrySignatureValue() throws IOException {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);
        List<Packet<?>> global = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            global.add(inv.getArgument(0));
            return null;
        }).when(ctx.playerList).sendPacketToAllPlayers(any(Packet.class));

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> global.size() >= 2),
            "REMOVE+ADD tab-list broadcast must reach the global list");

        // Decode the ACTUAL broadcast ADD_PLAYER payload instead of a freshly
        // constructed packet: parse the wire format (action, count, per-player
        // UUID/name/properties) back out of the serialized buffer.
        SPacketPlayerListItem addPacket = global.stream()
            .filter(SPacketPlayerListItem.class::isInstance)
            .map(SPacketPlayerListItem.class::cast)
            .filter(p -> p.getAction() == SPacketPlayerListItem.Action.ADD_PLAYER)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no ADD_PLAYER broadcast captured"));
        assertEquals(TestProperties.NOTCH.getOriginalProperty().getSignature(),
            decodeTexturesSignature(addPacket),
            "the broadcast ADD_PLAYER must carry the textures signature on the wire");
    }

    /** Re-parses the vanilla 1.12.2 ADD_PLAYER payload and returns the textures signature. */
    private static String decodeTexturesSignature(SPacketPlayerListItem packet) throws IOException {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writePacketData(buf);
            assertEquals(SPacketPlayerListItem.Action.ADD_PLAYER.ordinal(), buf.readVarInt(),
                "first field is the action ordinal");
            assertTrue(buf.readVarInt() >= 1, "at least one player entry");
            buf.readUniqueId();
            buf.readString(32767);
            int properties = buf.readVarInt();
            String signature = null;
            for (int i = 0; i < properties; i++) {
                String key = buf.readString(32767);
                buf.readString(32767); // value
                boolean hasSignature = buf.readBoolean();
                String sig = hasSignature ? buf.readString(32767) : null;
                if ("textures".equals(key)) signature = sig;
            }
            return signature;
        } finally {
            buf.release();
        }
    }

    @Test
    void wireSerialize_updateDisplayName_omitsProfile() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");

        byte[] bytes = WireSerializer.serialize(
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME, alice));
        // UPDATE_DISPLAY_NAME only carries UUID + optional displayName — no profile.
        assertTrue(bytes.length < 50,
            "UPDATE_DISPLAY_NAME must NOT carry the profile (size was " + bytes.length + ")");
    }
}
