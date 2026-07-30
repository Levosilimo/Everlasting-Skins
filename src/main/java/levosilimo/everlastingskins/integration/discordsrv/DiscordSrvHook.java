package levosilimo.everlastingskins.integration.discordsrv;

import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DiscordSrvHook {
    private static final Logger LOGGER = LogManager.getLogger();
    public static void announceSkinChange(ServerPlayer player, String skinSource) {
        try {
            Class.forName("github.scarsz.discordsrv.DiscordSRV").getMethod("getPlugin").invoke(null);
            LOGGER.info("[DiscordSRV] {} changed their skin to: {}", player.getScoreboardName(), skinSource != null ? skinSource : "default");
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.debug("DiscordSRV not present; skipping");
        }
    }
}
