package com.margeon.shoparbitrage;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ShopArbitragePanel extends PluginPanel
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShopArbitragePanel.class);

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final WikiPriceService wikiPriceService;
    private final FlippingSessionManager sessionManager;
    private final ShopArbitrageConfig config;
    private final ShopDataLoader dataLoader;

    private final JPanel listContainer = new JPanel();
    private final JLabel statusLabel = new JLabel("Click refresh to scan");

    // Filter/Sort controls
    private final JComboBox<String> sortDropdown = new JComboBox<>(new String[]{
            "Profit (High → Low)",
            "Profit (Low → High)",
            "Distance (Near → Far)",
            "Distance (Far → Near)",
            "Name (A → Z)"
    });
    
    // Category filter
    private final JComboBox<String> categoryDropdown = new JComboBox<>();
    
    // Checkboxes
    private final JCheckBox stackableOnlyCheckbox = new JCheckBox("Stackable Only");
    private final JCheckBox hideWildernessCheckbox = new JCheckBox("Hide Wilderness");
    private final JCheckBox membersOnlyCheckbox = new JCheckBox("Members Only");

    // Route planning
    private final List<ShopCardPanel> shopCards = new ArrayList<>();
    private final JButton planRouteButton = new JButton("Plan Route");

    // Cached results for re-filtering without API calls
    private List<ShopResult> cachedResults = new ArrayList<>();
    private Map<Integer, Integer> cachedItemPrices = new HashMap<>();
    private Set<String> availableCategories = new TreeSet<>();

    // UI Assets
    private static final ImageIcon REFRESH_ICON;
    private static final ImageIcon REFRESH_HOVER_ICON;

    static
    {
        final BufferedImage refreshImg = ImageUtil.loadImageResource(ShopArbitragePanel.class, "/icon.png");
        REFRESH_ICON = new ImageIcon(refreshImg);
        REFRESH_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(refreshImg, 0.53f));
    }

    public ShopArbitragePanel(ItemManager itemManager, ClientThread clientThread,
                              WikiPriceService wikiPriceService, FlippingSessionManager sessionManager,
                              ShopArbitrageConfig config)
    {
        super(false);
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.wikiPriceService = wikiPriceService;
        this.sessionManager = sessionManager;
        this.config = config;
        this.dataLoader = new ShopDataLoader();

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(createHeader(), BorderLayout.NORTH);

        listContainer.setLayout(new GridBagLayout());
        listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scrollPane.getVerticalScrollBar().setBorder(new EmptyBorder(0, 5, 0, 0));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void init()
    {
        refreshShopData();
    }

    private JPanel createHeader()
    {
        JPanel headerContainer = new JPanel();
        headerContainer.setLayout(new BoxLayout(headerContainer, BoxLayout.Y_AXIS));
        headerContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerContainer.setBorder(new EmptyBorder(10, 10, 5, 10));

        // Top row: Title + About Button + Refresh
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Shop Arbitrage");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        // Right side panel for buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // About button
        JButton aboutBtn = new JButton("?");
        aboutBtn.setFont(FontManager.getRunescapeBoldFont());
        aboutBtn.setFocusPainted(false);
        aboutBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        aboutBtn.setForeground(Color.WHITE);
        aboutBtn.setPreferredSize(new Dimension(30, 25));
        aboutBtn.setToolTipText("About this plugin");
        aboutBtn.addActionListener(e -> showAboutDialog());

        JLabel refreshBtn = new JLabel(REFRESH_ICON);
        refreshBtn.setToolTipText("Refresh Prices & Profits");
        refreshBtn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                refreshShopData();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                refreshBtn.setIcon(REFRESH_HOVER_ICON);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                refreshBtn.setIcon(REFRESH_ICON);
            }
        });

        buttonPanel.add(aboutBtn);
        buttonPanel.add(refreshBtn);

        titleRow.add(title, BorderLayout.CENTER);
        titleRow.add(buttonPanel, BorderLayout.EAST);

        // Category filter row
        JPanel categoryRow = new JPanel(new BorderLayout(5, 0));
        categoryRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        categoryRow.setBorder(new EmptyBorder(5, 0, 5, 0));

        JLabel categoryLabel = new JLabel("Category:");
        categoryLabel.setForeground(Color.LIGHT_GRAY);
        categoryLabel.setFont(FontManager.getRunescapeSmallFont());

        categoryDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        categoryDropdown.setForeground(Color.WHITE);
        categoryDropdown.setFont(FontManager.getRunescapeSmallFont());
        categoryDropdown.addItem("All Categories");
        categoryDropdown.addActionListener(e -> applyFiltersAndSort());

        categoryRow.add(categoryLabel, BorderLayout.WEST);
        categoryRow.add(categoryDropdown, BorderLayout.CENTER);

        // Sort row
        JPanel sortRow = new JPanel(new BorderLayout(5, 0));
        sortRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sortRow.setBorder(new EmptyBorder(2, 0, 5, 0));

        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setForeground(Color.LIGHT_GRAY);
        sortLabel.setFont(FontManager.getRunescapeSmallFont());

        sortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortDropdown.setForeground(Color.WHITE);
        sortDropdown.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.addActionListener(e -> applyFiltersAndSort());

        sortRow.add(sortLabel, BorderLayout.WEST);
        sortRow.add(sortDropdown, BorderLayout.CENTER);

        // Checkbox row 1
        JPanel checkboxRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        checkboxRow1.setBackground(ColorScheme.DARK_GRAY_COLOR);

        stackableOnlyCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stackableOnlyCheckbox.setForeground(Color.LIGHT_GRAY);
        stackableOnlyCheckbox.setFont(FontManager.getRunescapeSmallFont());
        stackableOnlyCheckbox.setToolTipText("Only show shops with stackable items (runes, arrows, etc.)");
        stackableOnlyCheckbox.addActionListener(e -> applyFiltersAndSort());

        hideWildernessCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        hideWildernessCheckbox.setForeground(Color.LIGHT_GRAY);
        hideWildernessCheckbox.setFont(FontManager.getRunescapeSmallFont());
        hideWildernessCheckbox.setToolTipText("Hide shops located in the Wilderness");
        hideWildernessCheckbox.setSelected(true); // Default to hiding wilderness
        hideWildernessCheckbox.addActionListener(e -> applyFiltersAndSort());

        checkboxRow1.add(stackableOnlyCheckbox);
        checkboxRow1.add(hideWildernessCheckbox);

        // Route planning button row
        JPanel routeRow = new JPanel(new BorderLayout());
        routeRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        routeRow.setBorder(new EmptyBorder(5, 0, 5, 0));

        planRouteButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        planRouteButton.setForeground(Color.WHITE);
        planRouteButton.setFont(FontManager.getRunescapeSmallFont());
        planRouteButton.setFocusPainted(false);
        planRouteButton.setToolTipText("Plan optimal route through selected shops");
        planRouteButton.addActionListener(e -> openRoutePlanner());

        routeRow.add(planRouteButton, BorderLayout.CENTER);

        // Status label
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setBorder(new EmptyBorder(5, 0, 0, 0));

        headerContainer.add(titleRow);
        headerContainer.add(categoryRow);
        headerContainer.add(sortRow);
        headerContainer.add(checkboxRow1);
        headerContainer.add(routeRow);
        headerContainer.add(statusLabel);

        return headerContainer;
    }

    public void refreshShopData()
    {
        statusLabel.setText("Fetching prices...");
        log.info("Starting shop data refresh");

        wikiPriceService.fetchLivePrices(() -> {
            log.info("Wiki prices fetched successfully, calculating profits");

            clientThread.invoke(() -> {
                try
                {
                    calculateAndDisplayResults();
                }
                catch (Exception e)
                {
                    log.error("Error calculating shop results", e);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: " + e.getMessage());
                    });
                }
            });
        });

        // Timeout check
        new Thread(() -> {
            try
            {
                Thread.sleep(10000);
                SwingUtilities.invokeLater(() -> {
                    if (statusLabel.getText().equals("Fetching prices..."))
                    {
                        statusLabel.setText("Warning: Price fetch timed out. Check logs.");
                        log.warn("Shop refresh timed out - Wiki API may be down");
                    }
                });
            }
            catch (InterruptedException e)
            {
                // Ignore
            }
        }).start();
    }

    private void calculateAndDisplayResults()
    {
        log.info("Loading shop data from JSON");
        List<ShopData> shops = dataLoader.loadShopData();

        if (shops == null || shops.isEmpty())
        {
            log.error("No shop data loaded - ShopData.json may be missing or invalid");
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Error: No shop data found. Check ShopData.json");
            });
            return;
        }

        log.info("Loaded {} shops, calculating profits", shops.size());
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Calculating profits for " + shops.size() + " shops...");
        });

        // Collect categories
        availableCategories.clear();
        for (ShopData shop : shops)
        {
            if (shop.getCategory() != null && !shop.getCategory().isEmpty())
            {
                availableCategories.add(shop.getCategory());
            }
        }

        // Update category dropdown on EDT
        SwingUtilities.invokeLater(() -> {
            String currentSelection = (String) categoryDropdown.getSelectedItem();
            categoryDropdown.removeAllItems();
            categoryDropdown.addItem("All Categories");
            for (String cat : availableCategories)
            {
                categoryDropdown.addItem(formatCategoryName(cat));
            }
            // Restore selection if still valid
            if (currentSelection != null)
            {
                for (int i = 0; i < categoryDropdown.getItemCount(); i++)
                {
                    if (categoryDropdown.getItemAt(i).equals(currentSelection))
                    {
                        categoryDropdown.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });

        List<ShopResult> results = new ArrayList<>();
        int shopsProcessed = 0;
        int shopsWithProfit = 0;

        for (ShopData shop : shops)
        {
            shopsProcessed++;

            if (shop == null || shop.getItems() == null || shop.getItems().isEmpty())
            {
                log.debug("Skipping shop with no items: {}", shop != null ? shop.getName() : "unknown");
                continue;
            }

            log.debug("Processing shop: {}", shop.getName());

            long shopTotalProfit = 0;
            long shopTripProfit = 0;
            int profitableItems = 0;

            for (ShopItemData item : shop.getItems())
            {
                if (item == null) continue;

                try
                {
                    long itemHourly = calculateItemProfit(shop, item);

                    if (itemHourly > 0)
                    {
                        profitableItems++;
                        shopTotalProfit += itemHourly;
                        log.debug("  {} is profitable: {} gp/hr", item.itemName, itemHourly);
                    }

                    // Calculate trip profit
                    WikiPriceService.WikiPrice price = wikiPriceService.getPrice(item.itemId);
                    if (price != null && price.high > 0)
                    {
                        int margin = price.high - item.shopPrice;
                        int tax = (int) Math.min(Math.floor(price.high * 0.01), 5000000);
                        int netMargin = margin - tax;
                        long itemTrip = (long) netMargin * 27;

                        if (itemTrip > 0)
                        {
                            shopTripProfit = Math.max(shopTripProfit, itemTrip);
                        }
                    }
                }
                catch (Exception e)
                {
                    log.error("Error calculating profit for item {} in shop {}",
                            item.itemName, shop.getName(), e);
                }
            }

            log.debug("Shop {} - {} profitable items, total profit: {} gp/hr",
                    shop.getName(), profitableItems, shopTotalProfit);

            if (shopTotalProfit > 0)
            {
                boolean hasStackable = false;
                if (shop.getItems() != null)
                {
                    for (ShopItemData item : shop.getItems())
                    {
                        if (item != null && itemManager.getItemComposition(item.itemId).isStackable())
                        {
                            hasStackable = true;
                            break;
                        }
                    }
                }

                shopsWithProfit++;
                results.add(new ShopResult(shop, shopTotalProfit, shopTripProfit, hasStackable));
                log.debug("Added {} to results with {} gp/hr", shop.getName(), shopTotalProfit);
            }
        }

        log.info("Processed {}/{} shops, found {} with profit", shopsProcessed, shops.size(), shopsWithProfit);

        cachedResults = results;

        // Pre-fetch item prices
        Map<Integer, Integer> allItemPrices = new HashMap<>();
        for (ShopResult result : results)
        {
            if (result.shop.getItems() != null)
            {
                for (ShopItemData item : result.shop.getItems())
                {
                    if (item != null && !allItemPrices.containsKey(item.itemId))
                    {
                        try
                        {
                            int price = itemManager.getItemPrice(item.itemId);
                            allItemPrices.put(item.itemId, price);
                        }
                        catch (Exception e)
                        {
                            log.warn("Failed to get price for item {}: {}", item.itemId, e.getMessage());
                            allItemPrices.put(item.itemId, 0);
                        }
                    }
                }
            }
        }

        log.info("Pre-fetched prices for {} items", allItemPrices.size());
        cachedItemPrices = allItemPrices;

        SwingUtilities.invokeLater(this::applyFiltersAndSort);
    }

    private String formatCategoryName(String category)
    {
        if (category == null) return "Unknown";
        // Convert RUNE_SHOP to "Rune Shop", CHARTER_SHIP to "Charter Ship", etc.
        String[] parts = category.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts)
        {
            if (sb.length() > 0) sb.append(" ");
            sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return sb.toString();
    }

    private String getCategoryFromDisplayName(String displayName)
    {
        if (displayName == null || displayName.equals("All Categories")) return null;
        // Convert "Rune Shop" back to "RUNE_SHOP"
        return displayName.toUpperCase().replace(" ", "_");
    }

    private long calculateItemProfit(ShopData shop, ShopItemData item)
    {
        WikiPriceService.WikiPrice price = wikiPriceService.getPrice(item.itemId);
        if (price == null)
        {
            log.debug("No price data for item: {} (ID: {})", item.itemName, item.itemId);
            return 0;
        }

        if (price.high <= 0)
        {
            log.debug("Item {} has invalid high price: {}", item.itemName, price.high);
            return 0;
        }

        int gePrice = price.high;
        int margin = gePrice - item.shopPrice;
        int tax = (int) Math.min(Math.floor(gePrice * 0.01), 5000000);
        int netMargin = margin - tax;

        if (netMargin <= 0)
        {
            return 0;
        }

        long hourly = ProfitCalculator.calculateHourlyProfit(
                itemManager,
                item.itemId,
                netMargin,
                item.quantity,
                shop.getDistanceToBank()
        );

        return hourly;
    }

    private void applyFiltersAndSort()
    {
        if (cachedResults.isEmpty())
        {
            log.debug("No cached results to filter/sort");
            return;
        }

        log.debug("Applying filters and sort to {} cached shops", cachedResults.size());

        List<ShopResult> filtered = new ArrayList<>();

        int minProfit = config.minProfitFilter();
        int maxDistance = config.maxDistanceFilter();
        boolean stackableOnly = stackableOnlyCheckbox.isSelected();
        boolean hideWilderness = hideWildernessCheckbox.isSelected();
        String selectedCategory = getCategoryFromDisplayName((String) categoryDropdown.getSelectedItem());

        for (ShopResult result : cachedResults)
        {
            // Min profit filter
            if (result.totalProfit < minProfit)
            {
                continue;
            }

            // Max distance filter (0 = no limit)
            if (maxDistance > 0 && result.shop.getDistanceToBank() > maxDistance)
            {
                continue;
            }

            // Stackable only filter
            if (stackableOnly && !result.hasStackableItems)
            {
                continue;
            }

            // Wilderness filter
            if (hideWilderness && result.shop.isWilderness())
            {
                continue;
            }

            // Category filter
            if (selectedCategory != null && !result.shop.matchesCategory(selectedCategory))
            {
                continue;
            }

            filtered.add(result);
        }

        log.debug("Filtered down to {} shops", filtered.size());

        // Sort
        String sortOption = (String) sortDropdown.getSelectedItem();

        if ("Profit (High → Low)".equals(sortOption))
        {
            filtered.sort((r1, r2) -> Long.compare(r2.totalProfit, r1.totalProfit));
        }
        else if ("Profit (Low → High)".equals(sortOption))
        {
            filtered.sort((r1, r2) -> Long.compare(r1.totalProfit, r2.totalProfit));
        }
        else if ("Distance (Near → Far)".equals(sortOption))
        {
            filtered.sort((r1, r2) -> Integer.compare(r1.shop.getDistanceToBank(), r2.shop.getDistanceToBank()));
        }
        else if ("Distance (Far → Near)".equals(sortOption))
        {
            filtered.sort((r1, r2) -> Integer.compare(r2.shop.getDistanceToBank(), r1.shop.getDistanceToBank()));
        }
        else if ("Name (A → Z)".equals(sortOption))
        {
            filtered.sort((r1, r2) -> r1.shop.getName().compareTo(r2.shop.getName()));
        }

        updateShopList(filtered, cachedItemPrices);
    }

    private void updateShopList(List<ShopResult> results, Map<Integer, Integer> itemPrices)
    {
        listContainer.removeAll();
        shopCards.clear();
        statusLabel.setText("Found " + results.size() + " profitable shops");

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 8, 0);

        for (ShopResult result : results)
        {
            try
            {
                if (result != null && result.shop != null)
                {
                    ShopCardPanel card = new ShopCardPanel(
                            result.shop,
                            itemManager,
                            result.totalProfit,
                            result.tripProfit,
                            itemPrices,
                            config
                    );

                    listContainer.add(card, c);
                    shopCards.add(card);
                    c.gridy++;
                }
            }
            catch (Exception e)
            {
                log.error("Failed to create shop card for {}",
                        result != null && result.shop != null ? result.shop.getName() : "unknown", e);
            }
        }

        c.weighty = 1;
        listContainer.add(new JPanel(), c);

        listContainer.revalidate();
        listContainer.repaint();
    }

    private void openRoutePlanner()
    {
        List<ShopData> selectedShops = new ArrayList<>();
        for (ShopCardPanel card : shopCards)
        {
            if (card.isSelected())
            {
                selectedShops.add(card.getShopData());
            }
        }

        if (selectedShops.isEmpty())
        {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select at least one shop by checking the boxes.",
                    "No Shops Selected",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        log.info("Planning route for {} selected shops", selectedShops.size());

        clientThread.invoke(() -> {
            try
            {
                net.runelite.api.coords.WorldPoint startLocation =
                        new net.runelite.api.coords.WorldPoint(3253, 3420, 0);

                RoutePlanner.PlannedRoute route = RoutePlanner.calculateRoute(selectedShops, startLocation);

                SwingUtilities.invokeLater(() -> displayRoute(route));
            }
            catch (Exception e)
            {
                log.error("Error calculating route", e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            this,
                            "Error calculating route: " + e.getMessage(),
                            "Route Planning Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        });
    }

    private void displayRoute(RoutePlanner.PlannedRoute route)
    {
        JFrame routeFrame = new JFrame("Optimal Shop Route");
        routeFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        RoutePanel routePanel = new RoutePanel(itemManager, clientThread);
        routePanel.displayRoute(route);

        routeFrame.add(routePanel);
        routeFrame.setSize(400, 600);
        routeFrame.setLocationRelativeTo(this);
        routeFrame.setVisible(true);
    }

    private void showAboutDialog()
    {
        JDialog aboutDialog = new JDialog();
        aboutDialog.setTitle("About Shop Arbitrage");
        aboutDialog.setModal(true);
        aboutDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        AboutPanel aboutPanel = new AboutPanel();
        aboutDialog.add(aboutPanel);

        aboutDialog.pack();
        aboutDialog.setLocationRelativeTo(this);
        aboutDialog.setVisible(true);
    }

    // Helper class for results
    private static class ShopResult
    {
        ShopData shop;
        long totalProfit;
        long tripProfit;
        boolean hasStackableItems;

        public ShopResult(ShopData shop, long totalProfit, long tripProfit, boolean hasStackableItems)
        {
            this.shop = shop;
            this.totalProfit = totalProfit;
            this.tripProfit = tripProfit;
            this.hasStackableItems = hasStackableItems;
        }
    }
}
