/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import levosilimo.everlastingskins.client.ClientSkinApplier;
import net.minecraft.src.AbstractClientPlayer;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.Minecraft;
import net.minecraft.src.Packet250CustomPayload;
import net.minecraft.src.ThreadDownloadImageData;

import java.awt.image.BufferedImage;

/**
 * 1.6.4 client-side skin receiver (lib-5 Option B: joint client side inside
 * the same jar). Registered by the {@code @NetworkMod} joint pattern
 * (clientPacketHandlerSpec on the @Mod class — FML registers the client spec
 * only on a client process, so the same jar works on both sides).
 *
 * <p>Decodes the existing {@link SkinMessage} wire format and injects the PNG
 * into the player's skin {@link ThreadDownloadImageData} via
 * {@link ClientSkinApplier} — no ASM. The player is resolved by name from
 * {@code World.playerEntities} (1.6.4 has no UUID on the wire).
 *
 * <p>Re-injection on join: the server re-broadcasts the stored skin on every
 * login, so a packet that arrives before the player entity spawns is simply
 * dropped here (the join broadcast re-delivers it). Caveat (documented, not
 * hardened): if the vanilla skin-download thread completes AFTER the
 * injection, it overwrites the pixels with the default skin — the ASM
 * hardening that would prevent this (shielding the download) is a future
 * upgrade, out of scope.
 */
public final class ClientSkinHandler implements IPacketHandler {

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        try {
            SkinMessage message = SkinMessage.decode(packet.data);
            byte[] png = message.getTexturePng();
            if (png == null) {
                // Notification-only broadcast from a pre-joint server.
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) {
                return;
            }
            EntityPlayer target = ClientSkinApplier.findPlayer(mc.theWorld, message.getPlayerName());
            if (!(target instanceof AbstractClientPlayer)) {
                // Not spawned yet — the join broadcast re-delivers.
                return;
            }
            AbstractClientPlayer clientPlayer = (AbstractClientPlayer) target;
            ThreadDownloadImageData imageData = clientPlayer.getTextureSkin();
            if (imageData == null) {
                return;
            }
            BufferedImage image = ClientSkinApplier.flattenToLegacy(ClientSkinApplier.decode(png));
            ClientSkinApplier.apply(imageData, image);
            // Re-upload the injected pixels into the existing GL texture:
            // TextureManager.loadTexture re-runs the TDI's loadTexture, which
            // uploads the new bufferedImage into the current gl id.
            mc.getTextureManager().loadTexture(clientPlayer.getLocationSkin(), imageData);
        } catch (Exception e) {
            // Never crash the network thread on a malformed/undecodable payload.
            System.err.println("EverlastingSkins: client skin apply failed: " + e);
        }
    }
}
