package com.skalpha.tiertagger.config;
import com.skalpha.tiertagger.TierCache;
import com.skalpha.tiertagger.TierTagger;
import com.skalpha.tiertagger.model.TierList;
import com.skalpha.tiertagger.tierlist.PlayerSearchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.option.*;
import net.uku3lig.ukulib.config.option.SliderOption;
import net.uku3lig.ukulib.config.option.widget.ButtonTab;
import net.uku3lig.ukulib.config.screen.TabbedConfigScreen;
import net.uku3lig.ukulib.utils.Ukutils;
import java.util.*;
import java.util.stream.Collectors;
public class TTConfigScreen extends TabbedConfigScreen<TierTaggerConfig> {
    private boolean isInitialized = false;
    private int selectedTabIndex = 0;
    public TTConfigScreen(Screen parent) {
        super("TierTagger Config", parent, TierTagger.getManager());
    }
    @Override
    protected Tab[] getTabs(TierTaggerConfig config) {
        return new Tab[]{
                new MainSettingsTab(),
                new ColorsTab(),
                new TierlistTab()
        };
    }


    public void refresh(int index) {
        this.selectedTabIndex = index;
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void init() {
        super.init();

        this.children().stream()
                .filter(c -> c instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar)
                .map(c -> (net.minecraft.client.gui.components.tabs.TabNavigationBar) c)
                .findFirst()
                .ifPresent(nav -> nav.selectTab(this.selectedTabIndex, false));

        isInitialized = true;
    }
    @Override
    protected Collection<AbstractWidget> getInvalidOptions() {
        if (!isInitialized) {
            return Collections.emptySet();
        }
        return super.getInvalidOptions();
    }

    public class MainSettingsTab extends ButtonTab<TierTaggerConfig> {
        public MainSettingsTab() {
            super("tiertagger.config", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            //To Do List: More Settings
            return new WidgetCreator[]{
                    CyclingOption.ofBoolean("tiertagger.config.enabled", config.isEnabled(), config::setEnabled),
                    new CyclingOption<>("tiertagger.config.gamemode", TierCache.getGamemodes(), config.getGameMode(),
                            m -> config.setGameMode(m.id()), m -> Component.literal(m.title()),
                            m -> m.isNone() ? Tooltip.create(Component.translatable("tiertagger.config.gamemode.none")) : null,
                            !config.getGameMode().isNone()),
                    CyclingOption.ofBoolean("tiertagger.config.retired", config.isShowRetired(), config::setShowRetired),
                    CyclingOption.ofTranslatableEnum("tiertagger.config.highest", TierTaggerConfig.HighestMode.class,
                            config.getHighestMode(), config::setHighestMode,
                            OptionInstance.cachedConstantTooltip(Component.translatable("tiertagger.config.highest.desc"))),
                    CyclingOption.ofBoolean("tiertagger.config.icons", config.isShowIcons(), config::setShowIcons),
                    CyclingOption.ofBoolean("tiertagger.config.playerList", config.isPlayerList(), config::setPlayerList),
                    new SimpleButton("tiertagger.clear", b -> TierCache.clearCache()),
                    new ScreenOpenButton("tiertagger.config.search", PlayerSearchScreen::new)
            };
        }
    }

    public class ColorsTab extends ButtonTab<TierTaggerConfig> {
        protected ColorsTab() {
            super("tiertagger.colors", TTConfigScreen.this.manager);
        }
        //To Do List: Gradient Color Support
        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            Comparator<Map.Entry<String, Integer>> comparator = (e1, e2) -> {
                String k1 = e1.getKey();
                String k2 = e2.getKey();
                if (k1.length() > 2 && k2.length() > 2) {
                    int res = Character.compare(k1.charAt(2), k2.charAt(2));
                    if (res != 0) return res;
                }
                return k1.compareTo(k2);
            };
            List<ColorOption> tiers = config.getTierColors().entrySet().stream()
                    .sorted(comparator)
                    .map(e -> new ColorOption(e.getKey(), e.getValue(), val -> config.getTierColors().put(e.getKey(), val)))
                    .collect(Collectors.toList());
            tiers.addLast(new ColorOption("tiertagger.colors.retired", config.getRetiredColor(), config::setRetiredColor));
            return tiers.toArray(WidgetCreator[]::new);
        }
    }

    public class TierlistTab extends ButtonTab<TierTaggerConfig> {
        private static final int PER_PAGE = 9;

        public TierlistTab() {
            super("tiertagger.config.tierlists", TTConfigScreen.this.manager);
        }
        //To Do List: Add Tierlist Discord Profiles and Support 2 Tierlists at once

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            List<TierList> all;
            try {
                all = List.copyOf(TierList.values());
            } catch (Exception e) {
                System.err.println("TierTagger: Failed to load tierlists: " + e);
                e.printStackTrace();
                return new WidgetCreator[]{
                        new SimpleButton(Component.literal("Error loading tierlists"), b -> {
                        })
                };
            }

            if (all.isEmpty()) {
                return new WidgetCreator[]{
                        new SimpleButton(Component.literal("No Tierlists Found"), b -> {
                        })
                };
            }

            int totalPages = (int) Math.ceil(all.size() / (double) PER_PAGE);
            if (config.tierlistPage >= totalPages) {
                config.tierlistPage = Math.max(0, totalPages - 1);
            }

            int page = config.tierlistPage;
            List<WidgetCreator> widgets = new ArrayList<>();

            int start = page * PER_PAGE;
            for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
                TierList tier = all.get(start + i);
                boolean selected = tier.getUrl().equals(config.getApiUrl());

                Component label = selected
                        ? Component.literal( tier.getName()).withStyle(style -> style.withColor(0x55FF55))
                        : Component.literal(tier.getName());

                widgets.add(new SimpleButton(
                        label,
                        b -> {
                            if (selected) return;

                            config.setApiUrl(tier.getUrl());
                            TierTagger.getManager().saveConfig();
                            TierCache.clearCache();
                            TierCache.init();

                            Ukutils.sendToast(
                                    Component.literal("Tierlist changed to " + tier.getName() + "!"),
                                    Component.literal("Reloading tiers...")
                            );

                            if (Minecraft.getInstance().screen instanceof TTConfigScreen screen) {
                                screen.refresh(2);
                            }
                        },
                        !selected
                ));
            }
            if (totalPages > 1) {
                widgets.add(new SliderOption(
                        "tiertagger.config.page",
                        page,
                        val -> {
                            config.tierlistPage = (int) Math.round(val);
                            TierTagger.getManager().saveConfig();

                            if (Minecraft.getInstance().screen instanceof TTConfigScreen screen) {
                                screen.refresh(2);
                            }
                        },
                        val -> Component.literal(String.valueOf(((int) Math.round(val) + 1))),
                        0,
                        totalPages - 1
                ));
            }

            return widgets.toArray(WidgetCreator[]::new);
        }
    }
}