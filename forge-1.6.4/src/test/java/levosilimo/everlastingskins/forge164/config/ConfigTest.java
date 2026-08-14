/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.forge164.config;

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
 * 1.6.4 Config load/parse round-trip + defaults, mirroring the forge-1.7.10
 * ConfigTest (PR #477) off the mc1.12.2 template. The .cfg fixture uses the
 * era's lowercase categories (the 1.6.4 {@code Configuration} lowercases on
 * lookup, same quirk as 1.7.10) and the B:/S:/I: property-prefix format
 * (Property.Type.getID() = first letter, unchanged since 1.6).
 */
public class ConfigTest {

    /**
     * 1.6.4 quirk: the Forge {@code Configuration} constructor computes the
     * config's relative name against {@code FMLInjectionData.data()[6]} (the
     * FML-injected MC home, populated only at FML launch). Outside an
     * FML-booted JVM that slot is null and the constructor NPEs — backfill
     * the package-private static {@code minecraftHome} field via reflection
     * so load() is exercisable. 1.6.4's FMLInjectionData lives in
     * {@code cpw.mods.fml.relauncher} (the 1.7+ {@code cpw.mods.fml.common}
     * home came later — verified against the vendored 9.11.1.1345 tree).
     * No-op in production (FML sets it at launch).
     *
     * <p>Second 1.6.4 quirk (absent on 1.7+): this era's Configuration has
     * a STATIC field sized off {@code Item.itemsList.length} (original
     * source line 49 of forge/common/.../Configuration.java in the vendored
     * src zip), so merely loading the class initializes the whole MC item/
     * block registry. Item's static init is cyclic with Block's (Item →
     * ItemSpade → Block → StatList → AchievementList → ItemStack reads
     * Item.ingotIron, which is still null while Item is suspended), so the
     * JVM's class-init order decides the outcome: triggering Item first
     * NPEs (the observed failure), triggering Block first lets Item run to
     * completion inside the chain (the real server-boot order). Forcing
     * {@code Block} init below reproduces the boot order and the cycle
     * resolves re-entrantly.
     */
    @BeforeClass
    public static void injectFmlHome() throws Exception {
        Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        if (home.get(null) == null) {
            home.set(null, new File(System.getProperty("java.io.tmpdir")));
        }
        // Boot-order registry init: Block must complete before Item (see above).
        Class.forName("net.minecraft.src.Block");
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
