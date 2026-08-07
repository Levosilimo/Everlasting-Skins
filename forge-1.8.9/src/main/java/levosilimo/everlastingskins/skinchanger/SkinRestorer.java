/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.util.UUIDUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 1.8.9 binding for the {@code :common} {@link SkinStorage}.
 *
 * Storage constraint (memory #1123): SkinStorage is keyed by UUID only —
 * no EntityPlayer/GameProfile object ever crosses the lane boundary into
 * :common. This class extracts the player UUID at the lane edge:
 * {@link EntityPlayer#getPersistentID()} (inherited from Entity, dashed
 * form) and {@link EntityPlayer#getGameProfile()} (authlib surface) are
 * the 1.8.9 legacy APIs; 32-char no-dash UUID strings are normalized via
 * {@link UUIDUtils#convertToDashed(String)}.
 */
public final class SkinRestorer {

    private static volatile SkinStorage skinStorage;

    private SkinRestorer() {}

    @Nullable
    public static SkinStorage getSkinStorage() {
        return skinStorage;
    }

    /** Server-start bootstrap: creates the data dir and the storage. */
    public static void init(FMLServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        Path dataDir = server.getFile("EverlastingSkins").toPath();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            EverlastingSkins.LOGGER.error("Failed to create skin data directory", e);
        }
        SkinIO skinIO = new SkinIO(dataDir);
        // Startup integrity sweep: quarantine bit-rotten records before the
        // first player logs in, so SkinStorage only ever sees verified files.
        skinIO.validateAllFiles();
        skinStorage = new SkinStorage(skinIO);
        EverlastingSkins.LOGGER.info("Skin storage ready at {}", dataDir);
    }

    /** Server-stop flush: persist all queued writes before shutdown. */
    public static void onServerStopping() {
        SkinStorage storage = skinStorage;
        if (storage != null) {
            storage.flushPending();
        }
    }

    /** Player UUID from the 1.8.9 Entity surface (dashed form). */
    public static UUID uuidOf(EntityPlayer player) {
        return player.getPersistentID();
    }

    /** Player UUID from the legacy GameProfile surface (authlib). */
    public static UUID profileIdOf(EntityPlayer player) {
        return player.getGameProfile().getId();
    }

    /**
     * Normalizes a raw UUID string for storage-keying: dashed or 32-char
     * no-dash input both map to the canonical dashed UUID.
     *
     * @throws IllegalArgumentException if the input is not a UUID
     */
    public static UUID normalizeUuid(String rawUuid) {
        return UUIDUtils.tryParseUniqueId(rawUuid)
            .orElseThrow(() -> new IllegalArgumentException("Invalid UUID: " + rawUuid));
    }
}
