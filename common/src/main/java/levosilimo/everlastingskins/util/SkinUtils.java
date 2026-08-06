/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SkinUtils {
    private static final String NAMEMC_IMG_URL = EndpointsConfig.getString("url.namemc.img");

    private SkinUtils() {
    }

    public static Throwable getRootCause(Throwable throwable) {
        if (throwable.getCause() != null) {
            return getRootCause(throwable.getCause());
        }

        return throwable;
    }

    public static long hashSha256String(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(str.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to get SHA-256 hash algorithm", e);
        }
    }

    public static byte[] md5(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static long getEpochSecond() {
        return System.currentTimeMillis() / 1000L;
    }

    public static void renameFile(Path parent, String oldName, String newName) throws IOException {
        try (Stream<Path> stream = Files.list(parent)) {
            // Folders are case-insensitive on Windows, so we need to check it using this method
            List<String> files = stream.map(Path::getFileName).map(Path::toString).collect(Collectors.toList());

            String tempName = newName + "_temp";
            if (files.contains(oldName) && !files.contains(tempName) && !files.contains(newName)) {
                Path oldPath = parent.resolve(oldName);
                Path tempPath = parent.resolve(tempName);
                Path newPath = parent.resolve(newName);

                // Windows will not allow renaming a folder to a name that differs only in case
                // So we need to rename it to a temporary name first
                Files.move(oldPath, tempPath, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tempPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static <E> E getRandomEntry(List<E> list) {
        Random random = ThreadLocalRandom.current();
        return list.get(random.nextInt(list.size()));
    }

    public static <E> E getRandomEntry(Set<E> list) {
        Random random = ThreadLocalRandom.current();
        int index = random.nextInt(list.size());
        int i = 0;
        for (E entry : list) {
            if (i == index) {
                return entry;
            }
            i++;
        }

        throw new IllegalStateException("Failed to get random entry");
    }

    public static int getJavaVersion() {
        String specVersion = System.getProperty("java.specification.version");
        String[] split = specVersion.split("\\.");

        String majorVersion;
        if (split.length == 0 || split.length > 2) {
            throw new IllegalArgumentException("Invalid Java version: " + specVersion);
        } else if (split.length == 1) {
            majorVersion = split[0];
        } else if (split[0].equals("1")) {
            majorVersion = split[1];
        } else {
            throw new IllegalArgumentException("Invalid Java version: " + specVersion);
        }

        return Integer.parseInt(majorVersion);
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
        try {
            return Optional.of(new URL(str));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String sanitizeImageURL(String imageUrl) {
        Optional<URL> uriOptional = parseURL(imageUrl);
        if (!uriOptional.isPresent()) {
            return imageUrl;
        }

        String host = uriOptional.get().getHost();

        if (host == null) {
            return imageUrl;
        }

        boolean isNamemc = host.equals("namemc.com") || host.endsWith(".namemc.com");
        if (isNamemc) {
            String path = uriOptional.get().getPath();
            if (path == null) {
                return imageUrl;
            }

            String skinPath = "/skin/";
            if (path.startsWith(skinPath)) {
                String uuid = path.substring(skinPath.length());
                return String.format(NAMEMC_IMG_URL, uuid);
            }


        }

        return imageUrl;
    }

    public static String sanitizeSkinInput(String skinInput) {
        Optional<URL> uriOptional = parseURL(skinInput);
        if (!uriOptional.isPresent()) {
            return skinInput;
        }

        String host = uriOptional.get().getHost();
        if (host == null) {
            return skinInput;
        }

        boolean isNamemc = host.equals("namemc.com") || host.endsWith(".namemc.com");
        if (isNamemc) {
            String path = uriOptional.get().getPath();
            if (path == null) {
                return skinInput;
            }

            String profilePath = "/profile/";
            if (path.startsWith(profilePath)) {
                String usernamePart = path.substring(profilePath.length());
                int dotIndex = usernamePart.indexOf('.');
                if (dotIndex != -1) {
                    usernamePart = usernamePart.substring(0, dotIndex);
                }
                return usernamePart;
            }
        }

        return skinInput;
    }

    public static boolean invalidMinecraftUsername(String str) {
        str = str.trim();
        // Note: there are exceptions to players with under 3 characters, who bought the game early in its development.
        if (str.length() > 16) {
            return true;
        }

        // For some reason, Apache's Lists.charactersOf is faster than character indexing for small strings.
        for (char c : str.toCharArray()) {
            // Note: Players who bought the game early in its development can have "-" in usernames.
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z') && c != '_' && c != '-') {
                return true;
            }
        }

        return false;
    }

    public static boolean validSkinUrl(String str) {
        Optional<URL> uriOptional = SkinUtils.parseURL(str);
        return uriOptional.isPresent() && (uriOptional.get().getProtocol().equals("http") || uriOptional.get().getProtocol().equals("https"));
    }
}