package levosilimo.everlastingskins.skinchanger;

import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.EverlastingSkins;
import levosilimo.everlastingskins.enums.SkinActionType;
import levosilimo.everlastingskins.enums.SkinVariant;
import levosilimo.everlastingskins.permission.PermissionContext;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.skinchanger.responses.mojang.MojangSkinDataResult;
import levosilimo.everlastingskins.integration.discordsrv.DiscordSrvHook;
import levosilimo.everlastingskins.metrics.SkinMetrics;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import levosilimo.everlastingskins.util.EverlastingHelpers;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import net.minecraft.potion.PotionEffect;
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

    private static MineSkinAPI mineSkinAPI = new MineSkinApiHttpImpl();
    private static MojangAPI mojangAPI = new MojangApiHttpImpl();

    public static MineSkinAPI getMineSkinAPI() {
        return mineSkinAPI;
    }

    public static MojangAPI getMojangAPI() {
        return mojangAPI;
    }

    /* Package-private for tests: inject fakes without reflection. */
    static void setMineSkinAPI(MineSkinAPI api) {
        mineSkinAPI = api;
    }

    static void setMojangAPI(MojangAPI api) {
        mojangAPI = api;
    }

    static void resetAPIs() {
        mojangAPI = new MojangApiHttpImpl();
        mineSkinAPI = new MineSkinApiHttpImpl();
    }

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
                    SkinMetrics.INSTANCE.recordTimedOut(p.getUniqueID());
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
                    SkinRestorer.getServer().addScheduledTask(() -> task(p, null));
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
                SkinRestorer.getServer().addScheduledTask(() -> task(p, sp));
            }
            for (EntityPlayerMP p : targets) {
                try {
                    DiscordSrvHook.announceSkinChange(p, customSource);
                } catch (Exception e) {
                    EverlastingSkins.logger.warn("Failed to announce skin change to DiscordSRV", e);
                }
            }
        });
    }

    public static void task(EntityPlayerMP target, CustomSkinProperty property) {
        if (target == null) return;

        long startNanos = System.nanoTime();
        SkinMetrics.INSTANCE.recordRefreshStarted(target.getUniqueID());

        if (property == null || property.isEmpty()) {
            SkinMetrics.INSTANCE.recordRefreshFailed(target.getUniqueID());
            return;
        }

        MinecraftServer server = target.mcServer;
        PlayerList playerList = server.getPlayerList();
        WorldServer world = (WorldServer) target.world;

        // 1. Mutate the GameProfile properties on the server side.
        target.getGameProfile().getProperties().removeAll("textures");
        target.getGameProfile().getProperties().put("textures", property.getOriginalProperty());

        // 2. Tab-list update to ALL online players (global, no dimension scoping).
        long broadcastStartNanos = System.nanoTime();
        playerList.sendPacketToAllPlayers(
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, target));
        playerList.sendPacketToAllPlayers(
            new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, target));
        long broadcastNanos = System.nanoTime() - broadcastStartNanos;
        SkinMetrics.INSTANCE.recordBroadcastLatency(broadcastNanos);

        // 3. Target's own view — respawn cascade (same dimension = no inventory wipe).
        EntityPlayerMP self = target;
        self.connection.sendPacket(new SPacketRespawn(
            self.dimension, self.world.getDifficulty(),
            self.world.getWorldInfo().getTerrainType(),
            self.interactionManager.getGameType()));
        self.connection.setPlayerLocation(
            self.posX, self.posY, self.posZ, self.rotationYaw, self.rotationPitch);
        self.connection.sendPacket(new SPacketServerDifficulty(
            self.world.getDifficulty(),
            self.world.getWorldInfo().isDifficultyLocked()));
        playerList.updatePermissionLevel(self);
        self.sendPlayerAbilities();

        // 4. Replay active potion effects (vanilla transferPlayerToDimension parity).
        for (PotionEffect effect : self.getActivePotionEffects()) {
            self.connection.sendPacket(new SPacketEntityEffect(self.getEntityId(), effect));
        }

        // 5. Time/weather sync (join parity).
        playerList.updateTimeAndWeatherForPlayer(self, world);

        // 6. Observer re-render via per-viewer EntityTracker untrack/re-track.
        if (Config.refreshViaEntityTracker) {
            EntityTracker tracker = world.getEntityTracker();
            tracker.untrack(self);
            tracker.track(self);
            tracker.updateVisibility(self);
        }

        // 7. Persist asynchronously (in-memory map already updated; disk flush off-tick).
        long saveStartNanos = System.nanoTime();
        SkinRestorer.getSkinStorage().saveSkinAsync(self.getUniqueID(), property);
        long saveNanos = System.nanoTime() - saveStartNanos;

        // 8. Metrics.
        long durationNanos = System.nanoTime() - startNanos;
        SkinMetrics.INSTANCE.recordTaskDuration(durationNanos);
        if (durationNanos > 50_000_000L) {
            EverlastingSkins.logger.warn("SkinRefresh spike: {}ms for player {}",
                durationNanos / 1_000_000, self.getName());
        }
        SkinMetrics.INSTANCE.recordRefreshCompleted(
            target.getUniqueID(), startNanos, 0L, saveNanos, broadcastNanos);
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
