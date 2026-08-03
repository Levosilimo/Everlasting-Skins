/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.integration.discordsrv;

import levosilimo.everlastingskins.util.I18nUtils;
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

            String message = formatAnnounce(player, skinSource);

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

    /**
     * Builds the localized announcement text for a player. Routes through the
     * per-player locale (AT-exposed EntityPlayerMP.language) so non-English
     * players get their own language in the Discord channel; falls back to
     * Config.LANGUAGE for a null player. Package-private for direct testing.
     */
    static String formatAnnounce(EntityPlayerMP player, String skinSource) {
        String label = skinSource != null ? skinSource : "default";
        String name = player != null ? player.getDisplayNameString() : "";
        return I18nUtils.formatMessage("discord_announce", player, name, label);
    }
}
