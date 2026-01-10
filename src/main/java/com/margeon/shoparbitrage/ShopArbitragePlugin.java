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
import javax.swing.*;
import java.awt.image.BufferedImage;

@PluginDescriptor(
        name = "Shop Arbitrage",
        description = "Advanced shop arbitrage and GE flip prediction tool",
        tags = {"shop", "money", "profit", "arbitrage", "flipping", "prediction"}
)
public class ShopArbitragePlugin extends Plugin
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShopArbitragePlugin.class);

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
    private PriceHistoryService priceHistoryService;

    @Inject
    private FlipScorer flipScorer;

    @Inject
    private FlippingSessionManager sessionManager;

    private LoginPanel loginPanel;
    private MainPanelV2 mainPanel;
    private NavigationButton navButton;
    private boolean isAuthenticated = false;

    @Override
    protected void startUp() throws Exception
    {
        // Create login panel first
        loginPanel = new LoginPanel();
        loginPanel.setOnLoginSuccess(this::onLoginSuccess);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

        // Start with login panel
        navButton = NavigationButton.builder()
                .tooltip("Shop Arbitrage")
                .icon(icon)
                .priority(5)
                .panel(loginPanel)
                .build();

        clientToolbar.addNavigation(navButton);
        log.info("Shop Arbitrage plugin started - awaiting authentication");
    }

    /**
     * Called when login is successful
     */
    private void onLoginSuccess()
    {
        log.info("Authentication successful - initializing main panel");
        isAuthenticated = true;

        SwingUtilities.invokeLater(() -> {
            // Initialize main panel with prediction services
            mainPanel = new MainPanelV2(
                    itemManager,
                    clientThread,
                    wikiPriceService,
                    priceHistoryService,
                    flipScorer,
                    sessionManager,
                    config
            );

            // Remove old nav button and create new one with main panel
            clientToolbar.removeNavigation(navButton);

            final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

            navButton = NavigationButton.builder()
                    .tooltip("Shop Arbitrage")
                    .icon(icon)
                    .priority(5)
                    .panel(mainPanel)
                    .build();

            clientToolbar.addNavigation(navButton);

            // Initialize the main panel (fetch data, etc.)
            mainPanel.init();

            log.info("Main panel initialized and displayed");
        });
    }

    @Override
    protected void shutDown() throws Exception
    {
        // Save price history on shutdown (only if authenticated)
        if (isAuthenticated && priceHistoryService != null)
        {
            priceHistoryService.saveHistory();
        }

        clientToolbar.removeNavigation(navButton);
        loginPanel = null;
        mainPanel = null;
        isAuthenticated = false;
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        // Only track GE offers if authenticated
        if (isAuthenticated)
        {
            sessionManager.onGrandExchangeOfferChanged(event);
        }
    }

    @Provides
    ShopArbitrageConfigV2 provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ShopArbitrageConfigV2.class);
    }
}