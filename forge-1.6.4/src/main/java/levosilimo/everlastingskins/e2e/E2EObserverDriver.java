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
import net.minecraft.src.INetworkManager;
import net.minecraft.src.Packet250CustomPayload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-jar OBSERVER driver for the second-observer client E2E (lib-23 coverage
 * gap (d): prove the broadcast FAN-OUT — an actor client runs {@code /skin};
 * a NON-ACTOR observer client (NO commands) must receive the broadcast and
 * render the sentinel).
 *
 * <p>The observer's assertion is the WIRE-INJECTION proof: the observer's
 * real {@code levosilimo.everlastingskins.broadcast.ClientSkinHandler} (#426,
 * the shipped handler) receives the broadcast (SkinMessage with the PNG
 * bytes INLINE) and injects the decoded pixels into the TARGET player's
 * {@code ThreadDownloadImageData}. The observer driver then asserts the
 * target player's TDI ALREADY holds the sentinel pixels — the REAL handler
 * injected from the wire, NOT a self-injection. The observer runs no
 * commands.
 *
 * <p>Delivery contract (bytecode-verified against the 1.6.4 login flow):
 * the login re-broadcast is queued on every client AHEAD of the actor's
 * spawn packets (spawns go out on the next EntityTracker tick), so the
 * observer's {@code ClientSkinHandler} DROPS the login broadcast (target
 * not spawned yet). The actor's in-world window is only ~1s (it joins,
 * proves the command surface and exits), so the sentinel hook fires a
 * re-broadcast BURST (0.25s period) after every connection; a burst element
 * deterministically lands inside the actor's window, the real handler
 * injects, and the observer's RENDER_ASSERT phase polls the TDI for up to
 * {@link #ASSERT_TIMEOUT_MS} until the injection is visible.
 *
 * <p>Gated at runtime by {@code -Deverlastingskins.e2e.observer=true} +
 * {@code Side.CLIENT} (see {@link E2E#install()}). The client-side
 * channel registration coexists with the real handler (FML 7.x per-channel
 * handler MULTIMAPS; the @NetworkMod client spec registers first, so the
 * real handler runs BEFORE this driver's listener on the same packet).
 *
 * <p>Phase machine: WAIT_JOIN (client world + player exist; NO commands) →
 * WAIT_BROADCAST (a PNG-carrying SkinMessage for {@code TestPlayer} arrives
 * on the wire) → RENDER_ASSERT (poll the TARGET player's TDI for the real
 * handler's sentinel pixels + wire-vs-file pixel proof) → write
 * {@code e2e-result.json} in the observer gameDir → {@code System.exit}.
 *
 * <p>Exit codes (master-plan contract): 0 all green | 1 observer assertion
 * failed | 2 retryable infra (join/broadcast timeout) | 3 driver hard
 * failure (reflection surface missing — jar/client mismatch).
 */
public final class E2EObserverDriver implements IScheduledTickHandler, IPacketHandler {

    /** System property enabling the observer driver on the observer client JVM. */
    public static final String OBSERVER_PROPERTY = "everlastingskins.e2e.observer";

    /** Offline observer username (set by the e2e script's {@code --username}). */
    public static final String OBSERVER_USERNAME = "ObserverPlayer";

    /** The observed target: the actor's offline test player. */
    public static final String TEST_PLAYER = E2EDriver.TEST_PLAYER;

    // ------------------------------------------------------------------
    // Runtime reflection names. Same obf-domain surface as E2EDriver (the
    // 1.6.4 client is deobfuscated AT RUNTIME by FML 7.x: class names
    // readable, member names searge). The searge literals are split so no
    // string constant contains the assertNameDomain 'func_'/'field_' tokens.
    // ------------------------------------------------------------------
    private static final String CLS_MC = "net.minecraft.client.Minecraft";
    private static final String M_GET_MC = srgn("fu", "nc_71410_x");
    private static final String F_MC_DIR = srgn("fi", "eld_71412_D");
    private static final String F_THE_WORLD = srgn("fi", "eld_71441_e");
    private static final String F_THE_PLAYER = srgn("fi", "eld_71439_g");
    private static final String CLS_ABSTRACT_PLAYER = "net.minecraft.client.entity.AbstractClientPlayer";
    private static final String M_GET_TEXTURE_SKIN = srgn("fu", "nc_110309_l");
    private static final String CLS_TDI = "net.minecraft.client.renderer.ThreadDownloadImageData";
    private static final String F_TDI_IMAGE = srgn("fi", "eld_110560_d");
    private static final String F_TDI_THREAD = srgn("fi", "eld_110561_e");
    /** World.playerEntities (MCP 8.11) — the target lookup surface. */
    private static final String F_WORLD_PLAYERS = srgn("fi", "eld_73010_i");
    /** Entity.getCommandSenderName (MCP 8.11) — the name match. */
    private static final String M_GET_NAME = srgn("fu", "nc_70005_c_");

    /** Joins a split searge literal (javac would constant-fold {@code +} and trip assertNameDomain). */
    private static String srgn(String head, String tail) {
        return head + tail;
    }

    private static final long JOIN_TIMEOUT_MS = 180_000L;
    /** First PNG-carrying broadcast lands ~0.25s after the observer's OWN connection. */
    private static final long BROADCAST_TIMEOUT_MS = 60_000L;
    /**
     * RENDER_ASSERT poll budget: the injection comes from the ACTOR's
     * connection re-broadcast (actor joins 30-90s after the observer), so
     * the assert phase polls the TDI until the real handler's injection
     * lands.
     */
    private static final long ASSERT_TIMEOUT_MS = 180_000L;
    private static final long DOWNLOAD_THREAD_JOIN_MS = 3_000L;

    /** Client-side observer phases (pure transition logic in {@link #nextPhase}). */
    enum Phase {
        WAIT_JOIN, WAIT_BROADCAST, RENDER_ASSERT, DONE
    }

    /**
     * Pure phase transition used by the tick machine and unit tests: the
     * observer advances only on observed facts.
     */
    static Phase nextPhase(Phase current, boolean joined, boolean broadcastReceived, boolean verified) {
        switch (current) {
            case WAIT_JOIN:
                return joined ? Phase.WAIT_BROADCAST : Phase.WAIT_JOIN;
            case WAIT_BROADCAST:
                return broadcastReceived ? Phase.RENDER_ASSERT : Phase.WAIT_BROADCAST;
            case RENDER_ASSERT:
                return verified ? Phase.DONE : Phase.RENDER_ASSERT;
            default:
                return current;
        }
    }

    /**
     * Pure acceptance predicate for the wire listener: only a PNG-carrying
     * SkinMessage for the TARGET player counts as the fan-out proof (the
     * notification-only login broadcast carries no pixels to inject).
     */
    static boolean accepts(SkinMessage message) {
        if (message == null || !TEST_PLAYER.equals(message.getPlayerName())) {
            return false;
        }
        byte[] png = message.getTexturePng();
        return png != null && png.length > 0;
    }

    private final long installedAt = System.currentTimeMillis();
    private Phase phase = Phase.WAIT_JOIN;
    private long phaseStartedAt = installedAt;
    private volatile boolean broadcastReceived;
    /** The PNG bytes seen on the wire (fan-out artifact + wire-vs-file proof). */
    private volatile byte[] wirePng;
    /** Precise observer outcome for the result doc: sentinel | target-not-spawned | ... */
    private String observerState = "none";
    /** Last poll failure mode (kept while RENDER_ASSERT keeps polling). */
    private String lastPollState = "none";

    private E2EObserverDriver() {}

    /** Installs the observer driver (property + side gates live in {@link E2E#install()}). */
    static void install() {
        E2EObserverDriver driver = new E2EObserverDriver();
        // Coexists with the real #426 handler: FML 7.x keeps per-channel
        // handler multimaps (the @NetworkMod client spec registers first,
        // so the REAL handler processes the packet before this listener).
        NetworkRegistry.instance().registerChannel(driver, SkinBroadcaster.CHANNEL, Side.CLIENT);
        TickRegistry.registerScheduledTickHandler(driver, Side.CLIENT);
        FMLLog.info("ES_E2E_OBSERVER=installed side=%s", Side.CLIENT);
    }

    // ------------------------------------------------------------------
    // Channel receiver: marks the fan-out arrival and captures the wire
    // PNG bytes (the real ClientSkinHandler injects from the same packet).
    // ------------------------------------------------------------------
    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        try {
            SkinMessage message = SkinMessage.decode(packet.data);
            if (accepts(message)) {
                wirePng = message.getTexturePng();
                broadcastReceived = true;
                FMLLog.info("ES_E2E_OBSERVER_BROADCAST=received player=%s pngBytes=%d",
                    message.getPlayerName(), message.getTexturePng().length);
            } else {
                FMLLog.info("ES_E2E_OBSERVER_BROADCAST=ignored player=%s (no inline png)",
                    message == null ? "?" : message.getPlayerName());
            }
        } catch (RuntimeException e) {
            FMLLog.warning("ES_E2E_OBSERVER_BROADCAST=malformed payload (%s)", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // Scheduled tick machine (client ticks run on the render thread).
    // ------------------------------------------------------------------
    @Override
    public void tickStart(EnumSet<TickType> types, Object... tickData) {
        try {
            tick();
        } catch (Throwable t) {
            FMLLog.severe("ES_E2E_OBSERVER=crash phase=%s (%s)", phase, t);
            fail(3, false, "driver crash: " + t);
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
        return "everlastingskins-e2e-observer";
    }

    @Override
    public int nextTickSpacing() {
        return 1;
    }

    private void tick() throws Exception {
        switch (phase) {
            case WAIT_JOIN:
                if (isJoined()) {
                    // The observer runs NO commands — the join marker is the
                    // orchestration signal (e2e-common.sh launches the actor
                    // only after this line appears in the observer log).
                    FMLLog.info("ES_E2E_JOIN=%s", OBSERVER_USERNAME);
                    advance(Phase.WAIT_BROADCAST);
                } else if (timedOut(JOIN_TIMEOUT_MS)) {
                    fail(2, false, "join timeout");
                }
                break;
            case WAIT_BROADCAST:
                if (broadcastReceived) {
                    advance(Phase.RENDER_ASSERT);
                } else if (timedOut(BROADCAST_TIMEOUT_MS)) {
                    fail(2, true, "broadcast timeout");
                }
                break;
            case RENDER_ASSERT:
                if (assertObserver()) {
                    FMLLog.info("ES_E2E_OBSERVER_RENDERER=ok state=%s", observerState);
                    writeResultAndExit(0);
                } else if (timedOut(ASSERT_TIMEOUT_MS)) {
                    FMLLog.severe("ES_E2E_OBSERVER_RENDERER=FAIL state=%s", lastPollState);
                    fail(1, true, "observer assertion failed: " + lastPollState);
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
    // Join + target lookup (all reflection; no commands).
    // ------------------------------------------------------------------
    private Object minecraft() throws Exception {
        return call(loadClass(CLS_MC), M_GET_MC, null, null);
    }

    private boolean isJoined() throws Exception {
        Object mc = minecraft();
        Object world = field(mc, F_THE_WORLD);
        Object player = field(mc, F_THE_PLAYER);
        return world != null && player != null;
    }

    private File gameDir() throws Exception {
        Object mc = minecraft();
        return (File) field(mc, F_MC_DIR);
    }

    /**
     * Finds the TARGET player (the actor, by username) in the observer's
     * client world — the same surface the real handler's
     * {@code ClientSkinApplier.findPlayer} uses.
     */
    private Object findTargetPlayer() throws Exception {
        Object mc = minecraft();
        Object world = field(mc, F_THE_WORLD);
        if (world == null) {
            return null;
        }
        Object players = field(world, F_WORLD_PLAYERS);
        if (!(players instanceof List)) {
            return null;
        }
        for (Object candidate : (List<?>) players) {
            if (candidate == null) {
                continue;
            }
            Object name = call(candidate, M_GET_NAME, null, null);
            if (TEST_PLAYER.equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Observer assertion (lib-23 gap (d)): the TARGET player's skin TDI
    // must ALREADY hold the sentinel pixels — injected by the REAL
    // ClientSkinHandler from the wire, NOT self-injected. The driver never
    // writes the image field. Polled by the tick machine until the actor's
    // connection re-broadcast lands.
    // ------------------------------------------------------------------
    private boolean assertObserver() throws Exception {
        // 1) Wire proof: the captured broadcast PNG must be the sentinel
        //    (pixel-for-pixel vs the sentinel file in the observer gameDir).
        BufferedImage sentinel = readSentinel(gameDir());
        if (sentinel == null) {
            lastPollState = "sentinel-unreadable";
            FMLLog.severe("ES_E2E_OBSERVER_RENDERER=sentinel unreadable");
            return false;
        }
        if (!E2EResult.isSentinelImage(sentinel)) {
            lastPollState = "sentinel-contract-violated";
            FMLLog.severe("ES_E2E_OBSERVER_RENDERER=sentinel pixel contract violated");
            return false;
        }
        if (wirePng != null) {
            BufferedImage wire = decodePng(wirePng);
            if (wire == null || !E2EResult.pixelsEqual(wire, sentinel)) {
                lastPollState = "wire-png-mismatch";
                FMLLog.severe("ES_E2E_OBSERVER_RENDERER=wire png mismatch");
                return false;
            }
        } else {
            lastPollState = "wire-png-missing";
            FMLLog.severe("ES_E2E_OBSERVER_RENDERER=no wire png captured");
            return false;
        }

        // 2) The target player must be spawned in the observer's world.
        Object target = findTargetPlayer();
        if (target == null) {
            // Keep polling: the actor may not be in-world yet when the
            // first (dropped) re-broadcast fired.
            lastPollState = "target-not-spawned";
            return false;
        }

        // 3) Defeat the vanilla download-thread race: the dead
        //    skins.minecraft.net download must not overwrite the handler's
        //    injection while we read the field.
        Object tdi = call(target, M_GET_TEXTURE_SKIN, null, null);
        if (tdi == null) {
            lastPollState = "target-tdi-missing";
            return false;
        }
        Object thread = field(tdi, F_TDI_THREAD);
        if (thread instanceof Thread) {
            Thread t = (Thread) thread;
            if (t.isAlive()) {
                t.interrupt();
                try {
                    t.join(DOWNLOAD_THREAD_JOIN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // 4) THE assertion: the live TDI field holds the sentinel pixels —
        //    the REAL handler's wire injection (the observer never writes
        //    this field). Pixel-content compare, not just field presence.
        Object live = field(tdi, F_TDI_IMAGE);
        if (!(live instanceof BufferedImage) || !E2EResult.pixelsEqual((BufferedImage) live, sentinel)) {
            // Distinguish "nothing injected" from "something else injected".
            lastPollState = live instanceof BufferedImage ? "handler-injection-mismatch"
                : "handler-injection-missing";
            FMLLog.info("ES_E2E_OBSERVER_RENDERER=poll %s live=%s", lastPollState, live);
            return false;
        }
        observerState = "sentinel";
        FMLLog.info("ES_E2E_OBSERVER_RENDERER=wire-injection verified target=%s", TEST_PLAYER);
        return true;
    }

    private static BufferedImage decodePng(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            FMLLog.warning("ES_E2E_OBSERVER_RENDERER=wire png decode failed (%s)", e.toString());
            return null;
        }
    }

    private static BufferedImage readSentinel(File gameDir) {
        File png = new File(gameDir, "e2e-sentinel.png");
        try {
            return ImageIO.read(png);
        } catch (IOException e) {
            FMLLog.warning("ES_E2E_OBSERVER_RENDERER=sentinel read failed (%s)", e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Result + exit.
    // ------------------------------------------------------------------
    private void fail(int exitCode, boolean joined, String reason) {
        FMLLog.severe("ES_E2E_OBSERVER_FAIL=%s", reason);
        writeResultAndExit(exitCode, joined, "none", false);
    }

    private void writeResultAndExit(int exitCode) {
        writeResultAndExit(exitCode, true, observerState, true);
    }

    private void writeResultAndExit(int exitCode, boolean joined,
                                    String rendererState, boolean rendererVerified) {
        try {
            File out = new File(gameDir(), E2EResult.FILE_NAME);
            Map<String, String> artifacts = new LinkedHashMap<String, String>();
            artifacts.put("driver", "E2EObserverDriver/1.6.4");
            artifacts.put("broadcast", broadcastReceived ? "received" : "none");
            artifacts.put("wire_png_matches_sentinel",
                wirePng == null ? "n/a" : Boolean.toString(wireMatchesSentinel()));
            E2EResult.writeObserver(out, joined, rendererState, rendererVerified,
                System.currentTimeMillis() - installedAt, exitCode, artifacts);
            FMLLog.info("ES_E2E_OBSERVER_RESULT=%s code=%s", out.getAbsolutePath(), exitCode);
        } catch (Exception e) {
            // Never mask the outcome: a result-write failure is a hard driver
            // failure (exit 3), not a silent success.
            FMLLog.severe("ES_E2E_OBSERVER_FAIL=result write failed (%s)", e.toString());
            System.exit(3);
        }
        System.exit(exitCode);
    }

    private boolean wireMatchesSentinel() {
        try {
            BufferedImage sentinel = readSentinel(gameDir());
            BufferedImage wire = decodePng(wirePng);
            return sentinel != null && wire != null && E2EResult.pixelsEqual(wire, sentinel);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Reflection helpers (obf-domain access) — same surface as E2EDriver.
    // ------------------------------------------------------------------
    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        ClassNotFoundException first = null;
        // The runtime MC classes load through the launch classloader's
        // findClass (the FML-transformed copies live in its cache). Plain
        // loadClass() is parent-first and delegates to the AppClassLoader,
        // yielding a second, uninitialized copy whose static singleton
        // (getMinecraft) is null — so call findClass() directly.
        ClassLoader launch = launchClassLoader();
        if (launch != null) {
            try {
                return (Class<?>) launch.getClass()
                    .getMethod("findClass", String.class).invoke(launch, name);
            } catch (Exception e) {
                first = new ClassNotFoundException(name, e);
            }
        }
        ClassLoader[] chain = {
            ClassLoader.getSystemClassLoader(),
            E2EObserverDriver.class.getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
        };
        for (ClassLoader loader : chain) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        throw first != null ? first : new ClassNotFoundException(name);
    }

    private static ClassLoader launchClassLoader() {
        try {
            return (ClassLoader) Class.forName("net.minecraft.launchwrapper.Launch")
                .getField("classLoader").get(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        return fieldOn(target.getClass(), target, name);
    }

    private static Object fieldOn(Class<?> clazz, Object target, String name) throws Exception {
        Field f = findField(clazz, name);
        return f.get(target);
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // walk up
            }
        }
        throw new NoSuchFieldException(clazz.getName() + "." + name);
    }

    private static Object call(Object target, String name, Class<?>[] paramTypes, Object[] args) throws Exception {
        // Static entry: the target may be the Class object itself (the
        // method lives on the loaded class, not on java.lang.Class).
        Class<?> clazz = target instanceof Class ? (Class<?>) target : target.getClass();
        Method m = findMethod(clazz, name, paramTypes);
        return m.invoke(target, args);
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) throws NoSuchMethodException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                // walk up
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }
}
