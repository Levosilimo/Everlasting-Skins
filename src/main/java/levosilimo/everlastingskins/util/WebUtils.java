package levosilimo.everlastingskins.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class WebUtils {

    public static String POSTRequest(URL url, String userAgent, String contentType, String responseType, String input) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Accept", responseType);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setDoOutput(true);

        byte[] postData = input.getBytes(StandardCharsets.UTF_8);
        connection.getOutputStream().write(postData);

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        connection.disconnect();

        return response.toString();
    }

    public static String GETRequest(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        connection.disconnect();

        return response.toString();
    }
}