/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MineSkin feature-flag path: with MINESKIN_ENABLED the /skin set web command
 * routes to MineSkinAPI instead of rejecting the URL.
 */
class MineSkinPathIT {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
    }

    @AfterEach
    void tearDown() {
        Config.MINESKIN_ENABLED = false;
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void mineSkin_whenEnabled_usesMineSkinAPI() {
        Config.MINESKIN_ENABLED = true;
        FakeMineSkinAPI fake = new FakeMineSkinAPI(TestProperties.ALEX);
        SkinCommandTestAccess.setMineSkinAPI(fake);
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);

        ctx.commandManager.executeCommand(alice, "/skin set web classic http://example.com/skin.png");

        assertTrue(AsyncSupport.await(5000, () -> fake.calls() >= 1),
            "MineSkinAPI should be invoked by the async apply");
        assertEquals("http://example.com/skin.png", fake.lastUrl());
        assertEquals(SkinVariant.CLASSIC, fake.lastVariant());
        assertEquals(1, fake.calls());
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "skin should be stored after the async apply completes");
        CustomSkinProperty stored = ctx.storage.getSkin(alice.getUniqueID());
        assertNotNull(stored);
        assertEquals("Alex", stored.getSource());
    }
}
