/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */


package levosilimo.everlastingskins.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Dev-only mod container so the game test classes form a proper mod file:
 * FML requires a @Mod class for a javafml mods.toml, and the game test
 * framework registers test functions from the TEST_FUNCTION registry.
 *
 * MC 1.21.5+ data-driven framework: the {@code @GameTest} methods are turned
 * into {@code Consumer<GameTestHelper>} entries of the vanilla
 * {@code minecraft:test_function} registry (via ForgeGameTestHooks), while the
 * test instances themselves are datapack entries in
 * {@code data/everlastingskins_gametest/test_instance/*.json} that reference
 * those function keys. The GameTestServer picks every non-manual-only
 * instance up from the loaded datapack at boot.
 *
 * 26.2 note (EventBus 7): the {@code @Mod.EventBusSubscriber} static-handler
 * path is gone — the RegisterEvent listener is attached explicitly to the
 * mod BusGroup's RegisterEvent bus in the constructor.
 */
@Mod(EverlastingSkinsGameTest.MOD_ID)
public class EverlastingSkinsGameTest {
    public static final String MOD_ID = "everlastingskins_gametest";

    public EverlastingSkinsGameTest() {
        BusGroup modBusGroup = FMLJavaModLoadingContext.get().getModBusGroup();
        RegisterEvent.getBus(modBusGroup).addListener(EverlastingSkinsGameTest::registerTestFunctions);
    }

    private static void registerTestFunctions(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
            return;
        }
        ForgeGameTestHooks.gatherTests(SkinVisibilityTest.class, new SkinVisibilityTest())
                .forEach((name, ref) -> event.register(Registries.TEST_FUNCTION, name, () -> ref.consumer()));
    }
}
