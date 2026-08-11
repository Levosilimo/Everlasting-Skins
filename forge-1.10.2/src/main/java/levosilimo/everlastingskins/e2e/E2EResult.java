/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.e2e;

/**
 * Pure result-document constants for the real-client E2E (master-plan
 * contract, scripts/e2e/). Kept free of any Minecraft/FML import so the
 * lane's JUnit scaffold can test without a client.
 *
 * <p>The in-jar driver writes {@code e2e-result.json} into the client's
 * gameDir; {@code scripts/e2e/drivers/headlessmc.sh} merges the
 * server-side facts (server_booted, ES_E2E_SKIN) into the final document.
 */
public final class E2EResult {

    /** Result file name in the client gameDir (driver-side artifact). */
    public static final String FILE_NAME = "e2e-result.json";

    private E2EResult() {}
}
