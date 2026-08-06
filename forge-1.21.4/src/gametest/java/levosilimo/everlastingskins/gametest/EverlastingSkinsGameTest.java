/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.gametest;

import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only mod container so the game test classes form a proper mod file:
 * FML requires a @Mod class for a javafml mods.toml, and the game test
 * framework registers tests from mod file scan data.
 */
@Mod(EverlastingSkinsGameTest.MOD_ID)
public class EverlastingSkinsGameTest {
    public static final String MOD_ID = "everlastingskins_gametest";

    public EverlastingSkinsGameTest() {
    }
}
