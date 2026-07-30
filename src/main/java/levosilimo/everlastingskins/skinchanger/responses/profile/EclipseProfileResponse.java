/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.profile;

import levosilimo.everlastingskins.skinchanger.responses.EclipseCacheData;

import javax.annotation.Nullable;
import java.util.Objects;

public final class EclipseProfileResponse {
    private final EclipseCacheData cacheData;
    private final boolean exists;
    private final SkinProperty skinProperty;

    public EclipseProfileResponse(EclipseCacheData cacheData, boolean exists, @Nullable SkinProperty skinProperty) {
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

    @Nullable
    public SkinProperty skinProperty() {
        return skinProperty;
    }

    public boolean isPropertyNull() {
        return this.skinProperty == null;
    }

    public static final class SkinProperty {
        private final String value;
        private final String signature;

        public SkinProperty(String value, String signature) {
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SkinProperty that = (SkinProperty) o;
            return Objects.equals(value, that.value) && Objects.equals(signature, that.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, signature);
        }

        @Override
        public String toString() {
            return "SkinProperty[value=" + value + ", signature=" + signature + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EclipseProfileResponse that = (EclipseProfileResponse) o;
        return exists == that.exists && Objects.equals(cacheData, that.cacheData) && Objects.equals(skinProperty, that.skinProperty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheData, exists, skinProperty);
    }

    @Override
    public String toString() {
        return "EclipseProfileResponse[cacheData=" + cacheData + ", exists=" + exists + ", skinProperty=" + skinProperty + "]";
    }
}
