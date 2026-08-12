/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import com.mojang.authlib.properties.Property;
import levosilimo.everlastingskins.EverlastingSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.util.text.ITextComponent;
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
 * {@code modern-injar} pattern — the 1.16.5 port of the 1.21 driver,
 * adapted to the 1.16.5 API surface, all names bytecode-verified against
 * the FG 5.1 deobf client jar 2026-08-12).
 *
 * <p>Shipped in the mod jar, gated at runtime by
 * {@code -Deverlastingskins.e2e=true} + {@code Dist.CLIENT} (see
 * {@link E2E#install()}). The lane wrapper boots the real 1.16.5 client
 * the standard ForgeGradle way ({@code runClient} under xvfb + Mesa
 * llvmpipe) and passes the offline session via the launcher args; this
 * driver runs inside that client on the client tick event.
 *
 * <p>MODERN-LINE assertion semantics (vs the pre-1.8 lanes): the server
 * does NOT push PNG bytes on a custom channel — it mutates the player's
 * {@code GameProfile} textures property and re-broadcasts it as vanilla
 * tab-list packets (REMOVE + ADD_PLAYER, {@code VanillaSkinBroadcaster}).
 * The client's tab-list entry is the received packet data:
 * {@code ClientPlayNetHandler.getPlayerInfo(uuid).getProfile()
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
 * <p>1.16.5 deltas vs the 1.21 driver (all bytecode-verified):
 * <ul>
 *   <li>Java 8 surface: no switch-expressions / pattern instanceof /
 *       records (phase machine as if/else chains, casts after
 *       {@code instanceof});</li>
 *   <li>the 1.16.5 Main registers {@code --server/--port}, but the vanilla
 *       deferred connect is gated on the authlib privileges request, which
 *       401s for an offline token — the client sits at the main menu
 *       (observed live on the boot-smoke lane, E2EJoinDriver), so the
 *       driver dials the test server itself from the main menu via
 *       {@code ConnectingScreen(Screen, Minecraft, String, int)} (the
 *       1.16.5 menu screen is {@code MainMenuScreen} — {@code TitleScreen}
 *       is 1.17+ naming);</li>
 *   <li>{@code ClientPlayNetHandler} has no {@code sendCommand} (1.18+
 *       addition) and no {@code sendPacket} — packets go out via
 *       {@code send(IPacket)}; commands are {@code CChatMessagePacket}s
 *       with the LEADING slash (the server's
 *       {@code ServerPlayNetHandler.handleChat} routes {@code "/"}-prefixed
 *       messages to the command dispatcher); this is the opposite of 1.21's
 *       {@code sendCommand(COMMAND.substring(1))};</li>
 *   <li>{@code DisconnectedScreen} carries the reason in a private final
 *       {@code ITextComponent reason} field (reflect-read for the trial
 *       log — the {@code details}/{@code reason} pair is 1.19+);</li>
 *   <li>the game dir field is {@code Minecraft.gameDirectory};</li>
 *   <li>the {@code getPlayerInfo} surface is
 *       {@code net.minecraft.client.network.play.ClientPlayNetHandler} +
 *       {@code NetworkPlayerInfo} (the 1.16.5 play-net package).</li>
 * </ul>
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
        MinecraftForge.EVENT_BUS.addListener(E2EDriver::onClientTick);
        EverlastingSkins.logger.info("ES_E2E_DRIVER=installed");
    }

    /**
     * Pure phase-transition function, exercised by the unit tests. The
     * driver advances only on observed facts: join gates on world+player,
     * the assertion completes the run on the observed marker property.
     */
    static Phase nextPhase(Phase current, boolean joined, boolean texturesObserved) {
        if (current == Phase.WAIT_JOIN) {
            return joined ? Phase.WAIT_TEXTURES : Phase.WAIT_JOIN;
        }
        if (current == Phase.WAIT_TEXTURES) {
            return texturesObserved ? Phase.DONE : Phase.WAIT_TEXTURES;
        }
        return Phase.DONE;
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
        Phase next = nextPhase(phase, isJoined(mc), texturesPropertyContainsMarker(mc));
        if (next == Phase.WAIT_JOIN) {
            if (mc != null) {
                // Diagnostic: log once per distinct screen class so the
                // trial log shows when the title actually appears (the
                // llvmpipe boot takes minutes between the atlas load and
                // the title screen).
                String screenClass = mc.screen == null ? "<null>" : mc.screen.getClass().getName();
                if (!screenClass.equals(lastScreenClass)) {
                    lastScreenClass = screenClass;
                    EverlastingSkins.logger.info("ES_E2E_SCREEN={}", screenClass);
                    if (mc.screen instanceof DisconnectedScreen) {
                        logDisconnectReason((DisconnectedScreen) mc.screen);
                    }
                }
                if (!connectStarted && mc.screen instanceof MainMenuScreen) {
                    autoConnect(mc);
                }
            }
            if (timedOut(JOIN_TIMEOUT_MS)) {
                fail(2, false, false, "join timeout");
            }
        } else if (next == Phase.WAIT_TEXTURES) {
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
        } else {
            // DONE: marker observed — the tab-list textures property
            // carries the server-seeded sentinel value.
            texturesObserved = true;
            observedValue = texturesProperty(mc);
            EverlastingSkins.logger.info("ES_E2E_RENDERER=ok property={}", observedValue);
            writeResultAndExit(0);
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
        // trial log. 1.16.5 keeps it in a private final ITextComponent
        // "reason" field (the details/reason pair is 1.19+).
        try {
            java.lang.reflect.Field reasonField = DisconnectedScreen.class.getDeclaredField("reason");
            reasonField.setAccessible(true);
            Object reason = reasonField.get(screen);
            if (reason instanceof ITextComponent) {
                EverlastingSkins.logger.info("ES_E2E_DISCONNECT={}",
                    ((ITextComponent) reason).getString());
                return;
            }
            EverlastingSkins.logger.warn("ES_E2E_DISCONNECT=unreadable");
        } catch (Throwable t) {
            EverlastingSkins.logger.warn("ES_E2E_DISCONNECT=unreadable ({})", t.toString());
        }
    }

    /**
     * The vanilla deferred connect from {@code --server/--port} never fires
     * for an offline session (the authlib privileges request 401s —
     * bytecode-verified on this lane), so the driver starts the multiplayer
     * connect flow itself once the main menu is up. Gated on
     * {@code MainMenuScreen}: the early loading screen is also a non-null
     * screen, and connecting while the datapack load is still running gets
     * clobbered by the startup flow (observed live on 1.21: ES_E2E_CONNECT
     * fired mid-load, the title screen then replaced the connect screen,
     * and no TCP dial ever reached the server).
     */
    private static void autoConnect(Minecraft mc) {
        connectStarted = true;
        mc.setScreen(new ConnectingScreen(mc.screen, mc, SERVER_HOST, SERVER_PORT));
        EverlastingSkins.logger.info("ES_E2E_CONNECT=initiated {}:{}", SERVER_HOST, SERVER_PORT);
    }

    private static void sendCommand(Minecraft mc) {
        commandSent = true;
        // 1.16.5 has no sendCommand on ClientPlayNetHandler (1.18+): the
        // client sends commands as chat packets WITH the leading slash via
        // connection.send(...) (sendPacket is also 1.18+ naming) and
        // ServerPlayNetHandler.handleChat routes them to the dispatcher.
        mc.player.connection.send(new CChatMessagePacket(COMMAND));
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
     * first (observed live on 1.21: the sentinel was present in the tab
     * list while the raw contains() stayed false).
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
        NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(E2E.offlineUuid(TEST_PLAYER));
        if (info == null) {
            return null;
        }
        // authlib 1.5.25 PropertyMap extends a String->Property multimap, so
        // get("textures") returns a Collection (single-valued in practice).
        Collection<Property> textures = info.getProfile().getProperties().get("textures");
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        return textures.iterator().next().getValue();
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
            artifacts.put("driver", "E2EDriver/1.16.5");
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
