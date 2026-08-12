/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.integration.TestProperties;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * ES_E2E_SKIN sentinel markers (command-driven E2E, mirror of the 1.7.10
 * lane): under -Deverlastingskins.e2e=true the mod logs cmd/ok/fail markers
 * that the HeadlessMC driver greps verbatim from the server log. These tests
 * pin the exact marker payloads (the driver's grep is a substring match on
 * "ES_E2E_SKIN=ok player=$E2E_USERNAME", so a payload drift would silently
 * red the E2E) and the property gate itself.
 *
 * The static-final logger is swapped for a Mockito mock for the duration of
 * each test (Java-8 modifiers surgery, same style as SkinActionTestAccess);
 * the sentinel fires on the async completion thread, so async sentinel
 * assertions use Mockito timeout() to wait for the invocation itself.
 */
class E2ESentinelTest {

    private static final String E2E_PROPERTY = "everlastingskins.e2e";
    private static final Logger REAL_LOGGER = EverlastingSkins.logger;
    private static final Field LOGGER_FIELD = openLoggerField();

    @TempDir
    Path tempDir;

    private TestServerContext ctx;
    private FakeMojangAPI fake;
    private Logger logger;

    private static Field openLoggerField() {
        try {
            Field field = EverlastingSkins.class.getDeclaredField("logger");
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot open EverlastingSkins.logger; field layout changed?", e);
        }
    }

    @BeforeEach
    void setUp() throws IllegalAccessException {
        logger = mock(Logger.class);
        LOGGER_FIELD.set(null, logger);
        System.setProperty(E2E_PROPERTY, "true");
        ctx = new TestServerContext(tempDir);
        fake = new FakeMojangAPI();
        SkinCommandTestAccess.setMojangAPI(fake);
        SkinMetrics.INSTANCE.reset();
        SkinActionTestAccess.clearGuardState();
    }

    @AfterEach
    void tearDown() throws IllegalAccessException {
        LOGGER_FIELD.set(null, REAL_LOGGER);
        System.clearProperty(E2E_PROPERTY);
        SkinCommandTestAccess.resetAPIs();
        SkinActionTestAccess.clearGuardState();
        ctx.close();
    }

    @Test
    @DisplayName("command entry marker logged with the E2E property set")
    void commandMarker_loggedWhenE2EEnabled() {
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");

        verify(logger).info(eq("ES_E2E_SKIN=cmd player={} action={} args={}"),
            eq("Alice"), eq("set"), eq("[set, mojang, Notch]"));
    }

    @Test
    @DisplayName("success sentinel logged after a completed apply")
    void successSentinel_loggedAfterApply() {
        fake.addSkin("Notch", TestProperties.NOTCH);
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");

        assertTrue(AsyncSupport.await(5000,
                () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
            "skin must apply before the sentinel is asserted");

        verify(logger, timeout(5000)).info(eq("ES_E2E_SKIN=ok player={} source={}"), eq("Alice"), eq("Notch"));
    }

    @Test
    @DisplayName("failure sentinel logged when the provider returns no result")
    void failureSentinel_loggedWhenProviderReturnsNoResult() {
        // No skin registered for "Ghost" -> provider empty -> no-skin branch.
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.commandManager.executeCommand(alice, "/skin set mojang Ghost");

        // The sentinel fires on the async completion thread (SkinAction's
        // EXECUTOR), so the assertion must wait for the invocation itself:
        // awaiting the chat reply does NOT synchronize, because the
        // synchronous "change" toast (Config.TOGGLE=true, sent in apply()
        // before the fetch is submitted) is also an SPacketChat and satisfies
        // a packet-count predicate before the completion thread has logged
        // anything (observed as ArgumentsAreDifferent / TooFewActualInvocations
        // flakes on CI). timeout() polls the mock until the fail sentinel
        // appears or the window elapses, and still fails on a genuinely
        // broken sentinel.
        verify(logger, timeout(5000)).info(eq("ES_E2E_SKIN=fail player={} source={} reason=no-skin"),
            eq("Alice"), eq("Ghost"));
    }

    @Test
    @DisplayName("all markers suppressed without the E2E property")
    void markers_suppressedWithoutE2EProperty() {
        System.clearProperty(E2E_PROPERTY);
        fake.addSkin("Notch", TestProperties.NOTCH);
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");

        assertTrue(AsyncSupport.await(5000,
                () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
            "skin must apply before the suppression is asserted");

        verify(logger, never()).info(eq("ES_E2E_SKIN=cmd player={} action={} args={}"), any(), any(), any());
        verify(logger, never()).info(eq("ES_E2E_SKIN=ok player={} source={}"), any(), any());
        verify(logger, never()).info(eq("ES_E2E_SKIN=fail player={} source={} reason=no-skin"), any(), any());
    }
}
