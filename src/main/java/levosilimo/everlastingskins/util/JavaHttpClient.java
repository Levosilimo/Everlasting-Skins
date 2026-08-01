package levosilimo.everlastingskins.util;

import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

/**
 * {@link HttpClient} backed by the JDK 21 java.net.http client: HTTP/2 with
 * automatic HTTP/1.1 fallback, connection pooling, and per-request timeouts.
 * Zero new dependencies.
 */
public class JavaHttpClient implements HttpClient {

    private static final boolean ALLOW_HTTP = Boolean.getBoolean("everlastingskins.allowHttp");

    private static java.net.http.HttpClient.Version configuredVersion() {
        try {
            String version = levosilimo.everlastingskins.Config.HTTP_CLIENT_VERSION.get();
            return "HTTP_1_1".equals(version)
                    ? java.net.http.HttpClient.Version.HTTP_1_1
                    : java.net.http.HttpClient.Version.HTTP_2;
        } catch (Exception e) {
            return java.net.http.HttpClient.Version.HTTP_2;
        }
    }

    private static int configuredConnectTimeoutSeconds() {
        try {
            return levosilimo.everlastingskins.Config.HTTP_CONNECT_TIMEOUT_SECONDS.get();
        } catch (Exception e) {
            return 5;
        }
    }

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
            .version(configuredVersion())
            .connectTimeout(Duration.ofSeconds(configuredConnectTimeoutSeconds()))
            .build();

    @Override
    public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                String userAgent, HttpMethod method,
                                Map<String, String> headers, int timeout) throws IOException {
        // Ensure we're never sending a request to a non-HTTPS URL.
        // Allow HTTP when the system property everlastingskins.allowHttp is true (E2E tests with WireMock).
        if (!"https".equals(uri.getScheme()) && !ALLOW_HTTP) {
            throw new IOException("Only HTTPS is supported.");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeout))
                .header("Accept", accepts.getContentType())
                .header("User-Agent", userAgent);

        if (requestBody != null) {
            builder.header("Content-Type", requestBody.type().getContentType());
            builder.method(method.name(), HttpRequest.BodyPublishers.ofString(requestBody.body()));
        } else {
            builder.method(method.name(), HttpRequest.BodyPublishers.noBody());
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        try {
            java.net.http.HttpResponse<String> response = client.send(builder.build(), BodyHandlers.ofString());
            SkinMetrics.INSTANCE.recordProviderStatus(response.statusCode());
            return new HttpResponse(response.statusCode(), response.body(), response.headers().map());
        } catch (IOException e) {
            SkinMetrics.INSTANCE.recordProviderException();
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for response", e);
        }
    }
}
