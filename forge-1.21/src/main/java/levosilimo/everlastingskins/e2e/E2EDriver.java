/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.EverlastingSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-jar client driver for the real-client E2E (master plan slice 3,
 * {@code modern-injar} pattern — reference implementation for the 26.x
 * port; the 26.x lane reuses this shape with unobfuscated names and the
 * same {@code MinecraftForge.EVENT_BUS.addListener} registration, which is
 * EventBus-7 compatible — there is NO static-subscriber delta).
 *
 * <p>Shipped in the mod jar, gated at runtime by
 * {@code -Deverlastingskins.e2e=true} + {@code Dist.CLIENT} (see
 * {@link E2E#install()}). The lane wrapper boots the real 1.21 client the
 * standard ForgeGradle way ({@code runClient} under xvfb + Mesa llvmpipe)
 * and passes the offline session via the launcher args; this driver runs
 * inside that client on the client tick event.
 *
 * <p>MODERN-LINE assertion semantics (vs the pre-1.8 lanes): the server
 * does NOT push PNG bytes on a custom channel — it mutates the player's
 * {@code GameProfile} textures property and re-broadcasts it as vanilla
 * tab-list packets (REMOVE + ADD_PLAYER, {@code VanillaSkinBroadcaster}).
 * The client's tab-list entry is the received packet data:
 * {@code ClientPacketListener.getPlayerInfo(uuid).getProfile()
 * .getProperties().get("textures")}. The driver asserts that property is
 * non-null and its value contains the sentinel marker the server pre-seeded
 * into {@code SkinStorage} (see {@link E2ESentinelHook}) — the property the
 * vanilla renderer consumes, not pixels (llvmpipe-safe by design).
 *
 * <p>Phase machine: WAIT_JOIN (client world + player + connection exist) →
 * send {@code /skin set mojang TestPlayer} (the offline-friendly command
 * surface: the seeded skin's stored source/username match the request, so
 * {@code SkinActionCommand} skips the Mojang HTTP fetch and re-applies +
 * re-broadcasts the stored sentinel — deterministic in a sandboxed
 * network) → WAIT_TEXTURES (poll the tab-list property for the marker) →
 * write {@code e2e-result.json} in the client gameDir → {@code System.exit}.
 *
 * <p>AUTO-CONNECT: the 1.21 launcher removed the legacy {@code --server}/
 * {@code --port} args (verified against the real client jar: Main ignores
 * them via {@code allowsUnrecognizedOptions}) and the 1.21 {@code Main}
 * quick-play option does not fire on a dev launch, so the driver dials the
 * test server itself the first tick the main menu is up
 * ({@link ConnectScreen#startConnecting}) — same precedent as the pre-1.8
 * driver's {@code Minecraft.setServer}. The session itself comes from the
 * launcher args ({@code --username TestPlayer --uuid <offline> --accessToken
 * 0}, verified working in offline mode on the real 1.21 client).
 *
 * <p>Exit codes (master-plan contract): 0 all green | 1 assertion failed
 * (textures property never carried the marker) | 2 retryable infra (join
 * timeout) | 3 driver hard failure.
 */
public final class E2EDriver {

    /** Offline test player (matches the e2e scripts' username arg). */
    public static final String TEST_PLAYER = "TestPlayer";

    /** Command surface exercised by the driver (see class javadoc). */
    public static final String COMMAND = "/skin set mojang " + TEST_PLAYER;

    enum Phase {
        WAIT_JOIN,
        WAIT_TEXTURES,
        DONE
    }

    private static final long JOIN_TIMEOUT_MS = 420_000L;
    private static final long TEXTURES_TIMEOUT_MS = 90_000L;

    /** Test server the driver dials from the main menu (matches the wrapper). */
    public static final String SERVER_HOST = "127.0.0.1";
    public static final int SERVER_PORT = 25565;

    private static volatile Phase phase = Phase.WAIT_JOIN;
    private static volatile long phaseStartedAt = System.currentTimeMillis();
    private static volatile long installedAt = System.currentTimeMillis();
    private static volatile boolean commandSent;
    private static volatile boolean connectStarted;
    private static volatile String lastScreenClass = "";
    private static volatile boolean texturesObserved;
    private static volatile String observedValue = "";

    private E2EDriver() {}

    /** Called from {@code E2E.install()} (one line; gates live here). */
    public static void install() {
        if (!Boolean.getBoolean(E2E.E2E_PROPERTY)) {
            return;
        }
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist
            != net.minecraftforge.api.distmarker.Dist.CLIENT) {
            return;
        }
        // Instance-style addListener: EventBus-7 compatible (26.x needs no
        // static-subscriber delta for this registration).
        MinecraftForge.EVENT_BUS.addListener(E2EDriver::onClientTick);
        EverlastingSkins.logger.info("ES_E2E_DRIVER=installed");
    }

    /**
     * Pure phase-transition function, exercised by the unit tests. The
     * driver advances only on observed facts: join gates on world+player,
     * the assertion completes the run on the observed marker property.
     */
    static Phase nextPhase(Phase current, boolean joined, boolean texturesObserved) {
        return switch (current) {
            case WAIT_JOIN -> joined ? Phase.WAIT_TEXTURES : Phase.WAIT_JOIN;
            case WAIT_TEXTURES -> texturesObserved ? Phase.DONE : Phase.WAIT_TEXTURES;
            default -> Phase.DONE;
        };
    }

    // ------------------------------------------------------------------
    // Client tick (render thread — same thread the tab list is mutated on,
    // so the assert reads a consistent PlayerInfo map).
    // ------------------------------------------------------------------
    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            tick();
        } catch (Throwable t) {
            EverlastingSkins.logger.error("ES_E2E_DRIVER=crash phase={} ({})", phase, t.toString());
            fail(3, false, false, "driver crash: " + t);
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        switch (nextPhase(phase, isJoined(mc), texturesPropertyContainsMarker(mc))) {
            case WAIT_JOIN -> {
                if (mc != null) {
                    // Diagnostic: log once per distinct screen class so the
                    // trial log shows when the title actually appears (the
                    // llvmpipe boot takes minutes between the atlas load and
                    // the title screen).
                    String screenClass = mc.screen == null ? "<null>" : mc.screen.getClass().getName();
                    if (!screenClass.equals(lastScreenClass)) {
                        lastScreenClass = screenClass;
                        EverlastingSkins.logger.info("ES_E2E_SCREEN={}", screenClass);
                        if (mc.screen instanceof DisconnectedScreen ds) {
                            logDisconnectReason(ds);
                        }
                    }
                    if (!connectStarted && mc.screen instanceof TitleScreen) {
                        autoConnect(mc);
                    }
                }
                if (timedOut(JOIN_TIMEOUT_MS)) {
                    fail(2, false, false, "join timeout");
                }
            }
            case WAIT_TEXTURES -> {
                if (!commandSent) {
                    // Join observed on this tick: send the command once, then
                    // wait for the broadcast round-trip.
                    EverlastingSkins.logger.info("ES_E2E_JOIN={}", TEST_PLAYER);
                    sendCommand(mc);
                    EverlastingSkins.logger.info("ES_E2E_COMMAND={}", COMMAND);
                    advance(Phase.WAIT_TEXTURES);
                } else if (timedOut(TEXTURES_TIMEOUT_MS)) {
                    fail(1, true, true,
                        "textures property timeout (broadcast round-trip incomplete)");
                }
            }
            case DONE -> {
                // Marker observed: the tab-list textures property carries the
                // server-seeded sentinel value.
                texturesObserved = true;
                observedValue = texturesProperty(mc);
                EverlastingSkins.logger.info("ES_E2E_RENDERER=ok property={}", observedValue);
                writeResultAndExit(0);
            }
            default -> {
                // unreachable (nextPhase is total)
            }
        }
    }

    private static void advance(Phase next) {
        phase = next;
        phaseStartedAt = System.currentTimeMillis();
    }

    private static boolean timedOut(long budgetMs) {
        return System.currentTimeMillis() - phaseStartedAt > budgetMs;
    }

    // ------------------------------------------------------------------
    // Join + command surface.
    // ------------------------------------------------------------------
    private static boolean isJoined(Minecraft mc) {
        return mc != null && mc.player != null && mc.getConnection() != null;
    }

    private static void logDisconnectReason(DisconnectedScreen screen) {
        // The disconnect reason is not logged by vanilla; read it for the
        // trial log (official-mapped runtime keeps the official field names).
        try {
            java.lang.reflect.Field detailsField = DisconnectedScreen.class.getDeclaredField("details");
            detailsField.setAccessible(true);
            Object details = detailsField.get(screen);
            if (details != null) {
                java.lang.reflect.Field reasonField = details.getClass().getDeclaredField("reason");
                reasonField.setAccessible(true);
                Object reason = reasonField.get(details);
                if (reason instanceof net.minecraft.network.chat.Component c) {
                    EverlastingSkins.logger.info("ES_E2E_DISCONNECT={}", c.getString());
                    return;
                }
            }
            EverlastingSkins.logger.warn("ES_E2E_DISCONNECT=unreadable");
        } catch (Throwable t) {
            EverlastingSkins.logger.warn("ES_E2E_DISCONNECT=unreadable ({})", t.toString());
        }
    }

    /**
     * The 1.21 client has no launcher arg that auto-joins on a dev launch
     * (see class javadoc), so the driver starts the multiplayer connect flow
     * itself once the main menu is up. Gated on {@code TitleScreen}: the
     * early loading screen is also a non-null screen, and connecting while
     * the datapack load is still running gets clobbered by the startup flow
     * (observed live: ES_E2E_CONNECT fired mid-load, the title screen then
     * replaced the connect screen, and no TCP dial ever reached the server).
     */
    private static void autoConnect(Minecraft mc) {
        connectStarted = true;
        String address = SERVER_HOST + ":" + SERVER_PORT;
        ServerData data = new ServerData("e2e", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(mc.screen, mc,
            ServerAddress.parseString(address), data, false, null);
        EverlastingSkins.logger.info("ES_E2E_CONNECT=initiated {}", address);
    }

    private static void sendCommand(Minecraft mc) {
        commandSent = true;
        // sendCommand takes the command WITHOUT the leading slash (the chat
        // GUI strips it before calling; sending "/skin ..." makes the
        // server reply "Unknown or incomplete command" — observed live).
        mc.player.connection.sendCommand(COMMAND.substring(1));
    }

    // ------------------------------------------------------------------
    // The modern assertion: the tab-list copy of the profile (the received
    // ADD_PLAYER packet data) carries the sentinel textures property.
    // ------------------------------------------------------------------
    private static boolean texturesPropertyContainsMarker(Minecraft mc) {
        String value = texturesProperty(mc);
        if (value == null) {
            return false;
        }
        // Also observe the value even before the marker arrives (diagnostic:
        // a non-marker textures property would be a stale/default skin).
        observedValue = value;
        return valueContainsMarker(value);
    }

    /**
     * The textures property value is BASE64 of the vanilla textures JSON, so
     * the marker never appears in the raw string — the check must decode
     * first (observed live: the sentinel was present in the tab list while
     * the raw contains() stayed false).
     */
    private static boolean valueContainsMarker(String value) {
        if (value.contains(E2E.MARKER)) {
            return true;
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
                .contains(E2E.MARKER);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String texturesProperty(Minecraft mc) {
        if (mc == null || mc.getConnection() == null) {
            return null;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(E2E.offlineUuid(TEST_PLAYER));
        if (info == null) {
            return null;
        }
        // authlib 1.21 PropertyMap.get(name) returns a Collection (the
        // textures property is single-valued in practice).
        Collection<Property> textures = info.getProfile().getProperties().get("textures");
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        return textures.iterator().next().value();
    }

    // ------------------------------------------------------------------
    // Result + exit (plain System.exit — the modern lanes have no
    // FMLSecurityManager constraint).
    // ------------------------------------------------------------------
    private static void fail(int exitCode, boolean joined, boolean commandSent, String reason) {
        EverlastingSkins.logger.error("ES_E2E_FAIL={}", reason);
        writeResultAndExit(exitCode, joined, commandSent, false, false);
    }

    private static void writeResultAndExit(int exitCode) {
        writeResultAndExit(exitCode, true, commandSent, texturesObserved, texturesObserved);
    }

    private static void writeResultAndExit(int exitCode, boolean joined, boolean commandSent,
                                           boolean rendererState, boolean rendererVerified) {
        try {
            Minecraft mc = Minecraft.getInstance();
            File out = new File(mc != null ? mc.gameDirectory : new File("."), E2EResult.FILE_NAME);
            Map<String, String> artifacts = new LinkedHashMap<String, String>();
            artifacts.put("driver", "E2EDriver/1.21");
            artifacts.put("command", COMMAND);
            artifacts.put("uuid", E2E.offlineUuid(TEST_PLAYER).toString());
            artifacts.put("property", observedValue.isEmpty() ? "none" : observedValue);
            E2EResult.write(out, joined, commandSent, rendererState, rendererVerified,
                System.currentTimeMillis() - installedAt, exitCode, artifacts);
            EverlastingSkins.logger.info("ES_E2E_RESULT={} code={}", out.getAbsolutePath(), exitCode);
        } catch (Exception e) {
            // Never mask the outcome: a result-write failure is a hard driver
            // failure (exit 3), not a silent success.
            EverlastingSkins.logger.error("ES_E2E_FAIL=result write failed ({})", e.toString());
            System.exit(3);
        }
        System.exit(exitCode);
    }
}
