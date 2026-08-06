/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.util;

import com.mojang.authlib.properties.Property;

import javax.annotation.Nullable;
import java.util.Objects;

public class CustomSkinProperty {
    private final String source;
    private final String username;
    private final Property originalProperty;
    private static String defaultSkinValue;

    /**
     * authlib {@code Property} exposes its payload under a different accessor
     * per major line (1.5.x {@code getValue()} class vs the 6.x record
     * {@code value()}), and Gson restores instances without running the
     * constructor. Read the backing field reflectively so both lines and
     * deserialized instances behave identically.
     */
    private static final java.lang.reflect.Field ORIGINAL_VALUE_FIELD = originalValueField();

    private static java.lang.reflect.Field originalValueField() {
        try {
            java.lang.reflect.Field field = Property.class.getDeclaredField("value");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static void setDefaultSkinValue(String value) {
        defaultSkinValue = value;
    }

    public CustomSkinProperty(final String name, final String value, final String signature, @Nullable final String source) {
        this(name, value, signature, source, null);
    }

    public CustomSkinProperty(final String value, final String signature, @Nullable final String source) {
        this("textures", value, signature, source, null);
    }

    /**
     * @param username the exact username the provider was asked for when this
     *                 skin was fetched; null for UUID-keyed lookups, MineSkin
     *                 skins, defaults and legacy persisted skins. Persisted by
     *                 Gson alongside {@code source}.
     */
    public CustomSkinProperty(final String name, final String value, final String signature,
                              @Nullable final String source, @Nullable final String username) {
        this.originalProperty = new Property(name, value, signature);
        this.source = source;
        this.username = username;
    }

    public boolean isEmpty() {
        if (originalProperty == null) return true;
        String value = getValue();
        if (value == null || value.trim().isEmpty()) return true;
        if (defaultSkinValue != null && defaultSkinValue.equals(value)) return true;
        return false;
    }

    /**
     * True when the textures value is present and decodes as base64. A corrupt
     * or truncated value would otherwise poison the profile and client renders.
     */
    public boolean isValid() {
        if (originalProperty == null) return false;
        String value = getValue();
        if (value == null || value.isEmpty()) return false;
        try {
            java.util.Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    @SuppressWarnings("EqualsGetClass") // deliberate: public non-final class, getClass() keeps subclass equality contract-safe
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomSkinProperty that = (CustomSkinProperty) o;
        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source);
    }

    public Property getOriginalProperty() {
        return originalProperty;
    }

    /**
     * The textures payload. Read from authlib's backing field rather than a
     * version-specific accessor: the accessor name differs across authlib
     * versions (1.5.x {@code getValue()} vs the 6.x record {@code value()}),
     * and Gson-restored instances never ran the constructor.
     */
    public String getValue() {
        if (originalProperty == null || ORIGINAL_VALUE_FIELD == null) return null;
        try {
            return (String) ORIGINAL_VALUE_FIELD.get(originalProperty);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public String getSource() {
        return source;
    }

    @Nullable
    public String getUsername() {
        return username;
    }
}
