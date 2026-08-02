/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration;

import levosilimo.everlastingskins.FakeHttpClient;
import levosilimo.everlastingskins.harness.AsyncSupport;
import levosilimo.everlastingskins.harness.PacketLog;
import levosilimo.everlastingskins.harness.TestServerContext;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.skinchanger.RandomMojangSkin;
import levosilimo.everlastingskins.skinchanger.SkinCommandTestAccess;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EndpointsConfig;
import levosilimo.everlastingskins.util.HttpClient;
import levosilimo.everlastingskins.util.HttpsUrlConnectionHttpClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * clear / source / random command paths. RandomMojangSkin's HTTP client is
 * swapped for a canned responder so /skin set random stays offline.
 */
class ClearSourceRandomIT {

    private static final String RANDOM_HTML =
        "\n<span class=\"card-title green-text truncate\">Notch</span>\n"
            + "<span class=\"card-title green-text truncate\">Dinnerbone</span>\n"
            + "<span class=\"card-title green-text truncate\">Alex</span>\n";

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
    }

    @AfterEach
    void tearDown() {
        injectRandomHttpClient(new HttpsUrlConnectionHttpClient());
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void clear_restoresMojangSkin() {
        FakeMojangAPI fake = new FakeMojangAPI(TestProperties.NOTCH);
        SkinCommandTestAccess.setMojangAPI(fake);
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);
        PacketLog log = new PacketLog();
        log.attachTo(alice.connection);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "skin should be stored after the async apply completes");

        ctx.commandManager.executeCommand(alice, "/skin clear");
        // Clear re-fetches the Mojang profile (second lookup) before applying.
        assertTrue(AsyncSupport.await(5000, () -> fake.lookupCount("Notch") >= 2),
            "clear should restore from Mojang asynchronously");

        CustomSkinProperty afterClear = ctx.storage.getSkin(alice.getUniqueID());
        assertNotNull(afterClear);
        assertEquals("Notch", afterClear.getSource());
        // The restored Mojang skin is byte-identical to the stored one, so the
        // refresh is skipped and recorded on the refreshSkipped counter.
        assertTrue(AsyncSupport.await(5000, () -> SkinMetrics.INSTANCE.snapshot().refreshesSkipped() >= 1),
            "identical restore should be skipped and counted; chats=" + chatsText(log));
    }

    @Test
    void source_reportsCurrentSource() {
        SkinCommandTestAccess.setMojangAPI(new FakeMojangAPI(TestProperties.NOTCH));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);
        PacketLog log = new PacketLog();
        log.attachTo(alice.connection);

        ctx.commandManager.executeCommand(alice, "/skin set mojang Notch");
        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "skin should be stored after the async apply completes");
        // The apply pipeline sends "Skin applied" as its last step; await it so
        // it cannot land after log.clear() and inflate the /skin source count.
        assertTrue(AsyncSupport.await(5000, () -> log.ofType(SPacketChat.class).stream()
                .anyMatch(c -> c.getChatComponent().getUnformattedText().contains("Skin applied"))),
            "apply pipeline must finish with the 'Skin applied' chat before clear");
        log.clear();

        ctx.commandManager.executeCommand(alice, "/skin source");

        List<SPacketChat> chats = log.ofType(SPacketChat.class);
        assertEquals(1, chats.size());
        assertTrue(chats.get(0).getChatComponent().getUnformattedText().contains("Notch"));
    }

    @Test
    void random_setsARandomSkin() {
        FakeMojangAPI fake = new FakeMojangAPI(
            TestProperties.NOTCH, TestProperties.DINNERBONE, TestProperties.ALEX);
        SkinCommandTestAccess.setMojangAPI(fake);
        FakeHttpClient http = new FakeHttpClient();
        http.addResponse(URI.create(EndpointsConfig.getString("url.mskins.random")), 200, RANDOM_HTML);
        injectRandomHttpClient(http);
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);
        PacketLog log = new PacketLog();
        log.attachTo(alice.connection);

        ctx.commandManager.executeCommand(alice, "/skin set random");

        assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
            "skin should be stored after the async apply completes; chats=" + chatsText(log));
        CustomSkinProperty stored = ctx.storage.getSkin(alice.getUniqueID());
        assertNotNull(stored);
        // The canned mskins.net page always yields Notch first.
        assertEquals("Notch", stored.getSource());
        assertTrue(fake.lookupCount("Notch") >= 1);
    }

    private static String chatsText(PacketLog log) {
        return log.ofType(SPacketChat.class).stream()
            .map(c -> c.getChatComponent().getUnformattedText())
            .reduce("", (a, b) -> a + " | " + b);
    }

    // RandomMojangSkin.httpClient is static final; Java 8 Field.set refuses it,
    // so the value is replaced through Unsafe (test-only seam).
    private static void injectRandomHttpClient(HttpClient client) {
        try {
            // Force class initialization first: writing to a not-yet-initialized
            // class is overwritten when static init runs later.
            Class.forName(RandomMojangSkin.class.getName(), true,
                RandomMojangSkin.class.getClassLoader());
            Field field = RandomMojangSkin.class.getDeclaredField("httpClient");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = theUnsafe();
            unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), client);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot stub RandomMojangSkin.httpClient", e);
        }
    }

    private static sun.misc.Unsafe theUnsafe() throws ReflectiveOperationException {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
