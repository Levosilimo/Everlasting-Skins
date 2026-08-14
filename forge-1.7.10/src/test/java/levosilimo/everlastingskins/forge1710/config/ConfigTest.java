/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge1710.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 1.7.10 Config load/parse round-trip + defaults, mirroring the mc1.12.2
 * ConfigTest template (same Forge {@code Configuration} file format).
 */
public class ConfigTest {

    /**
     * 1.7.10 quirk: the Forge {@code Configuration} constructor computes the
     * config's relative name against {@code FMLInjectionData.data()[6]} (the
     * FML-injected MC home, populated only at FML launch). Outside an
     * FML-booted JVM that slot is null and the constructor NPEs — backfill
     * the package-private {@code minecraftHome} static via reflection so
     * load() is exercisable. No-op in production (FML sets it at launch).
     */
    @BeforeClass
    public static void injectFmlHome() throws Exception {
        Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        if (home.get(null) == null) {
            home.set(null, new File(System.getProperty("java.io.tmpdir")));
        }
    }

    @After
    public void restoreDefaults() {
        Config.LANGUAGE = "en";
        Config.urlAllowlistEnabled = false;
        Config.urlAllowlistDomains = new String[]{
            "imgur.com", "storage.googleapis.com", "cdn.discordapp.com",
            "textures.minecraft.net", "namemc.com", "crafatar.com",
            "mc-heads.net", "githubusercontent.com", "minecraftskins.com"
        };
        Config.metricsEnabled = true;
        Config.metricsDumpIntervalSeconds = 60;
        Config.mojangProfileCacheEnabled = true;
        Config.mojangProfileCacheTtlMs = 3600000L;
        Config.mojangProfileCacheMaxSize = 1000;
    }

    @Test
    public void load_readsKeysFromCfgFile() throws Exception {
        File cfg = File.createTempFile("everlastingskins-config-test", ".cfg");
        cfg.deleteOnExit();
        String content = "security {\n"
            + "    B:urlAllowlistEnabled=true\n"
            + "    S:urlAllowlistDomains <\n"
            + "        imgur.com\n"
            + "        example.com\n"
            + "     >\n"
            + "}\n"
            + "mojangcache {\n"
            + "    I:mojangProfileCacheTtlMs=5000\n"
            + "    I:mojangProfileCacheMaxSize=50\n"
            + "    B:mojangProfileCacheEnabled=false\n"
            + "}\n"
            + "messages {\n"
            + "    S:localization=ru\n"
            + "}\n"
            + "everlastingskins {\n"
            + "    B:metricsEnabled=false\n"
            + "    I:metricsDumpIntervalSeconds=120\n"
            + "}\n";
        Files.write(cfg.toPath(), content.getBytes());

        Config.load(cfg);

        assertTrue(Config.urlAllowlistEnabled);
        assertArrayEquals(new String[]{"imgur.com", "example.com"}, Config.urlAllowlistDomains);
        assertFalse(Config.mojangProfileCacheEnabled);
        assertEquals(5000L, Config.mojangProfileCacheTtlMs);
        assertEquals(50, Config.mojangProfileCacheMaxSize);
        assertEquals("ru", Config.LANGUAGE);
        assertFalse(Config.metricsEnabled);
        assertEquals(120, Config.metricsDumpIntervalSeconds);
    }

    @Test
    public void load_missingFileWritesDefaults() throws Exception {
        File cfg = File.createTempFile("everlastingskins-config-defaults", ".cfg");
        cfg.deleteOnExit();

        Config.load(cfg);

        assertEquals("en", Config.LANGUAGE);
        assertFalse(Config.urlAllowlistEnabled);
        assertEquals(9, Config.urlAllowlistDomains.length);
        assertTrue(Config.metricsEnabled);
        assertEquals(60, Config.metricsDumpIntervalSeconds);
        assertTrue(Config.mojangProfileCacheEnabled);
        assertEquals(3600000L, Config.mojangProfileCacheTtlMs);
        assertEquals(1000, Config.mojangProfileCacheMaxSize);
        // Defaults were persisted to disk (hasChanged -> save).
        assertTrue(cfg.length() > 0);
    }

    @Test
    public void defaults_matchReferenceLane() {
        assertFalse(Config.urlAllowlistEnabled);
        List<String> domains = Arrays.asList(Config.urlAllowlistDomains);
        assertTrue(domains.contains("imgur.com"));
        assertTrue(domains.contains("textures.minecraft.net"));
        assertTrue(domains.contains("namemc.com"));
        assertTrue(domains.contains("mc-heads.net"));
        assertEquals(9, domains.size());
        assertEquals(60, Config.metricsDumpIntervalSeconds);
        assertEquals(3600000L, Config.mojangProfileCacheTtlMs);
    }
}
