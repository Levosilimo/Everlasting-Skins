/*
 * SPDX-License-Identifier: MIT
 */

package levosilimo.everlastingskins.skinchanger.responses.profile;

public record MineToolsProfileResponse(Raw raw) {

    public record Raw (String id, String name, PropertyResponse[] properties, String status) {
    }

}
