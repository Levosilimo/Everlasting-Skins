/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.Player;
import levosilimo.everlastingskins.broadcast.SkinBroadcaster;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.NetHandler;
import net.minecraft.src.NetLoginHandler;
import net.minecraft.src.Packet1Login;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Server-side E2E sentinel hook (master plan lib-13): with
 * {@code -Deverlastingskins.e2e=true} on the SERVER, pre-seeds
 * {@code SkinStorage} for the offline test player
 * ({@link SkinRestorer#uuidOf(String)} offline-UUID bridge) before the test
 * client logs in, so the mod's login re-broadcast
 * ({@link levosilimo.everlastingskins.broadcast.SkinBroadcaster}) delivers
 * the sentinel notification to the in-jar client driver.
 *
 * <p>Timing contract: {@link #install()} is invoked from
 * {@code EverlastingSkins.init()} (FMLInitializationEvent); the seed must
 * land AFTER {@code SkinRestorer.onServerStarting} creates storage. The hook
 * therefore seeds lazily from {@code connectionReceived} — the server has
 * finished starting before it accepts any connection, and
 * {@code connectionReceived} precedes the mod's own
 * {@code playerLoggedIn} re-broadcast in the same login flow, so the seed
 * always precedes the broadcast deterministically.
 *
 * <p>The sentinel PNG itself (64x32, known pixels) is read from the server's
 * working directory ({@code e2e-sentinel.png}, copied there by the e2e
 * script from {@code common/src/test/resources/e2e/sentinel-64x32.png}) and
 * its sha1 is logged so the script can tie the server-side seed to the
 * client-side injection bytes. The stored property is storage-only on this
 * line (the 1.4.7 restore path is the broadcast notification); its value is
 * the sentinel PNG bytes base64-encoded.
 */
public final class E2ESentinelHook {

    /** System property the e2e script sets on the server JVM. */
    public static final String E2E_PROPERTY = "everlastingskins.e2e";

    /** Optional override for the sentinel PNG path (default: server dir {@code e2e-sentinel.png}). */
    public static final String SENTINEL_PROPERTY = "everlastingskins.e2e.sentinel";

    public static final String TEST_PLAYER = E2EDriver.TEST_PLAYER;

    /** Re-broadcast burst timing: 250ms period, 40 elements (10s of coverage). */
    public static final long REBROADCAST_PERIOD_MS = 250L;
    public static final int REBROADCAST_BURST_COUNT = 40;

    private static volatile boolean seeded;
    /** Cached seed PNG bytes, fanned out by the re-broadcast burst (no HTTP fetch). */
    private static volatile byte[] seededPng;

    private E2ESentinelHook() {}

    /** Called from {@code EverlastingSkins.init()} (one line; gates live here). */
    public static void install() {
        if (!Boolean.getBoolean(E2E_PROPERTY)) {
            return;
        }
        if (cpw.mods.fml.common.FMLCommonHandler.instance().getSide()
            != cpw.mods.fml.relauncher.Side.SERVER) {
            return;
        }
        NetworkRegistry.instance().registerConnectionHandler(new Seeder());
        FMLLog.info("ES_E2E_SENTINEL=hook installed");
    }

    /** Seeds storage on the first login attempt (idempotent). */
    private static final class Seeder implements IConnectionHandler {

        @Override
        public String connectionReceived(NetLoginHandler netHandler, INetworkManager manager) {
            seedOnce();
            // Every connection (actor, observer, …) schedules a re-broadcast
            // BURST of the CACHED seed bytes. The observer's delivery comes
            // from a burst element landing inside the actor's in-world window
            // (the actor's own connection burst starts 0.25s after it
            // connects; the observer's burst, started earlier, also covers
            // the actor's join). The cached bytes bypass the HTTP
            // SkinTextureFetcher, which cannot reach textures.minecraft.net
            // from the e2e sandbox (its broadcasts would be
            // notification-only).
            scheduleRebroadcast();
            return null;
        }

        @Override
        public void playerLoggedIn(Player player, NetHandler netHandler, INetworkManager manager) {
            if (player instanceof EntityPlayer) {
                FMLLog.info("ES_E2E_LOGIN=%s", ((EntityPlayer) player).getCommandSenderName());
            }
        }

        @Override
        public void connectionOpened(NetHandler netClientHandler, String server, int port, INetworkManager manager) {
        }

        @Override
        public void connectionOpened(NetHandler netClientHandler, MinecraftServer server, INetworkManager manager) {
        }

        @Override
        public void connectionClosed(INetworkManager manager) {
        }

        @Override
        public void clientLoggedIn(NetHandler netClientHandler, INetworkManager manager, Packet1Login login) {
        }
    }

    private static void seedOnce() {
        if (seeded) {
            return;
        }
        seeded = true;
        try {
            MinecraftServer server = cpw.mods.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server == null) {
                FMLLog.warning("ES_E2E_SENTINEL=no server instance, seed deferred");
                seeded = false;
                return;
            }
            byte[] png = readSentinelPng(server);
            if (png == null) {
                FMLLog.severe("ES_E2E_SENTINEL=FAIL png unreadable (set -D%s=<path> or drop e2e-sentinel.png in the server dir)",
                    SENTINEL_PROPERTY);
                return;
            }
            String sha1 = sha1(png);
            String value = Base64.getEncoder().encodeToString(png);
            CustomSkinProperty property = new CustomSkinProperty(
                "textures", value, "", "e2e-sentinel");
            seededPng = png;
            java.util.UUID uuid = SkinRestorer.uuidOf(TEST_PLAYER);
            SkinRestorer.applySkin(uuid, property);
            FMLLog.info("ES_E2E_SENTINEL=seeded player=%s uuid=%s pngSha1=%s bytes=%d",
                TEST_PLAYER, uuid, sha1, png.length);
        } catch (Throwable t) {
            FMLLog.severe("ES_E2E_SENTINEL=FAIL (%s)", t.toString());
        }
    }

    /**
     * Schedules a one-shot re-broadcast BURST of the cached seed bytes
     * (daemon thread; nothing holds the server up). No-op until the seed has
     * landed.
     */
    static void scheduleRebroadcast() {
        if (seededPng == null) {
            return;
        }
        rebroadcastThread(REBROADCAST_PERIOD_MS, REBROADCAST_BURST_COUNT).start();
    }

    /** Thread factory seam (unit-testable without timing): daemon, named, burst loop. */
    static Thread rebroadcastThread(final long periodMs, final int count) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < count; i++) {
                    try {
                        Thread.sleep(periodMs);
                    } catch (InterruptedException e) {
                        return;
                    }
                    rebroadcast();
                }
            }
        }, "ES-E2E-rebroadcast");
        t.setDaemon(true);
        return t;
    }

    /** Fans the cached sentinel bytes out over the real broadcast channel (PNG inline). */
    private static void rebroadcast() {
        byte[] png = seededPng;
        if (png == null) {
            return;
        }
        SkinBroadcaster.broadcastProfileChange(TEST_PLAYER, png);
        FMLLog.info("ES_E2E_SENTINEL=rebroadcast player=%s pngBytes=%d", TEST_PLAYER, png.length);
    }

    private static byte[] readSentinelPng(MinecraftServer server) {
        String override = System.getProperty(SENTINEL_PROPERTY);
        File candidate = override != null ? new File(override) : server.getFile("e2e-sentinel.png");
        if (!candidate.isFile()) {
            return null;
        }
        try {
            return java.nio.file.Files.readAllBytes(candidate.toPath());
        } catch (IOException e) {
            FMLLog.warning("ES_E2E_SENTINEL=read failed (%s)", e.toString());
            return null;
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
