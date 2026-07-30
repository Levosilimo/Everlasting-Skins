package levosilimo.everlastingskins.integration.discordsrv;

import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Soft-integration hook for DiscordSRV.
 * <p>
 * All interactions are reflective so that the mod loads cleanly when DiscordSRV is absent.
 * Verified chain: {@code DiscordSRV.getPlugin().getJda()} then {@code JDA.getTextChannelById()}.
 */
public final class DiscordSrvHook {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DISCORDSRV_CLASS = "github.scarsz.discordsrv.DiscordSRV";

    public static void announceSkinChange(EntityPlayerMP player, String skinSource) {
        try {
            Class<?> dsClass = Class.forName(DISCORDSRV_CLASS);
            Object plugin = dsClass.getMethod("getPlugin").invoke(null);
            if (plugin == null) {
                LOGGER.debug("DiscordSRV plugin instance is null; skipping");
                return;
            }
            Object jda = plugin.getClass().getMethod("getJda").invoke(plugin);
            if (jda == null) {
                LOGGER.debug("DiscordSRV JDA instance is null (not connected); skipping");
                return;
            }

            String channelId = DiscordSrvConfig.getChannelId();
            if (channelId.isEmpty()) {
                LOGGER.debug("DiscordSRV channel ID not configured; skipping");
                return;
            }

            Class<?> jdaClass = Class.forName("net.dv8tion.jda.api.JDA");
            Class<?> textChannelClass = Class.forName("net.dv8tion.jda.api.entities.TextChannel");
            Object textChannel = jdaClass.getMethod("getTextChannelById", String.class).invoke(jda, channelId);
            if (textChannel == null) {
                LOGGER.warn("DiscordSRV channel {} not found", channelId);
                return;
            }

            String label = skinSource != null ? skinSource : "default";
            String message = String.format("**%s** changed their skin to: `%s`",
                player.getDisplayNameString(), label);

            Object messageAction = textChannelClass.getMethod("sendMessage", CharSequence.class)
                .invoke(textChannel, message);
            messageAction.getClass().getMethod("queue").invoke(messageAction);

            LOGGER.info("DiscordSRV announcement sent to channel {}: {}", channelId, message);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("DiscordSRV not present; skipping");
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.warn("DiscordSRV reflection chain failed: {}", e.getMessage());
        }
    }
}
