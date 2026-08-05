/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.util;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * General-purpose helpers shared across the mod: hashing, formatting, URL
 * validation, and NameMC URL rewriting.
 */
public final class EverlastingHelpers {

    private static final String NAMEMC_IMG_URL = EndpointsConfig.getString("url.namemc.img");
    private static final String NAMEMC_HOST = "namemc.com";

    private EverlastingHelpers() {
    }

    public static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static long hashSha256String(String str) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static byte[] md5(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    public static String hashMD5(byte[] input) {
        byte[] digest = md5(input);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    public static long getEpochSecond() {
        return Instant.now().getEpochSecond();
    }

    public static boolean isURL(String str) {
        return str != null && (str.startsWith("http://") || str.startsWith("https://"));
    }

    public static String readableBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;
        if (bytes < mb) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / (double) kb);
        }
        if (bytes < gb) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (double) mb);
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (double) gb);
    }

    /**
     * Rename a file inside {@code parent} via a temporary name so a partial
     * move is never observed under the final name. No-op when the old file is
     * missing or either target name is already taken.
     */
    public static void renameFile(Path parent, String oldName, String newName) throws IOException {
        try (Stream<Path> stream = Files.list(parent)) {
            Set<String> files = stream
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.<String>toSet());

            String tempName = newName + "_temp";
            if (files.contains(oldName) && !files.contains(tempName) && !files.contains(newName)) {
                Files.move(parent.resolve(oldName), parent.resolve(tempName), StandardCopyOption.REPLACE_EXISTING);
                Files.move(parent.resolve(tempName), parent.resolve(newName), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static <E> E getRandomEntry(List<E> list) {
        Random random = ThreadLocalRandom.current();
        return list.get(random.nextInt(list.size()));
    }

    public static <E> E getRandomEntry(Set<E> set) {
        Random random = ThreadLocalRandom.current();
        int index = random.nextInt(set.size());
        int i = 0;
        for (E entry : set) {
            if (i == index) {
                return entry;
            }
            i++;
        }
        throw new IllegalStateException("Cannot get random entry from empty set");
    }

    public static int getJavaVersion() {
        String specVersion = System.getProperty("java.specification.version");
        String[] parts = specVersion.split("\\.");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid Java version: " + specVersion);
        }
        if ("1".equals(parts[0]) && parts.length > 1) {
            return Integer.parseInt(parts[1]);
        }
        return Integer.parseInt(parts[0]);
    }

    public static String lowerCaseCapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return capitalize(str.toLowerCase(Locale.ROOT));
    }

    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    public static Optional<URL> parseURL(String str) {
        if (str == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URL(str));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Rewrite a NameMC skin page URL to the direct image URL; anything else
     * passes through unchanged.
     */
    public static String sanitizeImageURL(String imageUrl) {
        Optional<URL> parsed = parseURL(imageUrl);
        if (!parsed.isPresent() || !isNameMcHost(parsed.get().getHost())) {
            return imageUrl;
        }
        URL url = parsed.get();
        String path = url.getPath();
        String skinPrefix = "/skin/";
        if (path != null && path.startsWith(skinPrefix)) {
            String uuidPart = path.substring(skinPrefix.length());
            return String.format(NAMEMC_IMG_URL, uuidPart);
        }
        return imageUrl;
    }

    /**
     * Extract the username from a NameMC profile page URL; anything else
     * passes through unchanged.
     */
    public static String sanitizeSkinInput(String skinInput) {
        Optional<URL> parsed = parseURL(skinInput);
        if (!parsed.isPresent() || !isNameMcHost(parsed.get().getHost())) {
            return skinInput;
        }
        URL url = parsed.get();
        String path = url.getPath();
        String profilePrefix = "/profile/";
        if (path != null && path.startsWith(profilePrefix)) {
            String usernamePart = path.substring(profilePrefix.length());
            int dotIdx = usernamePart.indexOf('.');
            if (dotIdx != -1) {
                usernamePart = usernamePart.substring(0, dotIdx);
            }
            return usernamePart;
        }
        return skinInput;
    }

    public static boolean invalidMinecraftUsername(String str) {
        String trimmed = str.trim();
        if (trimmed.length() > 16) {
            return true;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9')
                    && c != '_'
                    && c != '-') {
                return true;
            }
        }
        return false;
    }

    public static boolean validSkinUrl(String str) {
        Optional<URL> parsed = parseURL(str);
        if (!parsed.isPresent()) {
            return false;
        }
        String protocol = parsed.get().getProtocol();
        return "http".equals(protocol) || "https".equals(protocol);
    }

    private static boolean isNameMcHost(String host) {
        return host != null && (NAMEMC_HOST.equals(host) || host.endsWith("." + NAMEMC_HOST));
    }
}
