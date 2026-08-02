/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.util;


import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.responses.HttpResponse;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpsUrlConnectionHttpClient implements HttpClient {
    private final Logger logger = EverlastingSkins.logger;

    @Override
    public HttpResponse execute(URI uri, RequestBody requestBody, HttpType accepts,
                                String userAgent, HttpMethod method,
                                Map<String, String> headers, int timeout) throws IOException {
        long start = System.currentTimeMillis();
        URL url = uri.toURL();

        // Ensure we're never sending a request to a non-HTTPS URL.
        // Allow HTTP when the system property everlastingskins.allowHttp is true (E2E tests with WireMock).
        if (!url.getProtocol().equals("https") && !Boolean.getBoolean("everlastingskins.allowHttp")) {
            throw new IOException("Only HTTPS is supported.");
        }

        logger.debug("Sending " + method + " request to " + url + " with body: " + requestBody);

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod(method.name());
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setDoInput(true);
        connection.setUseCaches(false);

        connection.setRequestProperty("Accept", accepts.getContentType());
        connection.setRequestProperty("User-Agent", userAgent);

        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }

        connection.setDoOutput(requestBody != null);
        if (requestBody != null) {
            connection.setRequestProperty("Content-Type", requestBody.type().getContentType());

            byte[] body = requestBody.body().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
        }

        connection.connect();

        InputStream is;
        try {
            is = connection.getInputStream();
        } catch (IOException e) {
            logger.debug("Failed to get input stream, falling back to error stream.", e);
            is = connection.getErrorStream();
        }

        if (is == null) {
            throw new IOException("Failed to get input stream.");
        }

        HttpResponse response;
        try (InputStream in = is; ByteArrayOutputStream byteData = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                byteData.write(buffer, 0, read);
            }

            response = new HttpResponse(
                    connection.getResponseCode(),
                    byteData.toString(StandardCharsets.UTF_8),
                    connection.getHeaderFields()
            );
        } catch (IOException e) {
            SkinMetrics.INSTANCE.recordProviderException();
            throw e;
        }
        SkinMetrics.INSTANCE.recordProviderStatus(response.statusCode());

        logger.debug("Response body: " + response.body()
                .replace("\n", "")
                .replace("\r", ""));
        logger.debug("Response code: " + response.statusCode());
        logger.debug("Request took " + (System.currentTimeMillis() - start) + "ms.");

        return response;
    }
}
