package levosilimo.everlastingskins.gametest;

import java.util.concurrent.TimeUnit;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Shared polling assertion for GameTest packet-arrival waits (lib-47).
 *
 * <p>The GameTest framework runs succeedWhen/runAtEveryTick runnables once per
 * server tick and SWALLOWS {@link GameTestAssertException} (GameTestInfo's
 * sequence tick path catches it and retries next tick), so a throwing
 * assertion is the idiom for "keep waiting". The framework only converts that
 * swallow into a test failure when the TICK budget (timeoutTicks/maxTicks)
 * runs out — at which point the strict timeout path reports the last
 * transient assertion message ("target player must receive at least 1
 * ADD_PLAYER ..., got 0") as the failure. Wall-clock deadlines implemented
 * with plain GameTestAssertException are equally ineffective: they are
 * swallowed too. That let packet-arrival asserts race the tick budget instead
 * of a wall-clock deadline (skinset_selfreceivesbroadcast on forge-1.21 /
 * 1.21.4, concurrent_skin_set_two_players on forge-26.2, all on loaded CI
 * runners).
 *
 * <p>This helper fixes the deadline side. Create ONE {@link Deadline} outside
 * the succeedWhen lambda and use it for the whole wait:
 *
 * <pre>{@code
 * PacketAssert.Deadline deadline = PacketAssert.deadline(20_000);
 * helper.succeedWhen(() -> {
 *     PacketAssert.checkDeadline(helper, deadline, "self-reception packet");
 *     ...
 *     PacketAssert.assertEventually(helper, deadline, () -> {
 *         long count = countAddPlayerUpdatesWithTextures(drain(playerA), playerId);
 *         if (count < 1) {
 *             throw new GameTestAssertException("... got " + count);
 *         }
 *     });
 * });
 * }</pre>
 *
 * <p>While the deadline holds, failures rethrow as
 * {@link GameTestAssertException} (swallowed, re-polled next tick); once it
 * passes, the helper fails the test via {@link GameTestHelper#fail(String)} —
 * the framework's own failure channel — so the test fails AT the wall-clock
 * deadline with a clear timeout message instead of surfacing a transient
 * "got 0" from a race the framework would have tolerated a tick later.
 */
public final class PacketAssert {

    private PacketAssert() {
    }

    /** Wall-clock deadline for a packet-arrival wait. Create once, reuse across polls. */
    public static Deadline deadline(long timeoutMs) {
        return new Deadline(timeoutMs);
    }

    /**
     * Runs {@code assertion} once. On {@link GameTestAssertException} with time
     * remaining, rethrows it so the framework's per-tick swallow path re-polls
     * next tick; when the deadline has passed, fails the test via the helper
     * instead. MUST be called from the server thread (test body or a
     * succeedWhen poll) and share a single {@link Deadline} across polls —
     * a fresh deadline per poll would never expire.
     */
    public static void assertEventually(GameTestHelper helper, Deadline deadline, Runnable assertion) {
        try {
            assertion.run();
        } catch (GameTestAssertException failure) {
            if (deadline.expired()) {
                helper.fail("timed out after " + deadline.timeoutMs + "ms waiting: " + failure.getMessage());
            } else {
                throw failure;
            }
        }
    }

    /**
     * Fails the test via the helper once the deadline has passed; no-op
     * otherwise. Call at the top of a succeedWhen poll so the whole wait —
     * not just the packet phase — is bounded by the wall-clock deadline.
     */
    public static void checkDeadline(GameTestHelper helper, Deadline deadline, String what) {
        if (deadline.expired()) {
            helper.fail("timed out after " + deadline.timeoutMs + "ms waiting for " + what);
        }
    }

    /**
     * One-shot convenience (no helper, no cross-poll state): runs the
     * assertion once and rethrows {@link GameTestAssertException} while the
     * deadline holds, or throws {@link AssertionError} once it expires. The
     * framework's swallow path only catches GameTestAssertException, so the
     * AssertionError surfaces as a failure even from inside a succeedWhen
     * poll. Do NOT call this repeatedly from a succeedWhen lambda with a
     * fresh timeout each poll — the deadline would never expire; use
     * {@link #deadline(long)} + {@link #assertEventually(GameTestHelper,
     * Deadline, Runnable)} instead.
     */
    public static void assertEventually(Runnable assertion, long timeoutMs) {
        Deadline deadline = deadline(timeoutMs);
        try {
            assertion.run();
        } catch (GameTestAssertException failure) {
            if (deadline.expired()) {
                throw new AssertionError(
                        "timed out after " + timeoutMs + "ms waiting: " + failure.getMessage(), failure);
            }
            throw failure;
        }
    }

    /** Shared wall-clock deadline; created by {@link #deadline(long)}. */
    public static final class Deadline {
        private final long timeoutMs;
        private final long deadlineNanos;

        private Deadline(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            this.deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        }

        boolean expired() {
            return System.nanoTime() > deadlineNanos;
        }
    }
}
