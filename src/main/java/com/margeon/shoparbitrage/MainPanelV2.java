package com.margeon.shoparbitrage;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainPanelV2 extends PluginPanel
{
    private final ShopArbitragePanel shopPanel;
    private final FlippingPanelV2 flippingPanel;  // V2
    private final SlayerPanel slayerPanel;

    public MainPanelV2(ItemManager itemManager, ClientThread clientThread,
                       WikiPriceService wikiPriceService,
                       PriceHistoryService priceHistoryService,
                       FlipScorer flipScorer,
                       FlippingSessionManager sessionManager,
                       ShopArbitrageConfigV2 config)
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Initialize sub-panels
        shopPanel = new ShopArbitragePanel(itemManager, clientThread, wikiPriceService, sessionManager, config);

        // Use V2 flipping panel with prediction services
        flippingPanel = new FlippingPanelV2(
                itemManager,
                clientThread,
                wikiPriceService,
                priceHistoryService,
                flipScorer,
                sessionManager,
                config
        );

        slayerPanel = new SlayerPanel(itemManager, clientThread, wikiPriceService, config);

        // Tab container
        JPanel tabContainer = new JPanel(new BorderLayout());
        tabContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        tabContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel display = new JPanel();
        display.setBorder(new EmptyBorder(10, 0, 0, 0));
        display.setBackground(ColorScheme.DARK_GRAY_COLOR);
        display.setLayout(new BorderLayout());

        MaterialTabGroup tabGroup = new MaterialTabGroup(display);

        MaterialTab shopTab = new MaterialTab("Shops", tabGroup, shopPanel);
        MaterialTab flipTab = new MaterialTab("Predict", tabGroup, flippingPanel);  // Renamed
        MaterialTab slayerTab = new MaterialTab("Slayer", tabGroup, slayerPanel);

        tabGroup.addTab(shopTab);
        tabGroup.addTab(flipTab);
        tabGroup.addTab(slayerTab);

        tabGroup.select(flipTab);  // Default to prediction tab

        tabContainer.add(tabGroup, BorderLayout.CENTER);
        add(tabContainer, BorderLayout.NORTH);
        add(display, BorderLayout.CENTER);
    }

    public void init()
    {
        try { shopPanel.init(); } catch (Exception ignored) {}
        try { flippingPanel.init(); } catch (Exception ignored) {}
        try { slayerPanel.init(); } catch (Exception ignored) {}
    }
}