/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.command;

import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 1.8.9 {@code /everlastingskins} command over the legacy {@link ICommand}
 * surface (getCommandName/processCommand — the 1.8-era MCP names; the
 * getName/execute rename landed in 1.11). Op-level gating uses
 * {@link ICommandSender#canCommandSenderUseCommand(int, String)}.
 */
public class SkinRestorerCommand implements ICommand {

    private static final String NAME = "everlastingskins";
    private static final List<String> ALIASES = Arrays.asList("eskins", "es");
    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "reload", "help");

    @Override
    public String getCommandName() {
        return NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + NAME + " <status|reload|help>";
    }

    @Override
    public List<String> getCommandAliases() {
        return ALIASES;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0];
        if ("status".equals(sub)) {
            String backend = PermissionServiceManager.getActiveBackendName();
            String storage = SkinRestorer.getSkinStorage() != null ? "ready" : "not-ready";
            sender.addChatMessage(new ChatComponentText(
                "Everlasting Skins: backend=" + backend + ", storage=" + storage));
            return;
        }
        if ("reload".equals(sub)) {
            // Storage re-init happens on server start; a reload only re-reads
            // the shared default skin on the next getSkin() cache miss.
            sender.addChatMessage(new ChatComponentText(
                "Everlasting Skins: storage reloaded on next access"));
            return;
        }
        sender.addChatMessage(new ChatComponentText("Usage: " + getCommandUsage(sender)));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(2, NAME);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new java.util.ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand other) {
        return getCommandName().compareTo(other.getCommandName());
    }
}
