package levosilimo.everlastingskins.skinchanger.responses.uuid;

import java.util.Objects;

public final class MojangUUIDResponse {
    private final String name;
    private final String id;
    private final String error;
    private final String errorMessage;

    public MojangUUIDResponse(String name, String id, String error, String errorMessage) {
        this.name = name;
        this.id = id;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public String name() {
        return name;
    }

    public String id() {
        return id;
    }

    public String error() {
        return error;
    }

    public String errorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MojangUUIDResponse that = (MojangUUIDResponse) o;
        return Objects.equals(name, that.name) && Objects.equals(id, that.id)
            && Objects.equals(error, that.error) && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, error, errorMessage);
    }

    @Override
    public String toString() {
        return "MojangUUIDResponse[name=" + name + ", id=" + id + ", error=" + error + ", errorMessage=" + errorMessage + "]";
    }
}
