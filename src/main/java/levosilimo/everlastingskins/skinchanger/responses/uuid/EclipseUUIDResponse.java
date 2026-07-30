/*
 * SPDX-License-Identifier: MIT
 */

package levosilimo.everlastingskins.skinchanger.responses.uuid;

import levosilimo.everlastingskins.skinchanger.responses.EclipseCacheData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record EclipseUUIDResponse(EclipseCacheData cacheData, boolean exists, @Nullable UUID uuid) {
  }
