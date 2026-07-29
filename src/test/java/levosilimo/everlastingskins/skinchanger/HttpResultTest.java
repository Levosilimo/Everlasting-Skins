package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpResult contract: Success/Failure discrimination, unwrap behavior.
 */
class HttpResultTest {

    private static final Map<String, List<String>> EMPTY_HEADERS = Collections.emptyMap();

    @Test
    void successIsSuccess() {
        HttpResponse resp = new HttpResponse(200, "ok", EMPTY_HEADERS);
        HttpResult result = new HttpResult.Success(resp);
        assertTrue(result.isSuccess());
        assertSame(resp, result.get());
    }

    @Test
    void failureIsNotSuccess() {
        IOException cause = new IOException("boom");
        HttpResult result = new HttpResult.Failure(cause);
        assertFalse(result.isSuccess());
    }

    @Test
    void failureUnwrapThrows() {
        IOException cause = new IOException("boom");
        HttpResult result = new HttpResult.Failure(cause);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> result.get());
        assertSame(cause, ex.getCause());
    }

    @Test
    void recordComponentAccessors() {
        HttpResponse resp = new HttpResponse(200, "body", EMPTY_HEADERS);
        HttpResult.Success success = new HttpResult.Success(resp);
        assertSame(resp, success.response());

        IOException cause = new IOException("fail");
        HttpResult.Failure failure = new HttpResult.Failure(cause);
        assertSame(cause, failure.cause());
    }
}
