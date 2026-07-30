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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EverlastingHelpers {

    private static final String NAMEMC_IMG_URL = EndpointsConfig.getString("url.namemc.img");

    private EverlastingHelpers() {
    }

    public static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable.getCause();
        if (cause == null) {
            return throwable;
        }
        return getRootCause(cause);
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

    public static void renameFile(Path parent, String oldName, String newName) throws IOException {
        try (Stream<Path> stream = Files.list(parent)) {
            List<String> files = stream.map(new java.util.function.Function<Path, String>() {
                @Override
                public String apply(Path p) {
                    return p.getFileName().toString();
                }
            }).collect(Collectors.<String>toList());

            String tempName = newName + "_temp";
            if (files.contains(oldName) && !files.contains(tempName) && !files.contains(newName)) {
                Path oldPath = parent.resolve(oldName);
                Path tempPath = parent.resolve(tempName);
                Path newPath = parent.resolve(newName);

                Files.move(oldPath, tempPath, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tempPath, newPath, StandardCopyOption.REPLACE_EXISTING);
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

    public static String sanitizeImageURL(String imageUrl) {
        Optional<URL> parsed = parseURL(imageUrl);
        if (!parsed.isPresent()) {
            return imageUrl;
        }
        URL url = parsed.get();
        String host = url.getHost();
        if (host == null) {
            return imageUrl;
        }
        boolean isNameMc = "namemc.com".equals(host) || host.endsWith(".namemc.com");
        if (isNameMc) {
            String path = url.getPath();
            if (path != null) {
                String skinPrefix = "/skin/";
                if (path.startsWith(skinPrefix)) {
                    String uuidPart = path.substring(skinPrefix.length());
                    return String.format(NAMEMC_IMG_URL, uuidPart);
                }
            }
        }
        return imageUrl;
    }

    public static String sanitizeSkinInput(String skinInput) {
        Optional<URL> parsed = parseURL(skinInput);
        if (!parsed.isPresent()) {
            return skinInput;
        }
        URL url = parsed.get();
        String host = url.getHost();
        if (host == null) {
            return skinInput;
        }
        boolean isNameMc = "namemc.com".equals(host) || host.endsWith(".namemc.com");
        if (isNameMc) {
            String path = url.getPath();
            if (path != null) {
                String profilePrefix = "/profile/";
                if (path.startsWith(profilePrefix)) {
                    String usernamePart = path.substring(profilePrefix.length());
                    int dotIdx = usernamePart.indexOf('.');
                    if (dotIdx != -1) {
                        usernamePart = usernamePart.substring(0, dotIdx);
                    }
                    return usernamePart;
                }
            }
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
}
