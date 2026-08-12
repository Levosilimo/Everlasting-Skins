/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge102;

import levosilimo.everlastingskins.command.SkinRestorerCommand;
import levosilimo.everlastingskins.skinchanger.SkinCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mod-level registration regression: {@code serverStarting} must register
 * both the {@code /everlastingskins} admin command (a lane extra, absent
 * from the 1.21 reference) and the {@code /skin} command. Deterministic
 * mocks only — no live server.
 *
 * <p>The static Forge event bus cannot be touched in a plain JUnit run
 * (FML's EventBus.register resolves the LaunchClassLoader, which only exists
 * under LaunchWrapper), so the test swaps {@code MinecraftForge.EVENT_BUS}
 * for a mock for the duration and restores it afterwards.
 */
class EverlastingSkinsRegistrationTest {

    @TempDir
    Path tempDir;

    private EventBus originalBus;

    @BeforeEach
    void replaceEventBus() throws Exception {
        Field bus = MinecraftForge.class.getDeclaredField("EVENT_BUS");
        bus.setAccessible(true);
        // Drop the final modifier BEFORE the first get/set: the reflection
        // accessor caches isFinal at construction, so clearing afterwards is
        // too late (Java 8 idiom; the field is restored in tearDown).
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(bus, bus.getModifiers() & ~Modifier.FINAL);
        originalBus = (EventBus) bus.get(null);
        bus.set(null, mock(EventBus.class));
    }

    @AfterEach
    void restoreEventBus() throws Exception {
        Field bus = MinecraftForge.class.getDeclaredField("EVENT_BUS");
        bus.setAccessible(true);
        bus.set(null, originalBus);
    }

    @Test
    void serverStartingRegistersRestorerAndSkinCommands() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getFile(anyString())).thenReturn(tempDir.toFile());
        FMLServerStartingEvent event = mock(FMLServerStartingEvent.class);
        when(event.getServer()).thenReturn(server);

        EverlastingSkins.instance = new EverlastingSkins();
        EverlastingSkins.instance.serverStarting(event);

        verify(event).registerServerCommand(any(SkinRestorerCommand.class));
        verify(event).registerServerCommand(any(SkinCommand.class));
    }
}
