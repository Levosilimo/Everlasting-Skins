package levosilimo.everlastingskins.util;

import com.mojang.authlib.properties.Property;

import javax.annotation.Nullable;
import java.util.Objects;

public class CustomSkinProperty extends Property {
    private final String source;

    public CustomSkinProperty(final String name, final String value, final String signature, @Nullable final String source) {
        super(name, value, signature);
        this.source = source;
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

    public String getSource() {
        return source;
    }
}
