/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import levosilimo.everlastingskins.broadcast.SkinBroadcaster;
import levosilimo.everlastingskins.broadcast.SkinMessage;
import levosilimo.everlastingskins.client.ClientSkinApplier;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityClientPlayerMP;
import net.minecraft.src.ImageBufferDownload;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.Packet250CustomPayload;
import net.minecraft.src.ThreadDownloadImageData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-jar client driver for the real-client E2E (master plan slice 1,
 * {@code scripts/e2e/drivers/pre18-xvfb.sh}, MERGE model — lib-8 recipe).
 *
 * <p>Shipped in the mod jar, gated at runtime by
 * {@code -Deverlastingskins.e2e=true} + {@code Side.CLIENT} (see
 * {@link E2E#install()}). Lifecycle: {@code @Mod.Init} installs this driver;
 * the tick primitive is FML 4.7 {@link IScheduledTickHandler}
 * ({@code TickRegistry.registerScheduledTickHandler}).
 *
 * <p>MERGE-MODEL delta vs the 1.6.4 driver: this lane boots as the obf client
 * jar with the FML universal zip MERGED in (main
 * {@code net.minecraft.client.Minecraft}, no launchwrapper), so the runtime
 * class names and the compile-time deobf names agree modulo the lane's
 * reobf pass — the driver compiles DIRECTLY against the client classes
 * ({@code Minecraft.getMinecraft()}, {@code mc.theWorld/thePlayer},
 * {@code mc.renderEngine.obtainImageData}, the PUBLIC
 * {@code ThreadDownloadImageData.image/textureName/textureSetupComplete}
 * fields, {@code Entity.skinUrl} — MCP 7.26a) with zero reflection, unlike the
 * 1.6.4 driver. The connect: 1.4.7's {@code Minecraft.main} parses no
 * {@code --server/--port} (only {@code --demo/--applet/--password} — verified
 * against the client jar), so {@link #install()} calls
 * {@code Minecraft.setServer(host, port)} itself; the vanilla run loop picks
 * the fields up at startGame and shows GuiConnecting. The e2e scripts pin
 * the client jar sha1, which keeps every referenced member name stable.
 *
 * <p>Phase machine: WAIT_JOIN (client world + player exist) → send
 * {@code /skin set TestPlayer} + {@code /skin clear} (command surface proof;
 * the server console logs both chat lines) → WAIT_BROADCAST (the login
 * re-broadcast of the pre-seeded sentinel arrives on the
 * {@code everlastingskins} channel — this receiver coexists with the mod's
 * own {@link ClientSkinApplier} handler: FML 4.7 keeps per-channel
 * handler multimaps) → RENDER_ASSERT: observe the broadcast injection into
 * the player's skin {@link ThreadDownloadImageData} (the login broadcast
 * carries the sentinel PNG bytes inline; the client handler already injected
 * them), otherwise inject the sentinel from the gameDir file via
 * {@link ClientSkinApplier#apply}; re-arm the once-per-image upload guard
 * ({@code textureSetupComplete=false}) and re-trigger the upload through
 * {@code RenderEngine.getTextureForDownloadableImage}; then assert the
 * injected field state + the re-upload outcome (GL id + guard) + the sentinel
 * pixel contract → write {@code e2e-result.json} in the client gameDir →
 * {@code System.exit}.
 *
 * <p>Download-thread race (documented, same caveat as the client handler):
 * the vanilla {@code ThreadDownloadImage} download from skins.minecraft.net
 * writes {@code tdi.image} ONLY on a successful fetch (the failure path just
 * prints + disconnects — verified against the client jar). The e2e sandbox
 * cannot reach skins.minecraft.net, so the injected sentinel survives; a
 * reachable host would overwrite it and the field-state assert fails
 * honestly (exit 1).
 *
 * <p>Exit codes (master-plan contract): 0 all green | 1 renderer assertion
 * failed | 2 retryable infra (join/broadcast timeout) | 3 driver hard
 * failure.
 */
public final class E2EDriver implements IScheduledTickHandler, IPacketHandler {

    /** Offline test player (matches the e2e scripts' username arg). */
    public static final String TEST_PLAYER = "TestPlayer";

    /** System property the e2e driver script passes (merge boot has no --server/--port). */
    static final String SERVER_PROPERTY = "everlastingskins.e2e.server";

    /** System property for the test server port. */
    static final String PORT_PROPERTY = "everlastingskins.e2e.port";

    private static final long JOIN_TIMEOUT_MS = 180_000L;
    private static final long BROADCAST_TIMEOUT_MS = 60_000L;

    /** Client-side driver phases (pure transition logic in {@link #nextPhase}). */
    enum Phase {
        WAIT_JOIN, WAIT_BROADCAST, RENDER_ASSERT, DONE
    }

    /**
     * Pure phase transition used by the tick machine and unit tests: the
     * driver advances only on observed facts.
     */
    static Phase nextPhase(Phase current, boolean joined, boolean broadcastReceived, boolean asserted) {
        switch (current) {
            case WAIT_JOIN:
                return joined ? Phase.WAIT_BROADCAST : Phase.WAIT_JOIN;
            case WAIT_BROADCAST:
                return broadcastReceived ? Phase.RENDER_ASSERT : Phase.WAIT_BROADCAST;
            case RENDER_ASSERT:
                return asserted ? Phase.DONE : Phase.RENDER_ASSERT;
            default:
                return current;
        }
    }

    private final long installedAt = System.currentTimeMillis();
    private Phase phase = Phase.WAIT_JOIN;
    private long phaseStartedAt = installedAt;
    private volatile boolean broadcastReceived;
    private boolean rendererVerified;
    private boolean rendererState;
    private boolean broadcastInjected;

    private E2EDriver() {}

    /** Installs the client driver (property + side gates live in {@link E2E#install()}). */
    static void install() {
        E2EDriver driver = new E2EDriver();
        // Client-side channel receiver: FML 4.7 keeps per-channel handler
        // multimaps, so this coexists with the @NetworkMod client spec.
        NetworkRegistry.instance().registerChannel(driver, SkinBroadcaster.CHANNEL, Side.CLIENT);
        TickRegistry.registerScheduledTickHandler(driver, Side.CLIENT);
        // Merge-model auto-connect. Runs inside the Minecraft constructor
        // (@Mod.Init fires via finishMinecraftLoading), before startGame
        // consumes serverName → GuiConnecting → join.
        Minecraft mc = Minecraft.getMinecraft();
        String host = System.getProperty(SERVER_PROPERTY, "127.0.0.1");
        int port = Integer.getInteger(PORT_PROPERTY, 25565);
        mc.setServer(host, port);
        FMLLog.info("ES_E2E_DRIVER=installed side=%s server=%s:%d", Side.CLIENT, host, port);
    }

    // ------------------------------------------------------------------
    // Channel receiver: the server's login re-broadcast (sentinel pre-seed)
    // is a notification-only SkinMessage for TEST_PLAYER.
    // ------------------------------------------------------------------
    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        try {
            SkinMessage message = SkinMessage.decode(packet.data);
            if (TEST_PLAYER.equals(message.getPlayerName())) {
                broadcastReceived = true;
                FMLLog.info("ES_E2E_BROADCAST=received player=%s", message.getPlayerName());
            }
        } catch (RuntimeException e) {
            FMLLog.warning("ES_E2E_BROADCAST=malformed payload (%s)", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // Scheduled tick machine (client ticks run on the render thread, so GL
    // work in RENDER_ASSERT is on the same thread as the renderer).
    // ------------------------------------------------------------------
    @Override
    public void tickStart(EnumSet<TickType> types, Object... tickData) {
        try {
            tick();
        } catch (Throwable t) {
            FMLLog.severe("ES_E2E_DRIVER=crash phase=%s (%s)", phase, t);
            fail(3, false, false, "driver crash: " + t);
        }
    }

    @Override
    public void tickEnd(EnumSet<TickType> types, Object... tickData) {
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.CLIENT);
    }

    @Override
    public String getLabel() {
        return "everlastingskins-e2e";
    }

    @Override
    public int nextTickSpacing() {
        return 1;
    }

    private void tick() throws Exception {
        switch (phase) {
            case WAIT_JOIN:
                if (isJoined()) {
                    FMLLog.info("ES_E2E_JOIN=%s", TEST_PLAYER);
                    sendChat("/skin set " + TEST_PLAYER);
                    sendChat("/skin clear");
                    FMLLog.info("ES_E2E_COMMAND=/skin set " + TEST_PLAYER);
                    FMLLog.info("ES_E2E_COMMAND=/skin clear");
                    advance(Phase.WAIT_BROADCAST);
                } else if (timedOut(JOIN_TIMEOUT_MS)) {
                    fail(2, false, false, "join timeout");
                }
                break;
            case WAIT_BROADCAST:
                if (broadcastReceived) {
                    advance(Phase.RENDER_ASSERT);
                } else if (timedOut(BROADCAST_TIMEOUT_MS)) {
                    fail(2, true, true, "broadcast timeout");
                }
                break;
            case RENDER_ASSERT:
                rendererState = rendererVerified = assertRenderer();
                if (rendererVerified) {
                    FMLLog.info("ES_E2E_RENDERER=ok");
                    writeResultAndExit(0);
                } else {
                    FMLLog.severe("ES_E2E_RENDERER=FAIL");
                    fail(1, true, true, "renderer assertion failed");
                }
                break;
            default:
                break;
        }
    }

    private void advance(Phase next) {
        phase = next;
        phaseStartedAt = System.currentTimeMillis();
    }

    private boolean timedOut(long budgetMs) {
        return System.currentTimeMillis() - phaseStartedAt > budgetMs;
    }

    // ------------------------------------------------------------------
    // Join + command surface (direct client references — MCP 7.26a surface).
    // ------------------------------------------------------------------
    private boolean isJoined() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null && mc.thePlayer != null;
    }

    private void sendChat(String message) {
        Minecraft.getMinecraft().thePlayer.sendChatMessage(message);
    }

    // ------------------------------------------------------------------
    // Renderer assertion (lib-14, 1.4.7/1.4.7 variant): field state +
    // re-upload trigger + sentinel pixel compare. The 1.4.7
    // ThreadDownloadImageData is a POJO with public image/textureName/
    // textureSetupComplete fields; RenderEngine.getTextureForDownloadableImage
    // re-uploads when image != null && !textureSetupComplete and returns the
    // GL texture id (verified against the client jar).
    // ------------------------------------------------------------------
    private boolean assertRenderer() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        if (player == null) {
            FMLLog.severe("ES_E2E_RENDERER=no player entity");
            return false;
        }
        // Field declared on Entity; access it through the Entity type so the
        // reobf pass can map the reference (javac emits the receiver's
        // declared type as the owner, and the srg FD keys are the declaring
        // class — EntityClientPlayerMP.skinUrl would stay unmapped and
        // NoSuchFieldError at runtime).
        net.minecraft.src.Entity entity = player;
        String skinUrl = entity.skinUrl;
        if (skinUrl == null) {
            FMLLog.severe("ES_E2E_RENDERER=skinUrl not set");
            return false;
        }
        // Cached TDI: seeded by RenderGlobal.onEntityCreate when the player
        // spawned; the client handler injects through the same instance.
        ThreadDownloadImageData tdi = mc.renderEngine.obtainImageData(skinUrl, new ImageBufferDownload());
        if (tdi == null) {
            FMLLog.severe("ES_E2E_RENDERER=no cached ThreadDownloadImageData");
            return false;
        }

        // Sentinel image from the client gameDir (script-copied from :common
        // test resources; same bytes the server seeded from).
        BufferedImage sentinel = readSentinel(mc.getMinecraftDir());
        if (sentinel == null || !E2EResult.isSentinelImage(sentinel)) {
            FMLLog.severe("ES_E2E_RENDERER=sentinel unreadable or pixel contract violated");
            return false;
        }
        BufferedImage flat = ClientSkinApplier.flattenToLegacy(sentinel);

        // Observe the broadcast injection first: the login re-broadcast
        // carries the sentinel PNG inline and the client handler injects it
        // into this same cached TDI. If it already landed, the full
        // server→client pipeline is proven; either way, re-arm the
        // once-per-image upload guard and re-trigger the upload.
        broadcastInjected = tdi.image != null && E2EResult.isSentinelImage(tdi.image);
        if (broadcastInjected) {
            FMLLog.info("ES_E2E_RENDERER=broadcast injection observed");
            tdi.textureSetupComplete = false;
        } else {
            ClientSkinApplier.apply(tdi, flat);
        }

        // 1) Field-state assert (pixel compare vs the sentinel contract).
        if (tdi.image == null || !E2EResult.isSentinelImage(tdi.image)) {
            FMLLog.severe("ES_E2E_RENDERER=field state mismatch (image=%s)", tdi.image);
            return false;
        }

        // 2) Re-upload trigger: textureSetupComplete=false makes
        // getTextureForDownloadableImage upload the injected pixels
        // (allocateAndSetupTexture on first upload, setupTexture re-upload
        // into the existing GL texture) and return the GL id.
        int glId = mc.renderEngine.getTextureForDownloadableImage(skinUrl, null);
        boolean uploadOk = glId >= 0 && tdi.textureName == glId && tdi.textureSetupComplete;
        if (!uploadOk) {
            FMLLog.severe("ES_E2E_RENDERER=re-upload failed glId=%s textureName=%s setupComplete=%s",
                glId, tdi.textureName, tdi.textureSetupComplete);
            return false;
        }
        FMLLog.info("ES_E2E_RENDERER=ok image=%s glTextureId=%s broadcastInjected=%s",
            tdi.image, glId, broadcastInjected);
        return true;
    }

    private static BufferedImage readSentinel(File gameDir) {
        File png = new File(gameDir, "e2e-sentinel.png");
        try {
            return ImageIO.read(png);
        } catch (IOException e) {
            FMLLog.warning("ES_E2E_RENDERER=sentinel read failed (%s)", e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Result + exit.
    // ------------------------------------------------------------------
    private void fail(int exitCode, boolean joined, boolean commandSent, String reason) {
        FMLLog.severe("ES_E2E_FAIL=%s", reason);
        writeResultAndExit(exitCode, joined, commandSent, false, false);
    }

    private void writeResultAndExit(int exitCode) {
        writeResultAndExit(exitCode, true, true, rendererState, rendererVerified);
    }

    private void writeResultAndExit(int exitCode, boolean joined, boolean commandSent,
                                    boolean rendererState, boolean rendererVerified) {
        try {
            File out = new File(Minecraft.getMinecraft().getMinecraftDir(), E2EResult.FILE_NAME);
            Map<String, String> artifacts = new LinkedHashMap<String, String>();
            artifacts.put("driver", "E2EDriver/1.4.7");
            artifacts.put("broadcast", broadcastReceived ? "received" : "none");
            artifacts.put("broadcast_injected", broadcastInjected ? "true" : "false");
            E2EResult.write(out, joined, commandSent, rendererState, rendererVerified,
                System.currentTimeMillis() - installedAt, exitCode, artifacts);
            FMLLog.info("ES_E2E_RESULT=%s code=%s", out.getAbsolutePath(), exitCode);
        } catch (Exception e) {
            // Never mask the outcome: a result-write failure is a hard driver
            // failure (exit 3), not a silent success.
            FMLLog.severe("ES_E2E_FAIL=result write failed (%s)", e.toString());
            System.exit(3);
        }
        System.exit(exitCode);
    }
}
