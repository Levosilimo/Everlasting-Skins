package levosilimo.everlastingskins.skinchanger.responses.mojang;

import levosilimo.everlastingskins.util.CustomSkinProperty;

import java.util.UUID;

public record MojangSkinDataResult(UUID uniqueId, CustomSkinProperty skinProperty) {
}
