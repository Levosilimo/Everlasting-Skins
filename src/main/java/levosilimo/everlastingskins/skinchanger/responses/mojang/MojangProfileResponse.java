package levosilimo.everlastingskins.skinchanger.responses.mojang;

import levosilimo.everlastingskins.skinchanger.responses.profile.PropertyResponse;

import java.util.Objects;

public final class MojangProfileResponse {
    private final String id;
    private final String name;
    private final PropertyResponse[] properties;

    MojangProfileResponse(String id, String name, PropertyResponse[] properties) {
        this.id = id;
        this.name = name;
        this.properties = properties;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public PropertyResponse[] properties() {
        return properties;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MojangProfileResponse that = (MojangProfileResponse) obj;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, properties);
    }

    @Override
    public String toString() {
        return "MojangProfileResponse[" +
                "id=" + id + ", " +
                "name=" + name + ", " +
                "properties=" + properties + ']';
    }

}
