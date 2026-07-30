package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

public class SkinCommand extends CommandBase {
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
    private static final String PREFIX = "§6[" + EverlastingSkins.MOD_NAME + "]§f ";

    public static final MineSkinAPI mineSkinAPI = new MineSkinApiHttpImpl();
    public static final MojangAPI mojangAPI = new MojangApiHttpImpl();

    @Override
    public String getName() {
        return "skin";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/skin <set|clear|source> ...";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("eskin");
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            sender.sendMessage(new TextComponentString(PREFIX + getUsage(sender)));
            return;
        }
        switch (args[0]) {
            case "clear":  doClear(server, sender, args); break;
            case "source": doSource(server, sender, args); break;
            case "set":    doSet(server, sender, args); break;
            default:
                sender.sendMessage(new TextComponentString(PREFIX + getUsage(sender)));
        }
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) return Arrays.asList("set", "clear", "source");
        return Collections.emptyList();
    }

    public static void register(MinecraftServer server) {
        ((CommandHandler) server.getCommandManager()).registerCommand(new SkinCommand());
    }

    private void doClear(MinecraftServer server, ICommandSender sender, String[] args) {
        Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, true, 2);
        String node = targets.size() == 1 && targets.iterator().next() == sender
            ? "everlastingskins.command.skin.clear"
            : "everlastingskins.command.skin.other";
        if (!checkPermission(sender, node)) return;
        applySkinChange(targets, sender, SkinActionType.clear, SkinVariant.ALL, false, null);
    }

    private void doSource(MinecraftServer server, ICommandSender sender, String[] args) {
        EntityPlayerMP target;
        try {
            target = args.length >= 2
                ? CommandBase.getPlayer(server, sender, args[1])
                : CommandBase.getCommandSenderAsPlayer(sender);
        } catch (CommandException e) {
            sender.sendMessage(new TextComponentString(PREFIX + e.getMessage()));
            return;
        }
        if (args.length >= 2) {
            if (!checkPermission(sender, "everlastingskins.command.skin.other")) return;
        }
        UUID uuid = target.getUniqueID();
        if (SkinRestorer.getSkinStorage().hasDefaultSkin(uuid)) {
            sender.sendMessage(new TextComponentString(PREFIX + target.getGameProfile().getName()));
            return;
        }
        String source = SkinRestorer.getSkinStorage().getSource(uuid);
        sender.sendMessage(new TextComponentString(PREFIX + (source != null ? source : "No source available")));
    }

    private void doSet(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(PREFIX + getUsage(sender)));
            return;
        }
        switch (args[1]) {
            case "mojang": {
                if (args.length < 3) {
                    sender.sendMessage(new TextComponentString(PREFIX + "Usage: /skin set mojang <name>"));
                    return;
                }
                Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, false, 4);
                String node = targets.size() == 1 && targets.iterator().next() == sender
                    ? "everlastingskins.command.skin"
                    : "everlastingskins.command.skin.other";
                if (!checkPermission(sender, node)) return;
                applySkinChange(targets, sender, SkinActionType.username, SkinVariant.ALL, false, args[2]);
                break;
            }
            case "web": {
                if (!Config.MINESKIN_ENABLED) {
                    sender.sendMessage(new TextComponentString(PREFIX + "MineSkin is disabled in config"));
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(new TextComponentString(PREFIX + "Usage: /skin set web <classic|slim> <url>"));
                    return;
                }
                SkinVariant variant = "slim".equalsIgnoreCase(args[2]) ? SkinVariant.SLIM : SkinVariant.CLASSIC;
                Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, false, 5);
                String node = targets.size() == 1 && targets.iterator().next() == sender
                    ? "everlastingskins.command.skin.url"
                    : "everlastingskins.command.skin.other";
                if (!checkPermission(sender, node)) return;
                applySkinChange(targets, sender, SkinActionType.url, variant, false, args[3]);
                break;
            }
            case "random":
                Collection<EntityPlayerMP> targets = parseTargets(server, sender, args, false, 3);
                String node = targets.size() == 1 && targets.iterator().next() == sender
                    ? "everlastingskins.command.skin"
                    : "everlastingskins.command.skin.other";
                if (!checkPermission(sender, node)) return;
                applySkinChange(targets, sender, SkinActionType.random, SkinVariant.ALL, false, null);
                break;
            default:
                sender.sendMessage(new TextComponentString(PREFIX + "Usage: /skin set <mojang|web|random>"));
        }
    }

    private boolean checkPermission(ICommandSender sender, String node) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            PermissionContext ctx = PermissionContext.of(player.getUniqueID(), player.canUseCommand(2, "everlastingskins"));
            if (!PermissionServiceManager.hasPermission(ctx, node)) {
                sender.sendMessage(new TextComponentString(PREFIX + "Permission denied"));
                return false;
            }
        }
        return true;
    }

    private Collection<EntityPlayerMP> parseTargets(MinecraftServer server, ICommandSender sender,
            String[] args, boolean requiresOpAtLeastOne, int targetIndex) {
        if (args.length > targetIndex) {
            if (!checkPermission(sender, "everlastingskins.command.skin.other")) {
                return Collections.emptyList();
            }
            List<EntityPlayerMP> out = new ArrayList<>();
            for (int i = targetIndex; i < args.length; i++) {
                try {
                    out.add(CommandBase.getPlayer(server, sender, args[i]));
                } catch (CommandException ignored) {
                }
            }
            return out;
        }
        try {
            return Collections.singletonList(CommandBase.getCommandSenderAsPlayer(sender));
        } catch (CommandException e) {
            sender.sendMessage(new TextComponentString(PREFIX + e.getMessage()));
            return Collections.emptyList();
        }
    }

    private void applySkinChange(Collection<EntityPlayerMP> targets, ICommandSender sender,
            SkinActionType type, SkinVariant variant, boolean withCape, @Nullable String customSource) {
        for (EntityPlayerMP p : targets) {
            if (Config.TOGGLE) {
                p.sendMessage(new TextComponentString(PREFIX + "Processing..."));
            }
        }
        CompletableFuture<CustomSkinProperty> future = CompletableFuture.supplyAsync(() -> {
            CustomSkinProperty sp = null;
            try {
                switch (type) {
                    case clear:
                        String storedSrc = null;
                        String pName = null;
                        for (EntityPlayerMP t : targets) {
                            storedSrc = SkinRestorer.getSkinStorage().getSource(t.getUniqueID());
                            pName = t.getGameProfile().getName();
                            break;
                        }
                        MojangRestoreResult restore = tryRestoreFromMojang(mojangAPI, storedSrc, pName);
                        sp = restore != null ? restore.skin : null;
                        break;
                    case url: {
                        String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                        if (!sanitized.equals(customSource)) {
                            sp = mojangAPI.getSkin(sanitized)
                                .map(MojangSkinDataResult::skinProperty).orElse(null);
                        } else {
                            sp = MineSkinFeatureFlag.isEnabled()
                                ? mineSkinAPI.genSkin(customSource, variant).property()
                                : null;
                        }
                        break;
                    }
                    case username:
                        sp = mojangAPI.getSkin(customSource)
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        break;
                    case random:
                        sp = mojangAPI.getSkin(RandomMojangSkin.randomUsername(withCape, variant))
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        break;
                    case NEW:
                        sp = mojangAPI.getSkin(RandomMojangSkin.newUsername(variant))
                            .map(MojangSkinDataResult::skinProperty).orElse(null);
                        break;
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
            return sp;
        }, EXECUTOR);

        final ScheduledFuture<?> timeoutFuture = EXECUTOR.schedule(() -> {
            if (future.completeExceptionally(new TimeoutException("Skin fetch timeout"))) {
                EverlastingSkins.logger.error("Skin fetch timeout");
                for (EntityPlayerMP p : targets) {
                    p.sendMessage(new TextComponentString(PREFIX + "Skin fetch timeout"));
                }
            }
        }, 10, TimeUnit.SECONDS);

        future.whenComplete((sp, err) -> {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            if (err != null) {
                EverlastingSkins.logger.error("Skin process error", err);
                for (EntityPlayerMP p : targets) {
                    p.sendMessage(new TextComponentString(PREFIX + "Skin process error"));
                }
                return;
            }
            if (sp == null) {
                if (type != SkinActionType.clear) {
                    String reason = deriveReason(type, customSource);
                    EverlastingSkins.logger.warn("Skin provider returned no result: {}", reason);
                    for (EntityPlayerMP p : targets) {
                        p.sendMessage(new TextComponentString(PREFIX + reason));
                    }
                    return;
                }
                EverlastingSkins.logger.info("Skin cleared for player(s) — no Mojang profile found");
                for (EntityPlayerMP p : targets) {
                    SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), null);
                    if (Config.TOGGLE) {
                        p.sendMessage(new TextComponentString(PREFIX + "Skin cleared (no Mojang profile found)"));
                    }
                }
                for (EntityPlayerMP p : targets) {
                    SkinRestorer.getServer().addScheduledTask(() -> task(p));
                }
                return;
            }
            boolean isRestore = (type == SkinActionType.clear);
            for (EntityPlayerMP p : targets) {
                SkinRestorer.getSkinStorage().setSkin(p.getUniqueID(), sp);
                if (Config.TOGGLE) {
                    String msg = isRestore
                        ? "Skin restored from " + sp.getSource()
                        : "Skin applied";
                    p.sendMessage(new TextComponentString(PREFIX + msg));
                }
            }
            for (EntityPlayerMP p : targets) {
                SkinRestorer.getServer().addScheduledTask(() -> task(p));
            }
        });
    }

    private void task(EntityPlayerMP player) {
        WorldServer world = player.getServerWorld();
        int dimension = player.dimension;
        net.minecraft.world.EnumDifficulty difficulty = world.getDifficulty();
        net.minecraft.world.WorldType terrainType = world.getWorldInfo().getTerrainType();
        net.minecraft.world.GameType gameType = player.interactionManager.getGameType();
        PlayerList playerList = SkinRestorer.getServer().getPlayerList();

        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;

        SkinRestorer.getSkinStorage().saveSkin(player.getUniqueID());
        CustomSkinProperty skin = SkinRestorer.getSkinStorage().getSkin(player.getUniqueID());

        if (skin != null && !skin.isEmpty()) {
            player.getGameProfile().getProperties().removeAll("textures");
            player.getGameProfile().getProperties().put("textures", skin.getOriginalProperty());

            playerList.sendPacketToAllPlayers(new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, player));
            playerList.sendPacketToAllPlayers(new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, player));
        }

        player.connection.sendPacket(new SPacketRespawn(dimension, difficulty, terrainType, gameType));
        player.connection.sendPacket(new SPacketServerDifficulty(difficulty, world.getWorldInfo().isDifficultyLocked()));
        player.connection.sendPacket(new SPacketPlayerPosLook(x, y, z, yaw, pitch,
            EnumSet.noneOf(SPacketPlayerPosLook.EnumFlags.class), 0));
        player.connection.sendPacket(new SPacketPlayerAbilities(player.capabilities));

        player.setPositionAndRotation(x, y, z, yaw, pitch);
    }

    @Nullable
    static MojangRestoreResult tryRestoreFromMojang(MojangAPI mojangAPI, @Nullable String storedSource, String playerName) {
        String licensedUsername = (storedSource != null && !storedSource.trim().isEmpty())
            ? storedSource : playerName;
        CustomSkinProperty skin = mojangAPI.getSkin(licensedUsername)
            .map(MojangSkinDataResult::skinProperty)
            .filter(s -> !s.isEmpty())
            .orElse(null);
        if (skin == null) return null;
        return new MojangRestoreResult(skin, licensedUsername);
    }

    static class MojangRestoreResult {
        final CustomSkinProperty skin;
        final String licensedUsername;

        MojangRestoreResult(CustomSkinProperty skin, String licensedUsername) {
            this.skin = skin;
            this.licensedUsername = licensedUsername;
        }
    }

    private static String deriveReason(SkinActionType type, @Nullable String customSource) {
        switch (type) {
            case username:
                return customSource != null
                    ? "No skin found for \"" + customSource + "\""
                    : "No skin found";
            case url: {
                if (customSource != null) {
                    String sanitized = EverlastingHelpers.sanitizeSkinInput(customSource);
                    if (!sanitized.equals(customSource)) {
                        return "No skin found for \"" + sanitized + "\"";
                    }
                }
                return "MineSkin rejected the URL";
            }
            case random:
            case NEW:
                return "No random username available";
            default:
                return "Provider returned no result";
        }
    }
}
