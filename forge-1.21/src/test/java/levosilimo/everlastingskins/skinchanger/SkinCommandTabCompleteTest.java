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
import levosilimo.everlastingskins.forge21.skinchanger.SkinCommand;
import levosilimo.everlastingskins.Config;
import levosilimo.everlastingskins.permission.PermissionServiceManager;
import levosilimo.everlastingskins.permission.TestConfigSupport;
import levosilimo.everlastingskins.forge21.permission.VanillaPermissionService;
import levosilimo.everlastingskins.forge21.util.CompletionSources;
import levosilimo.everlastingskins.skinchanger.MojangProfileCache;
import levosilimo.everlastingskins.util.CustomSkinProperty;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
        CompletionSources.resetAppliedHistory();
        dispatcher = new CommandDispatcher<>();
        SkinCommand.register(dispatcher);
        source = sourceWithOpLevel(0);
    }

    @AfterEach
    void tearDown() {
        // Undo config overrides (e.g. the metrics op levels) and applied-skin
        // history so later test classes in the same JVM see clean state.
        TestConfigSupport.loadDefaults();
        CompletionSources.resetAppliedHistory();
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
        assertTrue(offered.contains("help"));
    }

    @Test
    @DisplayName("help is a root literal with an executor")
    void help_isRootLiteral() {
        CommandNode<CommandSourceStack> help = dispatcher.getRoot().getChild("skin").getChild("help");
        assertTrue(help.canUse(source));
        List<String> offered = completions("skin he");
        assertTrue(offered.contains("help"));
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
    @DisplayName("set mojang skin_name completion merges online player names")
    void set_mojang_skinName_completion_includesOnlinePlayers() {
        List<String> suggestions = completions("skin set mojang ");

        assertTrue(suggestions.contains("Alex"));
        assertTrue(suggestions.contains("Bob"));
    }

    @Test
    @DisplayName("set mojang skin_name completion offers previously applied names first")
    void set_mojang_skinName_completion_includesAppliedHistory() {
        CompletionSources.recordAppliedUsername("Herobrine");

        List<String> suggestions = completions("skin set mojang ");

        assertTrue(suggestions.contains("Herobrine"));
        // History is deduplicated against the configured defaults.
        assertTrue(suggestions.contains("Steve"));
    }

    @Test
    @DisplayName("set mojang skin_name completion is case-insensitive and matches substrings")
    void set_mojang_skinName_completion_isFuzzy() {
        CompletionSources.recordAppliedUsername("Alex");
        List<String> suggestions = completions("skin set mojang ALEX");
        assertTrue(suggestions.contains("Alex"), "case-insensitive prefix should match Alex");

        List<String> substring = completions("skin set mojang x");
        assertTrue(substring.contains("Alex"), "substring match should find Alex via its tail");
    }

    @Test
    @DisplayName("set mojang skin_name suggestions carry origin tooltips")
    void set_mojang_skinName_completion_hasTooltips() {
        MojangProfileCache cache = new MojangProfileCache();
        cache.put("Notch", new CustomSkinProperty("value", "signature", "Notch"));
        CompletionSources.setMojangProfileCache(cache);
        CompletionSources.recordAppliedUsername("Herobrine");

        List<Suggestion> raw = rawSuggestions("skin set mojang ");

        assertTrue(tooltipOf(raw, "Herobrine").contains("Previously used"));
        assertTrue(tooltipOf(raw, "Alex").contains("Online"));
        assertTrue(tooltipOf(raw, "notch").contains("Cached"), "cache snapshot keys are lowercased");
        assertTrue(tooltipOf(raw, "Steve").contains("Configured default"));
    }

    @Test
    @DisplayName("set web variant completion returns classic and slim")
    void set_web_variant_completion_returnsClassicSlim() {
        List<String> variants = completions("skin set web ");

        assertTrue(variants.contains("classic"));
        assertTrue(variants.contains("slim"));
    }

    @Test
    @DisplayName("set web url completion offers previously applied URLs with tooltips")
    void set_web_url_completion_includesAppliedUrls() {
        CompletionSources.recordAppliedUrl("https://imgur.com");

        List<Suggestion> raw = rawSuggestions("skin set web classic ");

        assertTrue(completions("skin set web classic ").contains("https://imgur.com"));
        assertTrue(tooltipOf(raw, "https://imgur.com").contains("Previously used"));
        assertTrue(tooltipOf(raw, "https://textures.minecraft.net").contains("Allowlisted URL"));
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
        return rawSuggestions(input).stream().map(Suggestion::getText).toList();
    }

    private List<Suggestion> rawSuggestions(String input) {
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(input, source);
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parsed).join();
        return suggestions.getList();
    }

    private static String tooltipOf(List<Suggestion> suggestions, String text) {
        for (Suggestion suggestion : suggestions) {
            if (suggestion.getText().equals(text) && suggestion.getTooltip() != null) {
                return suggestion.getTooltip().getString();
            }
        }
        return "";
    }

    private CommandSourceStack sourceWithOpLevel(int opLevel) {
        // Mocking a Level subclass first initializes the registry chain
        // (BuiltInRegistries/Forge) that EntityDataSerializers needs when
        // ServerPlayer is instrumented; without it the mock fails to create.
        var unused = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(PLAYER_UUID);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getProfilePermissions(any())).thenReturn(opLevel);
        when(player.getServer()).thenReturn(server);

        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getPlayer()).thenReturn(player);
        when(source.getOnlinePlayerNames()).thenReturn(ONLINE_PLAYERS);
        when(source.hasPermission(anyInt())).thenReturn(opLevel >= 2);
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
