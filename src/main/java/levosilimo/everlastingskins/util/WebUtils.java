package levosilimo.everlastingskins.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static levosilimo.everlastingskins.util.RandomUserAgent.getRandomUserAgent;

public class WebUtils {

    public static String POSTRequest(URL url, String userAgent, String contentType, String responseType, String input) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url.toURI())
                .header("Content-Type", contentType)
                .header("Accept", responseType)
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(input, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        CompletableFuture<HttpResponse<String>> responseCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return responseCompletableFuture.get().body();
    }

    public static String GETRequest(URL url) throws URISyntaxException, ExecutionException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url.toURI())
                .build();

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        CompletableFuture<HttpResponse<String>> responseCompletableFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return responseCompletableFuture.get().body();
    }
}