package levosilimo.everlastingskins.util;

import com.mojang.authlib.properties.Property;

import javax.annotation.Nullable;
import java.util.Objects;

public class CustomSkinProperty {
    private final String source;
    private final Property originalProperty;
    private static String defaultSkinValue;

    public static void setDefaultSkinValue(String value) {
        defaultSkinValue = value;
    }

    public CustomSkinProperty(final String name, final String value, final String signature, @Nullable final String source) {
        this.originalProperty = new Property(name, value, signature);
        this.source = source;
    }

    public CustomSkinProperty(final String value, final String signature, @Nullable final String source) {
        this.originalProperty = new Property("textures", value, signature);
        this.source = source;
    }

    public boolean isEmpty() {
        if (originalProperty == null) return true;
        String value = originalProperty.getValue();
        if (value == null || value.trim().isEmpty()) return true;
        if (defaultSkinValue != null && defaultSkinValue.equals(value)) return true;
        return false;
    }

    @Override
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

    public String getSource() {
        return source;
    }
}
