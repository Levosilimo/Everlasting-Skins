/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.broadcast;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import levosilimo.everlastingskins.client.ClientSkinApplier;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IImageBuffer;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.ImageBufferDownload;
import net.minecraft.src.Packet250CustomPayload;
import net.minecraft.src.ThreadDownloadImageData;

import java.awt.image.BufferedImage;

/**
 * 1.4.7 client-side skin receiver (lib-5 Option B: joint client side inside
 * the same jar). Registered by the {@code @NetworkMod} joint pattern
 * (clientPacketHandlerSpec on the @Mod class — FML registers the client spec
 * only on a client process, so the same jar works on both sides).
 *
 * <p>Decodes the existing {@link SkinMessage} wire format and injects the PNG
 * into the player's skin {@link ThreadDownloadImageData} via
 * {@link ClientSkinApplier} — no ASM. The player is resolved by name from
 * {@code World.playerEntities} (1.4.7 has no UUID on the wire); the cached
 * TDI is looked up by the player's {@code skinUrl} through
 * {@code RenderEngine.obtainImageData} (the cache keyed by skin URL, seeded
 * by {@code RenderGlobal.obtainEntitySkin} when the entity spawned).
 *
 * <p>Cape injection (per cape research): the 1.4.7 client fetches
 * {@code http://skins.minecraft.net/MinecraftCloaks/<name>.png} for EVERY
 * player with no allowlist (the render gates are only image loaded/visible/
 * not-sneaking), so byte injection suffices. The cape reuses the SAME
 * {@link ThreadDownloadImageData} class as the skin, cached in the
 * RenderEngine keyed by the player's {@code cloakUrl} — set for every player
 * by the {@code EntityOtherPlayerMP}/{@code EntityPlayerSP} constructors —
 * so the cape path mirrors the skin path with the cache key swapped from
 * {@code skinUrl} to {@code cloakUrl}.
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
            byte[] capePng = message.getCapePng();
            if (png == null && capePng == null) {
                // Notification-only broadcast from a pre-joint server.
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) {
                return;
            }
            EntityPlayer target = ClientSkinApplier.findPlayer(mc.theWorld, message.getPlayerName());
            if (target == null) {
                // Not spawned yet — the join broadcast re-delivers.
                return;
            }
            if (png != null) {
                // skinUrl is declared on Entity; access it through the Entity
                // type so the reobf pass can map the reference (javac emits the
                // receiver's declared type as the owner, and the srg FD keys are
                // the declaring class — EntityPlayer.skinUrl would stay unmapped
                // and NoSuchFieldError at runtime; found live by the slice-1
                // 1.5.2 E2E).
                String skinUrl = ((net.minecraft.src.Entity) target).skinUrl;
                if (skinUrl != null) {
                    ThreadDownloadImageData imageData =
                        mc.renderEngine.obtainImageData(skinUrl, new ImageBufferDownload());
                    if (imageData != null) {
                        BufferedImage image =
                            ClientSkinApplier.flattenToLegacy(ClientSkinApplier.decode(png));
                        ClientSkinApplier.apply(imageData, image);
                        // textureSetupComplete=false makes the next render pass
                        // re-upload the injected pixels into the existing GL texture.
                    }
                }
            }
            if (capePng != null) {
                String cloakUrl = target.cloakUrl;
                if (cloakUrl != null) {
                    ThreadDownloadImageData capeData =
                        mc.renderEngine.obtainImageData(cloakUrl, new ImageBufferDownload());
                    if (capeData != null) {
                        BufferedImage capeImage =
                            ClientSkinApplier.cropCapeToLegacy(ClientSkinApplier.decode(capePng));
                        ClientSkinApplier.apply(capeData, capeImage);
                    }
                }
            }
        } catch (Exception e) {
            // Never crash the network thread on a malformed/undecodable payload.
            System.err.println("EverlastingSkins: client skin apply failed: " + e);
        }
    }
}
