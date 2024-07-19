package levosilimo.everlastingskins.skinchanger.responses.mojang;

import levosilimo.everlastingskins.skinchanger.responses.profile.PropertyResponse;

public record MojangProfileResponse(String id, String name, PropertyResponse[] properties) {
}
