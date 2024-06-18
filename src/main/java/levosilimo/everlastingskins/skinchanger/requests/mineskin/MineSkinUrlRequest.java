package levosilimo.everlastingskins.skinchanger.requests.mineskin;

import levosilimo.everlastingskins.enums.SkinVariant;

import javax.annotation.Nullable;

public class MineSkinUrlRequest {
    private final @Nullable SkinVariant variant;
    private final @Nullable String name;
    private final @Nullable Integer visibility;
    private final @Nullable String url;
}
