package levosilimo.everlastingskins;

import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import levosilimo.everlastingskins.util.HttpClient;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic HTTP client stub for unit tests.
 * Returns canned responses for configured URIs and throws IOExceptions
 * for URIs registered as timeouts. Any unregistered URI causes a test failure.
 */
public class FakeHttpClient implements HttpClient {

    private static final Map<String, List<String>> EMPTY_HEADERS = Collections.emptyMap();

    private final Map<URI, HttpResponse> responses = new HashMap<URI, HttpResponse>();
    private URI timeoutUri = null;
    private URI errorUri = null;
    private IOException errorException = null;

    /**
     * Register a canned response for the given URI.
     */
    public void addResponse(URI uri, int statusCode, String body) {
        responses.put(uri, new HttpResponse(statusCode, body, EMPTY_HEADERS));
    }

    /**
     * Register a canned response with custom headers.
     */
    public void addResponse(URI uri, int statusCode, String body, Map<String, List<String>> headers) {
        responses.put(uri, new HttpResponse(statusCode, body, headers));
    }

    /**
     * Register a URI that should trigger an IOException (simulates network timeout).
     */
    public void addTimeout(URI uri) {
        this.timeoutUri = uri;
    }

    /**
     * Register a URI that should trigger a specific IOException.
     */
    public void addError(URI uri, IOException exception) {
        this.errorUri = uri;
        this.errorException = exception;
    }

    @Override
    public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                String userAgent, HttpMethod method,
                                Map<String, String> headers, int timeout) throws IOException {
        if (uri.equals(timeoutUri)) {
            throw new IOException("Simulated timeout for: " + uri);
        }
        if (uri.equals(errorUri) && errorException != null) {
            throw errorException;
        }
        HttpResponse response = responses.get(uri);
        if (response == null) {
            throw new IOException("Unexpected URI in test: " + uri);
        }
        return response;
    }
}
