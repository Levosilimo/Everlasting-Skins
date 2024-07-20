package levosilimo.everlastingskins.skinchanger.responses.profile;

import levosilimo.everlastingskins.skinchanger.responses.EclipseCacheData;

import javax.annotation.Nullable;
import java.util.Objects;

public final class EclipseProfileResponse {
    private final EclipseCacheData cacheData;
    private final boolean exists;

    public final SkinProperty skinProperty;

    EclipseProfileResponse(EclipseCacheData cacheData, boolean exists, @Nullable SkinProperty skinProperty) {
        this.cacheData = cacheData;
        this.exists = exists;
        this.skinProperty = skinProperty;
    }

    public EclipseCacheData cacheData() {
        return cacheData;
    }

    public boolean exists() {
        return exists;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        EclipseProfileResponse that = (EclipseProfileResponse) obj;
        return Objects.equals(this.cacheData, that.cacheData) &&
                this.exists == that.exists;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheData, exists);
    }

    @Override
    public String toString() {
        return "EclipseProfileResponse[" +
                "cacheData=" + cacheData + ", " +
                "exists=" + exists + ']';
    }

    public boolean isPropertyNull(){
        return this.skinProperty == null;
    }

    public final class SkinProperty {
        private final String value;
        private final String signature;

        SkinProperty(
                String value,
                String signature
        ) {
            this.value = value;
            this.signature = signature;
        }

        public String value() {
            return value;
        }

        public String signature() {
            return signature;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            SkinProperty that = (SkinProperty) obj;
            return Objects.equals(this.value, that.value) &&
                    Objects.equals(this.signature, that.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, signature);
        }

        @Override
        public String toString() {
            return "SkinProperty[" +
                    "value=" + value + ", " +
                    "signature=" + signature + ']';
        }

    }
}

