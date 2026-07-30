/*
 * SPDX-License-Identifier: MIT
 */
package levosilimo.everlastingskins.skinchanger.responses.profile;

import levosilimo.everlastingskins.skinchanger.responses.EclipseCacheData;
import org.jetbrains.annotations.Nullable;

public record EclipseProfileResponse(EclipseCacheData cacheData, boolean exists, @Nullable SkinProperty skinProperty) {

    public boolean isPropertyNull(){
        return this.skinProperty == null;
    }
    public record SkinProperty(
            String value,
            String signature
    ) {
    }
}
