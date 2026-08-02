package levosilimo.everlastingskins.integration;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Race contract: two concurrent /skin set commands for the same player must
 * not throw; storage ends with one of the two skins and the GameProfile holds
 * exactly one textures property (last write wins, single-entry map).
 */
class ConcurrentSetIT {

    @TempDir
    Path tempDir;

    private TestServerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestServerContext(tempDir);
    }

    @AfterEach
    void tearDown() {
        SkinCommandTestAccess.resetAPIs();
        ctx.close();
    }

    @Test
    void concurrentSet_consistentState() throws Exception {
        SkinCommandTestAccess.setMojangAPI(
            new FakeMojangAPI(TestProperties.NOTCH, TestProperties.DINNERBONE));
        EntityPlayerMP alice = ctx.newPlayer("Alice");
        ctx.makeOp(alice);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(() ->
                ctx.commandManager.executeCommand(alice, "/skin set mojang Notch"));
            Future<?> f2 = pool.submit(() ->
                ctx.commandManager.executeCommand(alice, "/skin set mojang Dinnerbone"));
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertTrue(AsyncSupport.await(5000, () -> ctx.storage.getSkin(alice.getUniqueID()) != null),
                "skin should be stored after the async applies complete");
            assertTrue(AsyncSupport.await(5000,
                () -> alice.getGameProfile().getProperties().get("textures").size() == 1),
                "profile should settle to exactly one textures property");

            CustomSkinProperty finalSkin = ctx.storage.getSkin(alice.getUniqueID());
            assertNotNull(finalSkin);
            String source = finalSkin.getSource();
            assertTrue("Notch".equals(source) || "Dinnerbone".equals(source),
                "storage must hold one of the two raced skins, was " + source);
            assertEquals(1, alice.getGameProfile().getProperties().get("textures").size());

            // Disk state must settle to the same winner as memory (async flush
            // drains in waves; the last enqueued payload wins on disk too).
            Path skinFile = tempDir.resolve("EverlastingSkins").resolve(alice.getUniqueID() + ".json");
            assertTrue(AsyncSupport.await(5000, () -> {
                if (!skinFile.toFile().exists()) return false;
                try {
                    return source.equals(readSource(skinFile));
                } catch (Exception e) {
                    return false;
                }
            }), "on-disk source must settle to the in-memory winner");
        } finally {
            pool.shutdownNow();
        }
    }

    private static String readSource(Path skinFile) throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(skinFile), java.nio.charset.StandardCharsets.UTF_8);
        return new com.google.gson.JsonParser().parse(json).getAsJsonObject().get("source").getAsString();
    }
}
