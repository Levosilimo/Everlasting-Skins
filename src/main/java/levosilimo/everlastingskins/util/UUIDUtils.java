/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import java.util.Optional;
import java.util.UUID;

public class UUIDUtils {
    public static Optional<UUID> tryParseUniqueId(String str) {
        try {
            return Optional.of(UUID.fromString(str));
        } catch (IllegalArgumentException ignored) {
            // If we have a non-dashed UUID, we can try to convert it to dashed.
            if (str.length() == 32) {
                try {
                    return Optional.of(convertToDashed(str));
                } catch (IllegalArgumentException ignored2) {
                    return Optional.empty();
                }
            }

            return Optional.empty();
        }
    }

    public static UUID convertToDashed(String noDashes) {
        StringBuilder idBuff = new StringBuilder(noDashes);
        idBuff.insert(20, '-');
        idBuff.insert(16, '-');
        idBuff.insert(12, '-');
        idBuff.insert(8, '-');
        return UUID.fromString(idBuff.toString());
    }

    public static String convertToNoDashes(UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
