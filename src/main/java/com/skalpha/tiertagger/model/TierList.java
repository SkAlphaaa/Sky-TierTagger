package com.skalpha.tiertagger.model;

import com.google.gson.*;
import lombok.Getter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class TierList {

    private static final Map<String, TierList> BY_ID = new LinkedHashMap<>();
    private static final Map<String, TierList> BY_NORMALIZED_URL = new ConcurrentHashMap<>();
    private static final List<TierList> ALL = new ArrayList<>();

    private static final AtomicBoolean LOADING = new AtomicBoolean(false);
    private static volatile boolean loadedSuccessfully = false;

    private static CompletableFuture<Void> loadFuture;

    private final String id;
    private final String name;
    private final String url;
    private final String iconUrl;
    private final char icon;

    private TierList(String id, String name, String url, String iconUrl, char icon) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.iconUrl = iconUrl;
        this.icon = icon;
    }
    public static List<TierList> values() {
        ensureLoadedAsync();
        return Collections.unmodifiableList(ALL);
    }

    private static void ensureLoadedAsync() {
        if (loadedSuccessfully || LOADING.get()) return;

        if (LOADING.compareAndSet(false, true)) {
            loadFuture = CompletableFuture.runAsync(TierList::loadFromApi);
        }
    }
    //Loads Active Tier lists from API
    private static void loadFromApi() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.skypractice.xyz/list"))
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray servers = root.getAsJsonArray("servers");
            if (servers == null) return;

            char[] icons = {
                    '\uE903','\uE904','\uE905','\uE906','\uE907','\uE908','\uE909','\uE90A',
                    '\uE90B','\uE90C','\uE90D','\uE90E','\uE90F','\uE910','\uE911','\uE912'
            };

            int iconIndex = 0;

            synchronized (TierList.class) {
                BY_ID.clear();
                BY_NORMALIZED_URL.clear();
                ALL.clear();

                for (JsonElement el : servers) {
                    JsonObject obj = el.getAsJsonObject();

                    String id = obj.get("id").getAsString();
                    String name = obj.get("name").getAsString();
                    String apiUrl = obj.get("api_url").getAsString();
                    String iconUrl = obj.get("icon_url").getAsString();

                    char icon = icons[Math.min(iconIndex++, icons.length - 1)];

                    TierList t = new TierList(id, name, apiUrl, iconUrl, icon);

                    ALL.add(t);
                    BY_ID.put(id, t);
                    BY_NORMALIZED_URL.put(normalize(apiUrl), t);
                }
            }

            loadedSuccessfully = !ALL.isEmpty();

        } catch (Exception e) {
            System.err.println("TierList load failed: " + e.getMessage());
        } finally {
            LOADING.set(false);
        }
    }

    private static String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
