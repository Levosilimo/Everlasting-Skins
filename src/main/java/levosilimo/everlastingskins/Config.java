package levosilimo.everlastingskins;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class Config {
    public static String LANGUAGE = "en";
    public static boolean TOGGLE = true;
    public static String MINESKIN_API_KEY = "";
    public static boolean MINESKIN_ENABLED = false; // OFF for Phase 5 viability gate.

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();
            LANGUAGE = cfg.getString("localization", "Messages", LANGUAGE, "Language of mod messages");
            TOGGLE = cfg.getBoolean("display", "Messages", TOGGLE, "Display mod messages");
            MINESKIN_API_KEY = cfg.getString("key", "Messages", MINESKIN_API_KEY, "Mineskin api key");
            MINESKIN_ENABLED = cfg.getBoolean("enabled", "MineSkin", false,
                "Enable MineSkin URL-based skin generation (off during Phase 5 viability gate)");
        } catch (Exception e) {
            EverlastingSkins.logger.error("Failed to load config", e);
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
    }

    private Config() {}
}
