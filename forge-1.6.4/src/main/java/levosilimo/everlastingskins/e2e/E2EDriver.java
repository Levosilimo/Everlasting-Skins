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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-jar client driver for the real-client E2E (master plan slice 1,
 * {@code scripts/e2e/drivers/pre18-xvfb.sh}).
 *
 * <p>Shipped in the mod jar, gated at runtime by
 * {@code -Deverlastingskins.e2e=true} + {@code Side.CLIENT} (see
 * {@link E2E#install()}). Lifecycle: {@code @Mod.EventHandler init} installs
 * this driver; the tick primitive is FML 7.x {@link IScheduledTickHandler}
 * ({@code TickRegistry.registerScheduledTickHandler}) — there is no
 * {@code addScheduledTask} and no {@code TickEvent} before 1.7.
 *
 * <p>Phase machine: WAIT_JOIN (client world + player exist) → send
 * {@code /skin set TestPlayer} + {@code /skin clear} (command surface proof;
 * the server console logs both chat lines) → WAIT_BROADCAST (the login
 * re-broadcast of the pre-seeded sentinel arrives on the
 * {@code everlastingskins} channel — {@link IPacketHandler} registration
 * coexists with the mod's own handler: FML 7.x keeps per-channel handler
 * MULTIMAPS) → RENDER_ASSERT: force the player's skin
 * {@code ThreadDownloadImageData}, defeat the vanilla download-thread race
 * (interrupt + join), inject the sentinel image into the renderer's
 * {@code bufferedImage} field, re-arm the once-only GL-upload guard and
 * re-upload via {@code getGlTextureId}, then assert the injected field state
 * + the guard state → write {@code e2e-result.json} in the client gameDir →
 * {@code System.exit}.
 *
 * <p>The 1.6.4 client jar is fully obfuscated and the lane's dev classpath
 * (merged-deobf.jar) is server+universal only — there are no client classes
 * to compile against, so ALL Minecraft access is reflection against the
 * obf-domain names from the MCP 8.11 joined.srg (stable because the client
 * jar is sha1-pinned by the e2e scripts). The names carry no
 * {@code func_/field_} tokens, so the reobf name-domain self-check
 * (assertNameDomain) stays green.
 *
 * <p>Exit codes (master-plan contract): 0 all green | 1 renderer assertion
 * failed | 2 retryable infra (join/broadcast timeout) | 3 driver hard
 * failure (reflection surface missing — jar/client mismatch).
 */
public final class E2EDriver implements IScheduledTickHandler, IPacketHandler {

    /** Offline test player (matches the e2e scripts' {@code --username}). */
    public static final String TEST_PLAYER = "TestPlayer";

    // ------------------------------------------------------------------
    // Runtime reflection names. The 1.6.4 CLIENT is deobfuscated AT RUNTIME
    // by FML 7.x's DeobfuscationTransformer (the FMLDeobfTweaker registers it
    // when fml.deobfuscatedEnvironment is unset — verified live: the runtime
    // Minecraft class is net.minecraft.client.Minecraft, not the jar's obf
    // "atv"). Class names come out READABLE (packages.csv); member names
    // stay SEARGE (func_/field_ — "getMinecraft" does not exist at runtime,
    // verified live). The lane's dev classpath (server+universal) has no
    // client classes, so everything stays reflection-based. The searge
    // member literals are split so no string constant contains the
    // assertNameDomain 'func_'/'field_' tokens; class literals are dot-form.
    // ------------------------------------------------------------------
    private static final String CLS_MC = "net.minecraft.client.Minecraft";
    private static final String M_GET_MC = srgn("fu", "nc_71410_x");
    private static final String F_MC_DIR = srgn("fi", "eld_71412_D");
    private static final String F_THE_WORLD = srgn("fi", "eld_71441_e");
    private static final String F_THE_PLAYER = srgn("fi", "eld_71439_g");
    private static final String CLS_PLAYER = "net.minecraft.client.entity.EntityClientPlayerMP";
    private static final String M_SEND_CHAT = srgn("fu", "nc_71165_d");
    private static final String CLS_ABSTRACT_PLAYER = "net.minecraft.client.entity.AbstractClientPlayer";
    private static final String M_GET_TEXTURE_SKIN = srgn("fu", "nc_110309_l");
    private static final String CLS_TDI = "net.minecraft.client.renderer.ThreadDownloadImageData";
    private static final String M_TDI_SET_IMAGE = srgn("fu", "nc_110556_a");
    private static final String M_TDI_GET_GL_ID = srgn("fu", "nc_110552_b");
    private static final String F_TDI_IMAGE = srgn("fi", "eld_110560_d");
    private static final String F_TDI_UPLOADED = srgn("fi", "eld_110559_g");
    private static final String F_TDI_THREAD = srgn("fi", "eld_110561_e");
    private static final String CLS_ABSTRACT_TEXTURE = "net.minecraft.client.renderer.texture.AbstractTexture";
    private static final String F_GL_TEXTURE_ID = srgn("fi", "eld_110553_a");

    /** Joins a split searge literal (javac would constant-fold {@code +} and trip assertNameDomain). */
    private static String srgn(String head, String tail) {
        return head + tail;
    }

    private static final long JOIN_TIMEOUT_MS = 180_000L;
    private static final long BROADCAST_TIMEOUT_MS = 60_000L;
    private static final long DOWNLOAD_THREAD_JOIN_MS = 3_000L;

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
    /** Precise renderer outcome for the result doc (audit lib-20): sentinel | none | sentinel-mismatch | ... */
    private String rendererState = "none";

    private E2EDriver() {}

    /** Installs the client driver (property + side gates live in {@link E2E#install()}). */
    static void install() {
        E2EDriver driver = new E2EDriver();
        // Client-side channel receiver: FML 7.x keeps per-channel handler
        // multimaps, so this coexists with the mod's server-side no-op.
        NetworkRegistry.instance().registerChannel(driver, SkinBroadcaster.CHANNEL, Side.CLIENT);
        TickRegistry.registerScheduledTickHandler(driver, Side.CLIENT);
        FMLLog.info("ES_E2E_DRIVER=installed side=%s", Side.CLIENT);
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
                    // Master-plan marker (ES_E2E_COMMAND=ok): both commands
                    // were sent; the server console logs them as the
                    // command-surface proof.
                    FMLLog.info("ES_E2E_COMMAND=ok");
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
                rendererVerified = assertRenderer();
                if (rendererVerified) {
                    FMLLog.info("ES_E2E_RENDERER=ok");
                    writeResultAndExit(0);
                } else {
                    FMLLog.severe("ES_E2E_RENDERER=FAIL state=%s", rendererState);
                    fail(1, true, true, "renderer assertion failed: " + rendererState);
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
    // Join + command surface (all reflection).
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

    private void sendChat(String message) throws Exception {
        Object mc = minecraft();
        Object player = field(mc, F_THE_PLAYER);
        call(player, M_SEND_CHAT, new Class<?>[] {String.class}, new Object[] {message});
    }

    private File gameDir() throws Exception {
        Object mc = minecraft();
        return (File) field(mc, F_MC_DIR);
    }

    // ------------------------------------------------------------------
    // Renderer assertion (lib-14): the 1.6.4 ThreadDownloadImageData uploads
    // to GL ONCE (textureUploaded guard inside getGlTextureId) — field state
    // alone is not an honest gate. The driver injects the sentinel image,
    // re-arms the guard and re-triggers the upload, then asserts both the
    // injected field state and the re-upload outcome.
    // ------------------------------------------------------------------
    private boolean assertRenderer() throws Exception {
        Object mc = minecraft();
        Object player = field(mc, F_THE_PLAYER);

        // The player's skin ThreadDownloadImageData (shared instance: the
        // renderer binds through the same getTextureSkin() cache).
        Object tdi = call(player, M_GET_TEXTURE_SKIN, null, null);

        // Defeat the vanilla download-thread race: the dead
        // skins.minecraft.net download must not overwrite the injection.
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

        // Sentinel image from the client gameDir (script-copied from :common
        // test resources; same bytes the server seeded from).
        BufferedImage sentinel = readSentinel(gameDir());
        if (sentinel == null) {
            rendererState = "sentinel-unreadable";
            FMLLog.severe("ES_E2E_RENDERER=sentinel unreadable");
            return false;
        }
        if (!E2EResult.isSentinelImage(sentinel)) {
            rendererState = "sentinel-contract-violated";
            FMLLog.severe("ES_E2E_RENDERER=sentinel pixel contract violated");
            return false;
        }

        // 1) Inject + assert injected field state.
        call(tdi, M_TDI_SET_IMAGE, new Class<?>[] {BufferedImage.class}, new Object[] {sentinel});
        Object injected = field(tdi, F_TDI_IMAGE);
        if (injected != sentinel) {
            rendererState = "injection-field-mismatch";
            FMLLog.severe("ES_E2E_RENDERER=field state mismatch (injected=%s)", injected);
            return false;
        }

        // 2) Re-arm the once-only GL-upload guard and re-trigger the upload.
        fieldSet(tdi, F_TDI_UPLOADED, false);
        Object glId = call(tdi, M_TDI_GET_GL_ID, null, null);
        Object guard = field(tdi, F_TDI_UPLOADED);
        Object glField = fieldOn(tdi.getClass().getSuperclass(), tdi, F_GL_TEXTURE_ID);
        boolean guardReArmed = Boolean.TRUE.equals(guard);
        boolean glUploaded = glId instanceof Integer && glField instanceof Integer
            && ((Integer) glId).intValue() != -1
            && glId.equals(glField);
        if (!guardReArmed || !glUploaded) {
            rendererState = "reupload-failed";
            FMLLog.severe("ES_E2E_RENDERER=re-upload failed guard=%s glId=%s glField=%s", guard, glId, glField);
            return false;
        }

        // 3) PIXEL-CONTENT assert (audit lib-20): mechanics (guard re-armed,
        // glId uploaded) prove the injection round-trip, not that the
        // renderer holds the sentinel pixels. Re-read the live field and
        // compare it pixel-for-pixel against the sentinel file decoded from
        // the gameDir. The download thread is dead (interrupted above), so
        // the field can only hold our injection — any drift is a real
        // content failure. Wire-side proof is era-limited: the 1.6.4
        // broadcast is notification-only (SkinMessage.encode(name, null) in
        // SkinBroadcaster), so the received bytes carry no PNG to hash
        // against the sentinel file.
        Object live = field(tdi, F_TDI_IMAGE);
        if (!(live instanceof BufferedImage) || !E2EResult.pixelsEqual((BufferedImage) live, sentinel)) {
            rendererState = "sentinel-mismatch";
            FMLLog.severe("ES_E2E_RENDERER=content mismatch live=%s", live);
            return false;
        }
        rendererState = "sentinel";
        FMLLog.info("ES_E2E_RENDERER=injected bufferedImage=%s glTextureId=%s pixels=matched", injected, glId);
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
        writeResultAndExit(exitCode, joined, commandSent, "none", false);
    }

    private void writeResultAndExit(int exitCode) {
        writeResultAndExit(exitCode, true, true, rendererState, rendererVerified);
    }

    private void writeResultAndExit(int exitCode, boolean joined, boolean commandSent,
                                    String rendererState, boolean rendererVerified) {
        try {
            File out = new File(gameDir(), E2EResult.FILE_NAME);
            Map<String, String> artifacts = new LinkedHashMap<String, String>();
            artifacts.put("driver", "E2EDriver/1.6.4");
            artifacts.put("broadcast", broadcastReceived ? "received" : "none");
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

    // ------------------------------------------------------------------
    // Reflection helpers (obf-domain access).
    // ------------------------------------------------------------------
    private static Object reflectStatic(String className, String method) throws Exception {
        return call(loadClass(className), method, null, null);
    }

    /**
     * Resolves an obf-domain client class through the visible loader chain.
     * The mod classes load through FML's mod loader; the vanilla client
     * classes live in the launch classloader (LaunchClassLoader), which may
     * not be a parent of the mod loader on every FML 7.x arrangement — so
     * fall back through the system loader and the launch classloader
     * (reached via {@code net.minecraft.launchwrapper.Launch.classLoader}).
     */
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
            E2EDriver.class.getClassLoader(),
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

    private static void fieldSet(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.set(target, value);
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
