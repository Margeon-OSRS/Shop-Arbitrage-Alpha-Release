package com.margeon.shoparbitrage;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@PluginDescriptor(
        name = "Shop Arbitrage",
        description = "Advanced shop arbitrage and GE flip prediction tool",
        tags = {"shop", "money", "profit", "arbitrage", "flipping", "prediction"}
)
public class ShopArbitragePlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ShopArbitrageConfigV2 config;  // Use V2 config

    @Inject
    private WikiPriceService wikiPriceService;

    @Inject
    private PriceHistoryService priceHistoryService;  // NEW

    @Inject
    private FlipScorer flipScorer;  // NEW

    @Inject
    private FlippingSessionManager sessionManager;

    private MainPanelV2 mainPanel;  // Use V2 panel
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        // Initialize with prediction services
        mainPanel = new MainPanelV2(
                itemManager,
                clientThread,
                wikiPriceService,
                priceHistoryService,  // NEW
                flipScorer,           // NEW
                sessionManager,
                config
        );
        mainPanel.init();

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Shop Arbitrage")
                .icon(icon)
                .priority(5)
                .panel(mainPanel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        // Save price history on shutdown
        priceHistoryService.saveHistory();

        clientToolbar.removeNavigation(navButton);
        mainPanel = null;
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        sessionManager.onGrandExchangeOfferChanged(event);
    }

    @Provides
    ShopArbitrageConfigV2 provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ShopArbitrageConfigV2.class);
    }
}