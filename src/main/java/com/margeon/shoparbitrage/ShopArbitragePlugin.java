package com.margeon.shoparbitrage;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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
        description = "Finds profitable shop items to flip on the GE",
        tags = {"shop", "money", "profit", "arbitrage"}
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
    private ShopArbitrageConfig config;

    @Inject
    private WikiPriceService wikiPriceService;

    @Inject
    private FlippingSessionManager sessionManager;

    private MainPanel mainPanel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        // Initialize the Main Panel (which holds Shop, Flip, and History tabs)
        mainPanel = new MainPanel(itemManager, clientThread, wikiPriceService, sessionManager, config);
        mainPanel.init();

        // Load the icon for the sidebar
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
        clientToolbar.removeNavigation(navButton);
        mainPanel = null;
    }

    @Provides
    ShopArbitrageConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ShopArbitrageConfig.class);
    }
}