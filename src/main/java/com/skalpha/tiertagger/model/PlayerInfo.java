package com.skalpha.tiertagger.model;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.skalpha.tiertagger.TierCache;
import com.skalpha.tiertagger.TierTagger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public record PlayerInfo(
        @SerializedName("uuid") String uuid,
        @SerializedName("name") String name,
        @SerializedName("rankings") Map<String, Ranking> rankings,
        @SerializedName("region") String region,
        @SerializedName("points") int points,
        @SerializedName("overall") int overall,
        @SerializedName("badges") List<Badge> badges,
        @SerializedName("combat_master") boolean combatMaster
) {

    public record Ranking(
            @SerializedName("tier_name") String tierName,
            @SerializedName("tier") String tier,
            @SerializedName("retired") boolean retired,
            @SerializedName("points") int points
    ) {

        public NamedRanking asNamed(@Nullable GameMode mode) {
            return new NamedRanking(mode, this);
        }
    }

    public record NamedRanking(@Nullable GameMode mode, Ranking ranking) {
    }

    public record Badge(String title, String desc) {
    }

    private static final Map<String, Integer> REGION_COLORS = Map.of(
            "NA", 0xff6a6e,
            "EU", 0x6aff6e,
            "SA", 0xff9900,
            "AU", 0xf6b26b,
            "ME", 0xffd966,
            "AS", 0xc27ba0,
            "AF", 0x674ea7
    );

    public int getRegionColor() {
        if (region == null) return 0xffffff;
        return REGION_COLORS.getOrDefault(region.toUpperCase(Locale.ROOT), 0xffffff);
    }

    public static CompletableFuture<PlayerInfo> get(HttpClient client, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/profile/" + uuid;
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> TierTagger.GSON.fromJson(json, PlayerInfo.class))
                .whenComplete((res, err) -> {
                    if (err != null) {
                        TierTagger.getLogger().warn("Error getting player info ({})", uuid, err);
                    }
                });
    }

    public static CompletableFuture<PlayerInfo> search(HttpClient client, String name) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/profile/by-name/" + name;
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> TierTagger.GSON.fromJson(json, PlayerInfo.class))
                .whenComplete((res, err) -> {
                    if (err != null) {
                        TierTagger.getLogger().warn("Error searching player {}", name, err);
                    }
                });
    }

    public static CompletableFuture<Object> getRankings(HttpClient client, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/profile/" + uuid + "/rankings";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json ->
                        TierTagger.GSON.fromJson(
                                json,
                                new TypeToken<Map<String, Ranking>>() {}.getType()
                        )
                )
                .whenComplete((res, err) -> {
                    if (err != null) {
                        TierTagger.getLogger().warn("Error getting rankings ({})", uuid, err);
                    }
                });
    }



    public static Optional<NamedRanking> getHighestRanking(Map<String, Ranking> rankings) {
        return rankings.entrySet().stream()
                .min(Comparator
                        .comparing((Map.Entry<String, Ranking> e) -> e.getValue().retired)
                        .thenComparing(e -> -e.getValue().points)
                        .thenComparing(e -> e.getValue().tierName, Comparator.nullsLast(String::compareTo))
                )
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())));
    }


    public List<NamedRanking> getSortedTiers() {
        List<NamedRanking> list = new ArrayList<>();

        for (var entry : rankings.entrySet()) {
            list.add(entry.getValue().asNamed(
                    TierCache.findModeOrUgly(entry.getKey())
            ));
        }

        list.sort(Comparator
                .comparing((NamedRanking n) -> n.ranking.retired)
                .thenComparingInt(n -> -n.ranking.points)
                .thenComparing(n -> n.ranking.tierName, Comparator.nullsLast(String::compareTo))
        );

        return list;
    }

    @Getter
    @AllArgsConstructor
    public enum PointInfo {
        COMBAT_GRANDMASTER("Combat Grandmaster", 0xE6C622, 0xFDE047),
        COMBAT_MASTER("Combat Master", 0xFBB03B, 0xFFD13A),
        COMBAT_ACE("Combat Ace", 0xCD285C, 0xD65474),
        COMBAT_SPECIALIST("Combat Specialist", 0xAD78D8, 0xC7A3E8),
        COMBAT_CADET("Combat Cadet", 0x9291D9, 0xADACE2),
        COMBAT_NOVICE("Combat Novice", 0x9291D9, 0xFFFFFF),
        ROOKIE("Rookie", 0x6C7178, 0x8B979C),
        UNRANKED("Unranked", 0xFFFFFF, 0xFFFFFF);

        private final String title;
        private final int color;
        private final int accentColor;
    }

    public PointInfo getPointInfo() {
        if (points >= 400) return PointInfo.COMBAT_GRANDMASTER;
        if (points >= 250) return PointInfo.COMBAT_MASTER;
        if (points >= 100) return PointInfo.COMBAT_ACE;
        if (points >= 50) return PointInfo.COMBAT_SPECIALIST;
        if (points >= 20) return PointInfo.COMBAT_CADET;
        if (points >= 10) return PointInfo.COMBAT_NOVICE;
        if (points >= 1) return PointInfo.ROOKIE;
        return PointInfo.UNRANKED;
    }
}
