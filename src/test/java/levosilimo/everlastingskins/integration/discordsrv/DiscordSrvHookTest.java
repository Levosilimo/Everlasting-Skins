package levosilimo.everlastingskins.integration.discordsrv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link DiscordSrvHook}.
 * <p>
 * Covers lib-39 scenario DSRV-2 (null plugin) which is testable without
 * mocking — {@code DiscordSRV.getPlugin()} returns {@code null} naturally
 * when Bukkit is not running.
 * <p>
 * DSRV-3 through DSRV-5 require Mockito static mocking of DiscordSRV, which
 * is unavailable here because:
 * <ol>
 *   <li>DiscordSRV's constant pool references PaperMC types not available
 *       on the mc1.12.2 classpath — ByteBuddy retransformation fails.</li>
 *   <li>Mockito 2.x (the version compatible with Java 8 / ForgeGradle 2.3)
 *       does not support {@code mockStatic}.</li>
 * </ol>
 * These scenarios rely on the same reflection chain and are covered by the
 * 1.21 target where PaperMC API is available.
 * <p>
 * {@code doReturn} cannot bypass the type mismatch for {@code getJda()} return
 * type because the relocated JDA type is not imported and returning {@code null}
 * is the only value that satisfies both the compiler and Mockito's type check.
 */
class DiscordSrvHookTest {

    @Test
    @DisplayName("null DiscordSRV plugin instance is handled gracefully (DSRV-2)")
    void announceSkinChange_handlesNullPluginInstance() {
        // DiscordSRV.getPlugin() returns null when Bukkit is not running.
        // No mocking required — the reflection chain hits null, logs, and returns.
        assertDoesNotThrow(() -> DiscordSrvHook.announceSkinChange(null, "TestSource"));
    }
}
