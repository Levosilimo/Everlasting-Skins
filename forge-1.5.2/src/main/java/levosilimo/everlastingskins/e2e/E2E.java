/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Single entry point for the in-jar E2E support, invoked from
 * {@code EverlastingSkins.init()} (one line: {@code E2E.install();}).
 *
 * <p>Shipped-gated by {@code -Deverlastingskins.e2e=true} (never active in
 * production); side-gated afterwards:
 * <ul>
 *   <li>CLIENT → {@link E2EDriver} (phase machine, channel receiver,
 *       auto-connect, renderer assertion, result file + {@code System.exit});</li>
 *   <li>SERVER → {@link E2ESentinelHook} (sentinel pre-seed so the login
 *       re-broadcast delivers it).</li>
 * </ul>
 */
public final class E2E {

    /** System property the e2e scripts set on both JVMs. */
    public static final String E2E_PROPERTY = "everlastingskins.e2e";

    private E2E() {}

    /** One-line gated entry from the mod's {@code @Mod.Init init}. */
    public static void install() {
        if (!Boolean.getBoolean(E2E_PROPERTY)) {
            return;
        }
        Side side = FMLCommonHandler.instance().getSide();
        if (side == Side.CLIENT) {
            E2EDriver.install();
        } else if (side == Side.SERVER) {
            E2ESentinelHook.install();
        }
    }
}
