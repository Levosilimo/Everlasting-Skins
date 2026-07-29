package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;

public final class MineSkinFeatureFlag {
    public static boolean isEnabled() { return Config.MINESKIN_ENABLED; }
    private MineSkinFeatureFlag() {}
}
