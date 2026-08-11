/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import levosilimo.everlastingskins.forge102.EverlastingSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * In-jar client driver for the real-client E2E (master plan slice 2,
 * {@code scripts/e2e/drivers/headlessmc.sh}, 1.10.2 lane).
 *
 * <p>Shipped in the mod jar, gated at runtime by
 * {@code -Deverlastingskins.e2e=true} + {@code Side.CLIENT} (see
 * {@link E2E#install()}). Lifecycle: the mod's {@code @Mod.EventHandler init}
 * installs the driver; the tick primitive is the modern
 * {@link TickEvent.ClientTickEvent} via {@link MinecraftForge#EVENT_BUS}.
 *
 * <p>Phase machine: WAIT_JOIN (client player exists) → send
 * {@code /skin set mojang Notch} through the real chat surface
 * ({@code EntityPlayerSP.sendChatMessage} — no reflection: the lane compiles
 * against the deobfuscated client, unlike the 1.6.4 driver) → WAIT_ACK (the
 * server applies the skin asynchronously; the server-side {@code ES_E2E_SKIN}
 * sentinel in {@code SkinAction} is the acknowledgment) → write
 * {@code e2e-result.json} in the client gameDir → {@code System.exit}.
 *
 * <p>Exit codes (master-plan contract): 0 all green | 1 assertion failed |
 * 2 retryable infra (join timeout) | 3 driver hard failure.
 */
public final class E2EDriver {

    /** Offline test player (matches the e2e scripts' {@code --username}). */
    public static final String TEST_PLAYER = "TestPlayer";

    private static final Logger LOGGER = LogManager.getLogger("everlastingskins.e2e");

    private static final long JOIN_TIMEOUT_MS = 180_000L;
    private static final long ACK_WAIT_MS = 20_000L;

    /** Client-side driver phases (pure transition logic in {@link #nextPhase}). */
    enum Phase {
        WAIT_JOIN, WAIT_ACK, DONE
    }

    /**
     * Pure phase transition used by the tick machine and unit tests: the
     * driver advances only on observed facts.
     */
    static Phase nextPhase(Phase current, boolean joined, boolean ackElapsed) {
        switch (current) {
            case WAIT_JOIN:
                return joined ? Phase.WAIT_ACK : Phase.WAIT_JOIN;
            case WAIT_ACK:
                return ackElapsed ? Phase.DONE : Phase.WAIT_ACK;
            default:
                return current;
        }
    }

    private final long installedAt = System.currentTimeMillis();
    private Phase phase = Phase.WAIT_JOIN;
    private long phaseStartedAt = installedAt;
    private boolean commandSent;

    private E2EDriver() {}

    /** Installs the client driver (property + side gates live in {@link E2E#install()}). */
    static void install() {
        MinecraftForge.EVENT_BUS.register(new E2EDriver());
        LOGGER.info("ES_E2E_DRIVER=installed side=client");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            tick();
        } catch (Throwable t) {
            LOGGER.error("ES_E2E_DRIVER=crash phase={} ({})", phase, t);
            writeResultAndExit(3, false, false, "driver crash: " + t);
        }
    }

    private void tick() {
        switch (phase) {
            case WAIT_JOIN:
                if (isJoined()) {
                    LOGGER.info("ES_E2E_JOIN={}", TEST_PLAYER);
                    sendChat("/skin set mojang Notch");
                    commandSent = true;
                    LOGGER.info("ES_E2E_COMMAND=/skin set mojang Notch");
                    advance(Phase.WAIT_ACK);
                } else if (timedOut(JOIN_TIMEOUT_MS)) {
                    writeResultAndExit(2, false, false, "join timeout");
                }
                break;
            case WAIT_ACK:
                if (timedOut(ACK_WAIT_MS)) {
                    advance(Phase.DONE);
                    LOGGER.info("ES_E2E_ACK=elapsed");
                    writeResultAndExit(0, true, true, null);
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

    private boolean isJoined() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.player != null;
    }

    private void sendChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player != null) {
            player.sendChatMessage(message);
        }
    }

    private File gameDir() {
        return Minecraft.getMinecraft().mcDataDir;
    }

    // ------------------------------------------------------------------
    // Result + exit.
    // ------------------------------------------------------------------
    private void writeResultAndExit(int exitCode, boolean joined, boolean commandExecuted, String reason) {
        if (reason != null) {
            LOGGER.error("ES_E2E_FAIL={}", reason);
        }
        try {
            File out = new File(gameDir(), E2EResult.FILE_NAME);
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"lane\": \"1.10.2\",\n");
            sb.append("  \"client_joined\": ").append(joined).append(",\n");
            sb.append("  \"command_executed\": ").append(commandExecuted).append(",\n");
            sb.append("  \"renderer_state\": \"n/a\",\n");
            sb.append("  \"renderer_verified\": false,\n");
            sb.append("  \"duration_ms\": ").append(System.currentTimeMillis() - installedAt).append(",\n");
            sb.append("  \"exit_code\": ").append(exitCode).append(",\n");
            sb.append("  \"artifacts\": { \"driver\": \"E2EDriver/1.10.2\" }\n");
            sb.append("}\n");
            Files.write(out.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            LOGGER.info("ES_E2E_RESULT={} code={}", out.getAbsolutePath(), exitCode);
        } catch (IOException e) {
            // Never mask the outcome: a result-write failure is a hard driver
            // failure (exit 3), not a silent success.
            LOGGER.error("ES_E2E_FAIL=result write failed ({})", e.toString());
            System.exit(3);
        }
        System.exit(exitCode);
    }
}
