package com.skalpha.tiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.skalpha.tiertagger.config.TierTaggerConfig;
import com.skalpha.tiertagger.model.GameMode;
import com.skalpha.tiertagger.model.PlayerInfo;
import com.skalpha.tiertagger.model.TierList;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.uku3lig.ukulib.config.ConfigManager;
import net.uku3lig.ukulib.utils.PlayerArgumentType;
import net.uku3lig.ukulib.utils.Ukutils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TierTagger implements ModInitializer {

    public static final String MOD_ID = "tiertagger";
    private static final String UPDATE_URL_FORMAT =
            "https://api.modrinth.com/v2/project/XWN8Nb1V/version?game_versions=%s";

    public static final Gson GSON = new GsonBuilder().create();

    @Getter
    private static final ConfigManager<TierTaggerConfig> manager =
            ConfigManager.createDefault(TierTaggerConfig.class, MOD_ID);

    @Getter private static final Logger logger = LoggerFactory.getLogger(TierTagger.class);
    @Getter private static final HttpClient client = HttpClient.newHttpClient();

    @Getter private static Version latestVersion = null;
    private static final AtomicBoolean isObsolete = new AtomicBoolean(false);

    @Override
    public void onInitialize() {
        CompletableFuture.runAsync(() -> {
            TierList.values();
            TierCache.init();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
                dispatcher.register(
                        literal(MOD_ID)
                                .then(argument("player", PlayerArgumentType.player())
                                        .executes(TierTagger::displayTierInfo))
                )
        );

        Ukutils.registerKeybinding(
                new KeyMapping(
                        "tiertagger.keybind.gamemode",
                        GLFW.GLFW_KEY_UNKNOWN,
                        KeyMapping.Category.register(
                                Identifier.fromNamespaceAndPath("tiertagger", "key"))
                ),
                mc -> {
                    GameMode next = TierCache.findNextMode(manager.getConfig().getGameMode());
                    manager.getConfig().setGameMode(next.id());

                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                                Component.literal("Displayed gamemode: ")
                                        .append(next.asStyled(false)),
                                true
                        );
                    }
                }
        );

        checkForUpdates();
    }

    public static Component appendTier(UUID uuid, Component text) {
        MutableComponent following = getPlayerTier(uuid)
                .map(entry -> {
                    Component tierText = getRankingText(entry.ranking(), false);

                    if (manager.getConfig().isShowIcons()
                            && entry.mode() != null
                            && entry.mode().icon().isPresent()) {
                        return Component.literal(entry.mode().icon().get().toString())
                                .append(tierText);
                    }
                    return tierText.copy();
                })
                .orElse(null);

        if (following != null) {
            following.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            return following.append(text);
        }

        return text;
    }

    public static Optional<PlayerInfo.NamedRanking> getPlayerTier(UUID uuid) {
        GameMode mode = manager.getConfig().getGameMode();

        return TierCache.getPlayerRankings(uuid)
                .map(rankings -> {
                    PlayerInfo.Ranking direct = rankings.get(mode.id());
                    Optional<PlayerInfo.NamedRanking> highest =
                            PlayerInfo.getHighestRanking(rankings);

                    TierTaggerConfig.HighestMode highestMode =
                            manager.getConfig().getHighestMode();

                    if (direct == null) {
                        return highestMode != TierTaggerConfig.HighestMode.NEVER
                                ? highest.orElse(null)
                                : null;
                    }

                    if (highestMode == TierTaggerConfig.HighestMode.ALWAYS
                            && highest.isPresent()) {
                        return highest.get();
                    }

                    return direct.asNamed(mode);
                });
    }

    public static Component getRankingText(PlayerInfo.Ranking ranking, boolean showPoints) {
        String tier = ranking.tierName() != null
                ? ranking.tierName()
                : ranking.tier();

        if (ranking.retired()) {
            tier = "R" + tier;
        }

        int color = getTierColor(tier);

        MutableComponent text = Component.literal(tier)
                .withStyle(s -> s.withColor(color));

        if (showPoints) {
            text.append(Component.literal(" (" + ranking.points() + " pts)")
                    .withStyle(ChatFormatting.GRAY));
        }

        return text;
    }

    public static int getTierColor(String tier) {
        if (tier.startsWith("R")) {
            return manager.getConfig().getRetiredColor();
        }
        return manager.getConfig().getTierColors()
                .getOrDefault(tier, 0xD3D3D3);
    }

    private static int displayTierInfo(CommandContext<FabricClientCommandSource> ctx) {
        PlayerArgumentType.PlayerSelector selector =
                ctx.getArgument("player", PlayerArgumentType.PlayerSelector.class);

        Optional<Map<String, PlayerInfo.Ranking>> rankings =
                ctx.getSource().getWorld().players().stream()
                        .filter(p -> p.getScoreboardName().equalsIgnoreCase(selector.name())
                                || p.getStringUUID().equalsIgnoreCase(selector.name()))
                        .findFirst()
                        .map(Entity::getUUID)
                        .flatMap(TierCache::getPlayerRankings);

        if (rankings.isPresent()) {
            ctx.getSource().sendFeedback(printPlayerInfo(selector.name(), rankings.get()));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[TierTagger] Searching..."));
            TierCache.searchPlayer(selector.name())
                    .thenAccept(p ->
                            Minecraft.getInstance().execute(() ->
                                    ctx.getSource().sendFeedback(
                                            printPlayerInfo(selector.name(), p.rankings())
                                    )
                            )
                    )
                    .exceptionally(t -> {
                        ctx.getSource().sendError(
                                Component.literal("Could not find player " + selector.name()));
                        return null;
                    });
        }

        return 0;
    }

    private static Component printPlayerInfo(
            String name, Map<String, PlayerInfo.Ranking> rankings) {

        if (rankings.isEmpty()) {
            return Component.literal(name + " does not have any tiers.");
        }

        MutableComponent text =
                Component.empty().append("=== Rankings for " + name + " ===");

        rankings.forEach((m, r) -> {
            if (m == null) return;
            GameMode mode = TierCache.findModeOrUgly(m);
            text.append(Component.literal("\n"))
                    .append(mode.asStyled(true))
                    .append(": ")
                    .append(getRankingText(r, true));
        });

        return text;
    }

    private static void checkForUpdates() {
        String versionParam =
                "[\"%s\"]".formatted(SharedConstants.getCurrentVersion().name());

        String fullUrl = UPDATE_URL_FORMAT.formatted(
                URLEncoder.encode(versionParam, StandardCharsets.UTF_8));

        HttpRequest request =
                HttpRequest.newBuilder(URI.create(fullUrl)).GET().build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    JsonArray array = GSON.fromJson(r.body(), JsonArray.class);
                    if (array.isEmpty()) return null;

                    JsonObject root = array.get(0).getAsJsonObject();
                    String latestVer = root.get("version_number").getAsString();

                    try {
                        return Version.parse(latestVer);
                    } catch (VersionParsingException e) {
                        logger.warn("Could not parse version {}", latestVer);
                        return null;
                    }
                })
                .thenAccept(v -> latestVersion = v)
                .exceptionally(t -> {
                    logger.warn("Error checking for updates", t);
                    return null;
                });
    }

    public static boolean isObsolete() {
        return isObsolete.get();
    }
}
