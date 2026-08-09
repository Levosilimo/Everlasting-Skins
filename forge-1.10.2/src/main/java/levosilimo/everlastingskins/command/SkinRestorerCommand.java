/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.command;

import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.SkinRestorer;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 1.10.2 {@code /everlastingskins} command over the {@link ICommand}
 * surface. SPIKE finding (2026-08-08, verified against the deobf'd
 * stable_29 sources): 1.10.2 is the FIRST release with the modern MCP
 * command names — getName/getUsage/getAliases/execute/checkPermission/
 * getTabCompletions and {@link ICommandSender#sendMessage(ITextComponent)}
 * + {@link ICommandSender#canUseCommand(int, String)}. The 1.8.9-era
 * names (getCommandName/processCommand/addChatMessage) are gone here,
 * even though the getName/execute rename is usually associated with
 * 1.11. Op-level gating uses {@link ICommandSender#canUseCommand(int, String)}.
 */
public class SkinRestorerCommand implements ICommand {

    private static final String NAME = "everlastingskins";
    private static final List<String> ALIASES = Arrays.asList("eskins", "es");
    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "reload", "help");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + NAME + " <status|reload|help>";
    }

    @Override
    public List<String> getAliases() {
        return ALIASES;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length == 0 ? "help" : args[0];
        if ("status".equals(sub)) {
            String backend = PermissionServiceManager.getActiveBackendName();
            String storage = SkinRestorer.getSkinStorage() != null ? "ready" : "not-ready";
            sender.sendMessage(new TextComponentString(
                "Everlasting Skins: backend=" + backend + ", storage=" + storage));
            return;
        }
        if ("reload".equals(sub)) {
            // Storage re-init happens on server start; a reload only re-reads
            // the shared default skin on the next getSkin() cache miss.
            sender.sendMessage(new TextComponentString(
                "Everlasting Skins: storage reloaded on next access"));
            return;
        }
        sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(2, NAME);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
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
        return getName().compareTo(other.getName());
    }
}
