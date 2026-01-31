package com.skalpha.tiertagger.config;
import com.google.gson.internal.LinkedTreeMap;
import com.skalpha.tiertagger.TierCache;
import com.skalpha.tiertagger.model.GameMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.uku3lig.ukulib.config.option.StringTranslatable;
import java.io.Serializable;
import java.util.Optional;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierTaggerConfig implements Serializable {
    private boolean enabled = true;
    private String gameMode = "sword";
    private boolean showRetired = true;
    private HighestMode highestMode = HighestMode.NOT_FOUND;
    private boolean showIcons = true;
    private boolean playerList = true;
    private int retiredColor = 0xa2d6ff;
    private double sliderValue = 50.0;
    public int tierlistPage = 0;
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();
    /**
     * <p>the field was renamed to do a little trolling and force it setting to the default value in players' config</p>
     * <p>previous name(s): {@code baseUrl}</p>
     */
    private String apiUrl = "https://api.skypractice.xyz/api/metatl";
    public GameMode getGameMode() {
        Optional<GameMode> opt = TierCache.findMode(this.gameMode);
        if (opt.isPresent()) {
            return opt.get();
        } else {
            GameMode first = TierCache.getGamemodes().getFirst();
            if (!first.isNone()) this.gameMode = first.id();
            return first;
        }
    }
    //To Do List: Make it so it will check tierlist for colors :pray:
    public static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xe8ba3a);
        colors.put("LT1", 0xd5b355);
        colors.put("HT2", 0xc4d3e7);
        colors.put("LT2", 0xa0a7b2);
        colors.put("HT3", 0xf89f5a);
        colors.put("LT3", 0xc67b42);
        colors.put("HT4", 0x81749a);
        colors.put("LT4", 0x655b79);
        colors.put("HT5", 0x8f82a8);
        colors.put("LT5", 0x655b79);
        return colors;
    }
    @Getter
    @AllArgsConstructor
    public enum HighestMode implements StringTranslatable {
        NEVER ("never", "tiertagger.highest.never"),
        NOT_FOUND ("not_found", "tiertagger.highest.not_found"),
        ALWAYS ("always", "tiertagger.highest.always");
        private final String name;
        private final String translationKey;
    }
}