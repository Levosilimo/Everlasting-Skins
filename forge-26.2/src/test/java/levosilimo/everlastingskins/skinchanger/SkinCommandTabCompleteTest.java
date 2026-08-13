/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.skinchanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import levosilimo.everlastingskins.forge26.skinchanger.SkinCommand;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.forge26.permission.VanillaPermissionService;
import levosilimo.everlastingskins.forge26.util.CompletionSources;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tab-completion behavior of the Brigadier /skin tree: suggesters emit the
 * shared CompletionSources candidates, and requires() predicates gate which
 * nodes an unauthorized sender may use. Literal-level requires() is enforced
 * through the command tree the client receives (brigadier's server-side
 * suggestion walk offers all children), so those gates are asserted on the
 * nodes' canUse() rather than on the raw suggestion list.
 */
class SkinCommandTabCompleteTest {

    /**
     * The unit-test JVM has no running server, so mocking ServerPlayer loads
     * EntityDataSerializers, whose &lt;clinit&gt; would throw "Not bootstrapped".
     * Flag Bootstrap as done (same seam as SkinRefreshHandlerTest).
     */
    static {
        try {
            Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapFlag.setAccessible(true);
            bootstrapFlag.setBoolean(null, true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final UUID PLAYER_UUID = UUID.randomUUID();
    private static final List<String> ONLINE_PLAYERS = List.of("Alex", "Bob");

    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack source;

    @BeforeEach
    void setUp() {
        TestConfigSupport.loadDefaults();
        // :common's manager registers no backend by itself (fail-closed); the
        // per-version bootstrap registers these — tests mirror the bootstrap.
        PermissionServiceManager.registerService(new VanillaPermissionService());
        CompletionSources.setMojangProfileCache(new MojangProfileCache());
        dispatcher = new CommandDispatcher<>();
        SkinCommand.register(dispatcher);
        source = sourceWithOpLevel(0);
    }

    @AfterEach
    void tearDown() {
        // Undo config overrides (e.g. the metrics op levels) so later test
        // classes in the same JVM always see the defaults.
        TestConfigSupport.loadDefaults();
    }

    @Test
    @DisplayName("root completion hides subcommands the sender lacks permission for")
    void subcommand_completion_filtersByPermission() {
        CommandNode<CommandSourceStack> skin = dispatcher.getRoot().getChild("skin");
        // Defaults: metrics needs op level 2, so a level-0 player cannot use
        // the metrics subtree while set/clear/source stay open.
        assertTrue(skin.getChild("set").canUse(source));
        assertTrue(skin.getChild("clear").canUse(source));
        assertTrue(skin.getChild("source").canUse(source));
        assertFalse(skin.getChild("metrics").canUse(source));

        source = sourceWithOpLevel(2);
        assertTrue(skin.getChild("metrics").canUse(source));

        // Server-side completion offers every registered child; the client
        // filters literals by the tree derived from canUse().
        List<String> offered = completions("skin ");
        assertTrue(offered.contains("set"));
        assertTrue(offered.contains("clear"));
        assertTrue(offered.contains("source"));
        assertTrue(offered.contains("metrics"));
    }

    @Test
    @DisplayName("set provider completion returns the configured providers")
    void set_provider_completion_returnsConfiguredProviders() {
        List<String> providers = completions("skin set ");

        assertTrue(providers.contains("mojang"));
        assertTrue(providers.contains("random"));
        assertTrue(providers.contains("web"));
    }

    @Test
    @DisplayName("set mojang skin_name completion returns cached profiles and default skins")
    void set_mojang_skinName_completion_returnsRecentUsernames() {
        MojangProfileCache cache = new MojangProfileCache();
        cache.put("Notch", new CustomSkinProperty("value", "signature", "Notch"));
        CompletionSources.setMojangProfileCache(cache);

        List<String> suggestions = completions("skin set mojang ");

        assertTrue(suggestions.contains("notch"));
        assertTrue(suggestions.contains("Steve"));
        assertFalse(suggestions.contains("<random>"));
    }

    @Test
    @DisplayName("set web variant completion returns classic and slim")
    void set_web_variant_completion_returnsClassicSlim() {
        List<String> variants = completions("skin set web ");

        assertTrue(variants.contains("classic"));
        assertTrue(variants.contains("slim"));
    }

    @Test
    @DisplayName("set web url completion returns allowlisted domains with scheme prefixes")
    void set_web_url_completion_returnsAllowlistDomains() {
        List<String> urls = completions("skin set web classic ");

        assertTrue(urls.contains("https://imgur.com"));
        assertTrue(urls.contains("http://imgur.com"));
        assertTrue(urls.contains("https://textures.minecraft.net"));
        assertTrue(urls.contains("http://textures.minecraft.net"));
    }

    @Test
    @DisplayName("set random completion cascades bool, variant and online players")
    void set_random_completion_cascade() {
        List<String> level0 = completions("skin set random ");
        assertTrue(level0.contains("true"));
        assertTrue(level0.contains("false"));
        assertTrue(level0.contains("CLASSIC"));
        assertTrue(level0.contains("SLIM"));
        assertTrue(level0.contains("Alex"));
        assertTrue(level0.contains("Bob"));

        // The targets argument is only usable by senders with the other-permission.
        CommandNode<CommandSourceStack> targets = dispatcher.getRoot().getChild("skin")
                .getChild("set").getChild("random").getChild("targets");
        assertFalse(targets.canUse(source));
        source = sourceWithOpLevel(2);
        assertTrue(targets.canUse(source));

        List<String> afterCape = completions("skin set random true ");
        assertTrue(afterCape.contains("CLASSIC"));
        assertTrue(afterCape.contains("SLIM"));
    }

    @Test
    @DisplayName("clear and source target completion returns online players")
    void clear_source_target_completion_returnsOnlinePlayers() {
        source = sourceWithOpLevel(2);
        List<String> clear = completions("skin clear ");
        assertTrue(clear.contains("Alex"));
        assertTrue(clear.contains("Bob"));

        List<String> sourceTab = completions("skin source ");
        assertTrue(sourceTab.contains("Alex"));
        assertTrue(sourceTab.contains("Bob"));

        CommandNode<CommandSourceStack> clearTargets = dispatcher.getRoot().getChild("skin")
                .getChild("clear").getChild("targets");
        assertTrue(clearTargets.canUse(source));
        assertFalse(clearTargets.canUse(sourceWithOpLevel(0)));
    }

    @Test
    @DisplayName("metrics completion hides reset from senders without the reset permission")
    void metrics_completion_filtersByResetPermission() {
        Config.PERMISSIONS_OP_LEVEL_METRICS.set(0);
        Config.PERMISSIONS_OP_LEVEL_METRICS_RESET.set(2);

        CommandNode<CommandSourceStack> metrics = dispatcher.getRoot().getChild("skin").getChild("metrics");
        // Level-0 sender holds command.metrics but not command.metrics.reset.
        assertTrue(metrics.canUse(source));
        assertFalse(metrics.getChild("reset").canUse(source));

        List<String> offered = completions("skin metrics ");
        assertTrue(offered.contains("json"));
        assertTrue(offered.contains("players"));
        assertTrue(offered.contains("cleanup"));
    }

    @Test
    @DisplayName("metrics cleanup requires the reset permission like reset does")
    void metrics_cleanup_completion_requiresResetPermission() {
        Config.PERMISSIONS_OP_LEVEL_METRICS.set(0);
        Config.PERMISSIONS_OP_LEVEL_METRICS_RESET.set(2);

        CommandNode<CommandSourceStack> metrics = dispatcher.getRoot().getChild("skin").getChild("metrics");
        // Level-0 sender holds command.metrics but not command.metrics.reset.
        assertTrue(metrics.canUse(source));
        assertFalse(metrics.getChild("cleanup").canUse(source));

        // Level-2 sender holds both.
        source = sourceWithOpLevel(2);
        assertTrue(metrics.getChild("cleanup").canUse(source));
    }

    private List<String> completions(String input) {
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(input, source);
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parsed).join();
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    private CommandSourceStack sourceWithOpLevel(int opLevel) {
        // Mocking a Level subclass first initializes the registry chain
        // (BuiltInRegistries/Forge) that EntityDataSerializers needs when
        // ServerPlayer is instrumented; without it the mock fails to create.
        var unused = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(PLAYER_UUID);
        // 26.x PermissionContext.of(uuid, player) derives the op level from the
        // player's PermissionSet (Permission.HasCommandLevel probes), so the
        // requires() gates resolve through these constants like 1.21's
        // hasPermissions path did.
        when(player.permissions()).thenReturn(
                opLevel >= 2 ? PermissionSet.ALL_PERMISSIONS : PermissionSet.NO_PERMISSIONS);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);
        when(source.getOnlinePlayerNames()).thenReturn(ONLINE_PLAYERS);
        // canUse() gates also resolve through the source-level PermissionSet
        // (ForgeHooks.canUse probes source.permissions()), which the 1.21
        // hasPermission(int) stub does not cover on 26.x.
        when(source.permissions()).thenReturn(
                opLevel >= 2 ? PermissionSet.ALL_PERMISSIONS : PermissionSet.NO_PERMISSIONS);
        return source;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = ServerPlayer.class.getField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set ServerPlayer." + fieldName, e);
        }
    }
}
