package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sealed interface contract: Success/Failure discrimination, unwrap behavior.
 */
class HttpResultTest {

    private static final Map<String, List<String>> EMPTY_HEADERS = Map.of();

    @Test
    void successIsSuccess() {
        var resp = new HttpResponse(200, "ok", EMPTY_HEADERS);
        HttpResult result = new HttpResult.Success(resp);
        assertTrue(result.isSuccess());
        assertSame(resp, result.get());
    }

    @Test
    void failureIsNotSuccess() {
        var cause = new IOException("boom");
        HttpResult result = new HttpResult.Failure(cause);
        assertFalse(result.isSuccess());
    }

    @Test
    void failureUnwrapThrows() {
        var cause = new IOException("boom");
        HttpResult result = new HttpResult.Failure(cause);
        var ex = assertThrows(IllegalStateException.class, result::get);
        assertSame(cause, ex.getCause());
    }

    @Test
    void recordComponentAccessors() {
        var resp = new HttpResponse(200, "body", EMPTY_HEADERS);
        var success = new HttpResult.Success(resp);
        assertSame(resp, success.response());

        var cause = new IOException("fail");
        var failure = new HttpResult.Failure(cause);
        assertSame(cause, failure.cause());
    }
}
