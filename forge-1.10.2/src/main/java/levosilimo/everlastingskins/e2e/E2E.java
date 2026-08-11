/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Single entry point for the in-jar E2E support, invoked from
 * {@code EverlastingSkins.init()} (one line: {@code E2E.install();}).
 *
 * <p>Shipped-gated by {@code -Deverlastingskins.e2e=true} (never active in
 * production); side-gated afterwards:
 * <ul>
 *   <li>CLIENT → {@link E2EDriver} (phase machine, sends the /skin command
 *       via the real chat surface, writes the result file + exit);</li>
 *   <li>SERVER → nothing (the server-side {@code ES_E2E_SKIN} sentinel is
 *       emitted by {@code SkinAction} under the same property gate).</li>
 * </ul>
 *
 * <p>1.10.2 has no HeadlessMC specifics build (no {@code 1_10} module in
 * hmc-specifics), so this lane uses the in-jar driver route of the
 * master plan instead of the bridge (see
 * {@code scripts/e2e/drivers/headlessmc.sh}).
 */
public final class E2E {

    /** System property the e2e scripts set on both JVMs. */
    public static final String E2E_PROPERTY = "everlastingskins.e2e";

    private E2E() {}

    /** One-line gated entry from the mod's {@code @Mod.EventHandler init}. */
    public static void install() {
        if (!Boolean.getBoolean(E2E_PROPERTY)) {
            return;
        }
        Side side = FMLCommonHandler.instance().getSide();
        if (side.isClient()) {
            E2EDriver.install();
        }
    }
}
