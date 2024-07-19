package levosilimo.everlastingskins.skinchanger.requests.mineskin;

import levosilimo.everlastingskins.enums.SkinVariant;


public record MineSkinUrlRequest(SkinVariant variant, String name, Integer visibility, String url) {
}
