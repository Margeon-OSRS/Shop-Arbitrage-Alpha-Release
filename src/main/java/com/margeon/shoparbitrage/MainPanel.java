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

public class MainPanel extends PluginPanel
{
    private final ShopArbitragePanel shopPanel;
    private final FlippingPanel flippingPanel;
    private final SlayerPanel slayerPanel; // NEW

    public MainPanel(ItemManager itemManager, ClientThread clientThread,
                     WikiPriceService wikiPriceService, FlippingSessionManager sessionManager,
                     ShopArbitrageConfig config)
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Initialize sub-panels
        shopPanel = new ShopArbitragePanel(itemManager, clientThread, wikiPriceService, sessionManager, config);

        // FlippingPanel with config so it can use minDailyVolume and resultLimit
        flippingPanel = new FlippingPanel(itemManager, clientThread, wikiPriceService, sessionManager, config);

        // NEW: Slayer profit panel
        slayerPanel = new SlayerPanel(itemManager, clientThread, wikiPriceService, config);

        // --- TAB CONTAINER ---
        JPanel tabContainer = new JPanel(new BorderLayout());
        tabContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        tabContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // --- DISPLAY AREA ---
        JPanel display = new JPanel();
        display.setBorder(new EmptyBorder(10, 0, 0, 0));
        display.setBackground(ColorScheme.DARK_GRAY_COLOR);
        display.setLayout(new BorderLayout());

        // --- TABS (3 TABS NOW) ---
        MaterialTabGroup tabGroup = new MaterialTabGroup(display);

        // 1. SHOPS TAB
        MaterialTab shopTab = new MaterialTab(
                "Shops",
                tabGroup,
                shopPanel
        );

        // 2. FLIPPING TAB
        MaterialTab flipTab = new MaterialTab(
                "Flipping",
                tabGroup,
                flippingPanel
        );

        // 3. SLAYER TAB (NEW!)
        MaterialTab slayerTab = new MaterialTab(
                "Slayer",
                tabGroup,
                slayerPanel
        );

        // Add tabs to group
        tabGroup.addTab(shopTab);
        tabGroup.addTab(flipTab);
        tabGroup.addTab(slayerTab);

        // Select default
        tabGroup.select(shopTab);

        // Add components to main panel
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