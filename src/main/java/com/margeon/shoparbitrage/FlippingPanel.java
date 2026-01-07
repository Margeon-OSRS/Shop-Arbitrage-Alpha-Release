package com.margeon.shoparbitrage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlippingPanel extends PluginPanel
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlippingPanel.class);

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final WikiPriceService wikiPriceService;
    private final FlippingSessionManager sessionManager;
    private final ShopArbitrageConfig config; // ADDED
    private final Gson gson;

    // UI Components
    private final JPanel cardPanel = new JPanel(new CardLayout());
    private final JPanel watchlistPanel = new JPanel(new BorderLayout());
    private final JPanel scannerPanel = new JPanel(new BorderLayout());

    private final JPanel watchlistContainer = new JPanel();
    private final IconTextField searchBar = new IconTextField();
    private final List<FlipItem> watchList = new ArrayList<>();

    private final JPanel scannerContainer = new JPanel();
    private final JLabel scannerStatus = new JLabel("Click Scan to find flips");
    private final JTextField maxCashInput = new JTextField("100M");

    private final JLabel sessionProfitLabel = new JLabel("Session Profit: 0 gp");
    private final JButton viewHistoryButton = new JButton("View History");

    private static final File WATCHLIST_FILE = new File(RuneLite.RUNELITE_DIR, "shop-arbitrage-watchlist.json");

    // UPDATED CONSTRUCTOR - Added config parameter
    public FlippingPanel(ItemManager itemManager, ClientThread clientThread,
                         WikiPriceService wikiPriceService, FlippingSessionManager sessionManager,
                         ShopArbitrageConfig config)
    {
        super(false);
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.wikiPriceService = wikiPriceService;
        this.sessionManager = sessionManager;
        this.config = config; // ADDED
        this.gson = new Gson();

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Session Profit Header with History Button
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(5, 10, 5, 10));

        sessionProfitLabel.setFont(FontManager.getRunescapeSmallFont());
        sessionProfitLabel.setHorizontalAlignment(SwingConstants.CENTER);
        sessionProfitLabel.setForeground(Color.YELLOW);

        // View History Button
        viewHistoryButton.setFont(FontManager.getRunescapeSmallFont());
        viewHistoryButton.setFocusPainted(false);
        viewHistoryButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        viewHistoryButton.setForeground(Color.WHITE);
        viewHistoryButton.setToolTipText("View your trade history");
        viewHistoryButton.addActionListener(e -> openHistoryWindow());

        statsPanel.add(sessionProfitLabel, BorderLayout.CENTER);
        statsPanel.add(viewHistoryButton, BorderLayout.EAST);

        // Top Navigation
        JPanel navBar = new JPanel(new GridLayout(1, 2));
        navBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JButton btnWatchlist = createNavButton("Watchlist");
        JButton btnScanner = createNavButton("Scanner");

        btnWatchlist.addActionListener(e -> showCard("WATCHLIST", btnWatchlist, btnScanner));
        btnScanner.addActionListener(e -> showCard("SCANNER", btnScanner, btnWatchlist));

        navBar.add(btnWatchlist);
        navBar.add(btnScanner);

        // Wrap navbar and stats
        JPanel topWrapper = new JPanel(new BorderLayout());
        topWrapper.add(statsPanel, BorderLayout.NORTH);
        topWrapper.add(navBar, BorderLayout.SOUTH);

        add(topWrapper, BorderLayout.NORTH);

        // Initialize Panels
        initWatchlistUI();
        initScannerUI();

        cardPanel.add(watchlistPanel, "WATCHLIST");
        cardPanel.add(scannerPanel, "SCANNER");
        add(cardPanel, BorderLayout.CENTER);

        // Default View
        showCard("WATCHLIST", btnWatchlist, btnScanner);
        loadList();

        // Hook up the live update
        sessionManager.addListener(this::updateProfitUI);
    }

    private void updateProfitUI()
    {
        SwingUtilities.invokeLater(() -> {
            long p = sessionManager.getSessionProfit();
            sessionProfitLabel.setText("Session Profit: " + QuantityFormatter.quantityToStackSize(p) + " gp");
            sessionProfitLabel.setForeground(p >= 0 ? Color.GREEN : Color.RED);
        });
    }

    private JButton createNavButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(Color.GRAY);
        btn.setBorder(new EmptyBorder(8,0,8,0));
        return btn;
    }

    private void showCard(String name, JButton active, JButton inactive)
    {
        CardLayout cl = (CardLayout) cardPanel.getLayout();
        cl.show(cardPanel, name);
        active.setForeground(Color.WHITE);
        active.setFont(FontManager.getRunescapeBoldFont());
        inactive.setForeground(Color.GRAY);
        inactive.setFont(FontManager.getRunescapeSmallFont());
    }

    private void initWatchlistUI()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.addActionListener(e -> addItemToWatchlist(searchBar.getText()));

        header.add(searchBar, BorderLayout.CENTER);
        watchlistPanel.add(header, BorderLayout.NORTH);

        watchlistContainer.setLayout(new BoxLayout(watchlistContainer, BoxLayout.Y_AXIS));
        watchlistContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(watchlistContainer);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        watchlistPanel.add(scroll, BorderLayout.CENTER);
    }

    private void initScannerUI()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        inputPanel.setBorder(new EmptyBorder(0, 0, 5, 0));

        JLabel maxCashLabel = new JLabel("Max Item Price:");
        maxCashLabel.setFont(FontManager.getRunescapeSmallFont());
        maxCashLabel.setForeground(Color.LIGHT_GRAY);

        maxCashInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        maxCashInput.setForeground(Color.WHITE);
        maxCashInput.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        maxCashInput.setToolTipText("e.g. 10m, 500k, 1b");

        inputPanel.add(maxCashLabel, BorderLayout.WEST);
        inputPanel.add(maxCashInput, BorderLayout.CENTER);

        JButton scanBtn = new JButton("Scan Market");
        scanBtn.setFocusPainted(false);
        scanBtn.addActionListener(e -> runScanner());

        scannerStatus.setHorizontalAlignment(SwingConstants.CENTER);
        scannerStatus.setBorder(new EmptyBorder(5, 0, 0, 0));
        scannerStatus.setForeground(Color.GRAY);

        JPanel headerContent = new JPanel(new BorderLayout());
        headerContent.setBackground(ColorScheme.DARK_GRAY_COLOR);

        headerContent.add(inputPanel, BorderLayout.NORTH);
        headerContent.add(scanBtn, BorderLayout.CENTER);
        headerContent.add(scannerStatus, BorderLayout.SOUTH);

        header.add(headerContent, BorderLayout.CENTER);
        scannerPanel.add(header, BorderLayout.NORTH);

        scannerContainer.setLayout(new BoxLayout(scannerContainer, BoxLayout.Y_AXIS));
        scannerContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(scannerContainer);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scannerPanel.add(scroll, BorderLayout.CENTER);
    }

    // UPDATED: Now uses config values for filtering
    private void runScanner()
    {
        scannerStatus.setText("Scanning...");
        scannerStatus.setForeground(Color.YELLOW);
        scannerContainer.removeAll();

        long maxPrice = parseValue(maxCashInput.getText());

        // Get config values
        int minVolume = config.minDailyVolume();
        int resultLimit = config.resultLimit();

        log.info("Running scanner with maxPrice={}, minVolume={}, resultLimit={}",
                maxPrice, minVolume, resultLimit);

        wikiPriceService.fetchLivePrices(() -> {
            Map<Integer, WikiPriceService.WikiPrice> allPrices = wikiPriceService.getAllPrices();
            List<FlipItem> opportunities = new ArrayList<>();

            int totalItems = 0;
            int filteredByPrice = 0;
            int filteredByVolume = 0;
            int filteredByProfit = 0;

            for (Map.Entry<Integer, WikiPriceService.WikiPrice> entry : allPrices.entrySet())
            {
                WikiPriceService.WikiPrice p = entry.getValue();
                totalItems++;

                // FIXED: Check if item meets criteria
                if (p.high > 0 && p.low > 0 && p.low <= maxPrice)
                {
                    // NEW: Filter by daily volume from config
                    long dailyVolume = p.getDailyVolume();
                    if (dailyVolume < minVolume)
                    {
                        filteredByVolume++;
                        continue;
                    }

                    int margin = p.high - p.low;
                    int tax = (int) Math.min(Math.floor(p.high * 0.01), 5000000);
                    int profit = margin - tax;

                    if (profit > 500)
                    {
                        FlipItem item = new FlipItem("", entry.getKey());
                        item.highPrice = p.high;
                        item.lowPrice = p.low;
                        item.profit = profit;
                        item.volume = dailyVolume; // Store volume for display
                        opportunities.add(item);
                    }
                    else
                    {
                        filteredByProfit++;
                    }
                }
                else
                {
                    filteredByPrice++;
                }
            }

            log.info("Scanner results: total={}, filtered by price={}, volume={}, profit={}, opportunities={}",
                    totalItems, filteredByPrice, filteredByVolume, filteredByProfit, opportunities.size());

            opportunities.sort((i1, i2) -> Integer.compare(i2.profit, i1.profit));

            // FIXED: Use config.resultLimit() instead of hardcoded 20
            List<FlipItem> topResults = opportunities.subList(0, Math.min(opportunities.size(), resultLimit));

            clientThread.invokeLater(() -> {
                for (FlipItem item : topResults)
                {
                    try
                    {
                        item.name = itemManager.getItemComposition(item.id).getName();
                    }
                    catch (Exception e)
                    {
                        log.warn("Failed to get item name for ID {}: {}", item.id, e.getMessage());
                        item.name = "Unknown (" + item.id + ")";
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    scannerContainer.removeAll();
                    if (topResults.isEmpty())
                    {
                        scannerStatus.setText("No items found matching criteria");
                        scannerStatus.setForeground(Color.RED);
                    }
                    else
                    {
                        scannerStatus.setText("Top " + topResults.size() + " Flips (Under " + maxCashInput.getText() + ")");
                        scannerStatus.setForeground(Color.GREEN);
                        for (FlipItem item : topResults)
                        {
                            scannerContainer.add(createRow(item, false));
                            scannerContainer.add(Box.createRigidArea(new Dimension(0, 5)));
                        }
                    }
                    scannerContainer.revalidate();
                    scannerContainer.repaint();
                });
            });
        });
    }

    private long parseValue(String input)
    {
        if (input == null || input.isEmpty()) return Long.MAX_VALUE;
        String clean = input.toLowerCase().replaceAll("[^0-9.kmbt]", "");
        double multiplier = 1;
        if (clean.endsWith("k")) multiplier = 1_000;
        else if (clean.endsWith("m")) multiplier = 1_000_000;
        else if (clean.endsWith("b")) multiplier = 1_000_000_000;

        String number = clean.replaceAll("[^0-9.]", "");
        try
        {
            return (long) (Double.parseDouble(number) * multiplier);
        }
        catch (Exception e)
        {
            return Long.MAX_VALUE;
        }
    }

    private JPanel createRow(FlipItem item, boolean allowDelete)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(5, 5, 5, 5));
        row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 55));

        int margin = item.highPrice - item.lowPrice;
        // int tax = (int) Math.min(Math.floor(item.highPrice * 0.01), 5000000); // Unused in display, calculated for tooltip

        String tooltip = "<html>"
                + "<b>" + item.name + "</b><br>"
                + "Sell: " + QuantityFormatter.formatNumber(item.highPrice) + " gp<br>"
                + "Buy: " + QuantityFormatter.formatNumber(item.lowPrice) + " gp<br>"
                + "Margin: " + QuantityFormatter.formatNumber(margin) + " gp<br>"
                + "Net Profit: " + QuantityFormatter.formatNumber(item.profit) + " gp";

        if (item.volume > 0)
        {
            tooltip += "<br>Daily Volume: " + QuantityFormatter.formatNumber(item.volume);
        }

        if (!allowDelete) tooltip += "<br><br><b style='color:orange'>Click to add to Watchlist</b>";
        tooltip += "</html>";

        JLabel iconLabel = new JLabel();
        AsyncBufferedImage img = itemManager.getImage(item.id);
        img.addTo(iconLabel);
        JPanel iconWrapper = new JPanel(new BorderLayout());
        iconWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        iconWrapper.setPreferredSize(new Dimension(40, 32));
        iconWrapper.add(iconLabel, BorderLayout.CENTER);

        JPanel financePanel = new JPanel(new GridLayout(2, 1));
        financePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        financePanel.setPreferredSize(new Dimension(85, 35));

        JLabel sellLabel = new JLabel("S: " + QuantityFormatter.quantityToStackSize(item.highPrice));
        sellLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
        sellLabel.setFont(FontManager.getRunescapeSmallFont());
        sellLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel buyLabel = new JLabel("B: " + QuantityFormatter.quantityToStackSize(item.lowPrice));
        buyLabel.setForeground(Color.GRAY);
        buyLabel.setFont(FontManager.getRunescapeSmallFont());
        buyLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        financePanel.add(sellLabel);
        financePanel.add(buyLabel);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoPanel.setBorder(new EmptyBorder(0, 8, 0, 0));

        JLabel nameLabel = new JLabel(item.name);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());

        // Truncate long names to keep UI clean
        nameLabel.setPreferredSize(new Dimension(0, 0));

        JLabel profitLabel = new JLabel("Profit: " + QuantityFormatter.quantityToStackSize(item.profit));
        profitLabel.setForeground(item.profit > 0 ? Color.GREEN : Color.RED);
        profitLabel.setFont(FontManager.getRunescapeSmallFont());

        infoPanel.add(nameLabel);
        infoPanel.add(profitLabel);

        JPanel rightSide = new JPanel(new BorderLayout());
        rightSide.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rightSide.add(financePanel, BorderLayout.CENTER);

        // Set tooltips on everything so it appears wherever you hover
        row.setToolTipText(tooltip);
        nameLabel.setToolTipText(tooltip);
        profitLabel.setToolTipText(tooltip);
        sellLabel.setToolTipText(tooltip);
        buyLabel.setToolTipText(tooltip);
        iconLabel.setToolTipText(tooltip);
        infoPanel.setToolTipText(tooltip); // Add to panels too
        financePanel.setToolTipText(tooltip);
        rightSide.setToolTipText(tooltip);

        if (allowDelete)
        {
            JLabel deleteBtn = new JLabel(" X");
            deleteBtn.setForeground(Color.RED);
            deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            deleteBtn.setBorder(new EmptyBorder(0, 5, 0, 0));
            deleteBtn.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    watchList.remove(item);
                    saveList();
                    rebuildWatchlist();
                }
            });
            rightSide.add(deleteBtn, BorderLayout.EAST);
        }
        else
        {
            // --- FIX START ---
            // Create the listener once
            MouseAdapter addListener = new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (SwingUtilities.isLeftMouseButton(e))
                    {
                        addItemDirectly(item);
                        JOptionPane.showMessageDialog(scannerPanel, "Added " + item.name + " to Watchlist");
                    }
                }
            };

            // Add the listener to the row AND all child panels/labels that cover the row
            row.addMouseListener(addListener);
            infoPanel.addMouseListener(addListener);
            financePanel.addMouseListener(addListener);
            rightSide.addMouseListener(addListener);
            iconWrapper.addMouseListener(addListener);
            nameLabel.addMouseListener(addListener);
            profitLabel.addMouseListener(addListener);
            sellLabel.addMouseListener(addListener);
            buyLabel.addMouseListener(addListener);
            iconLabel.addMouseListener(addListener);
            // --- FIX END ---
        }

        row.add(iconWrapper, BorderLayout.WEST);
        row.add(infoPanel, BorderLayout.CENTER);
        row.add(rightSide, BorderLayout.EAST);

        return row;
    }

    public void init() { refreshPrices(); }

    private void saveList()
    {
        try (Writer writer = new FileWriter(WATCHLIST_FILE))
        {
            gson.toJson(watchList, writer);
        }
        catch (IOException e)
        {
            log.error("Failed to save watchlist", e);
        }
    }

    private void loadList()
    {
        if (!WATCHLIST_FILE.exists())
        {
            initDefaultItems();
            return;
        }
        try (Reader reader = new FileReader(WATCHLIST_FILE))
        {
            Type listType = new TypeToken<ArrayList<FlipItem>>(){}.getType();
            List<FlipItem> loaded = gson.fromJson(reader, listType);
            if (loaded != null)
            {
                watchList.clear();
                watchList.addAll(loaded);
            }
            else
            {
                initDefaultItems();
            }
        }
        catch (IOException e)
        {
            log.error("Failed to load watchlist", e);
            initDefaultItems();
        }
        rebuildWatchlist();
    }

    private void initDefaultItems()
    {
        watchList.add(new FlipItem("Old School Bond", 13190));
        watchList.add(new FlipItem("Zulrah's Scales", 12934));
        saveList();
        refreshPrices();
    }

    private void refreshPrices()
    {
        wikiPriceService.fetchLivePrices(() -> {
            for (FlipItem item : watchList)
            {
                WikiPriceService.WikiPrice realData = wikiPriceService.getPrice(item.id);
                if (realData != null)
                {
                    item.highPrice = realData.high;
                    item.lowPrice = realData.low;
                    int tax = (int) Math.min(Math.floor(item.highPrice * 0.01), 5000000);
                    item.profit = (item.highPrice - item.lowPrice) - tax;
                }
            }
            SwingUtilities.invokeLater(this::rebuildWatchlist);
        });
    }

    private void addItemToWatchlist(String name)
    {
        if (name.isEmpty()) return;
        searchBar.setEditable(false);
        searchBar.setIcon(IconTextField.Icon.LOADING);

        clientThread.invokeLater(() -> {
            try
            {
                List<ItemPrice> results = itemManager.search(name);
                if (results != null && !results.isEmpty())
                {
                    ItemPrice result = results.get(0);
                    FlipItem newItem = new FlipItem(result.getName(), result.getId());
                    addItemDirectly(newItem);
                    SwingUtilities.invokeLater(() -> {
                        searchBar.setText("");
                        searchBar.setEditable(true);
                        searchBar.setIcon(IconTextField.Icon.SEARCH);
                    });
                }
                else
                {
                    SwingUtilities.invokeLater(() -> {
                        searchBar.setEditable(true);
                        searchBar.setIcon(IconTextField.Icon.ERROR);
                    });
                }
            }
            catch (Exception e)
            {
                log.error("Failed to search for item", e);
                SwingUtilities.invokeLater(() -> {
                    searchBar.setEditable(true);
                    searchBar.setIcon(IconTextField.Icon.ERROR);
                });
            }
        });
    }

    private void addItemDirectly(FlipItem item)
    {
        if (watchList.stream().anyMatch(i -> i.id == item.id)) return;
        WikiPriceService.WikiPrice realData = wikiPriceService.getPrice(item.id);
        if (realData != null)
        {
            item.highPrice = realData.high;
            item.lowPrice = realData.low;
            int tax = (int) Math.min(Math.floor(item.highPrice * 0.01), 5000000);
            item.profit = (item.highPrice - item.lowPrice) - tax;
        }
        watchList.add(0, item);
        saveList();
        SwingUtilities.invokeLater(this::rebuildWatchlist);
    }

    private void rebuildWatchlist()
    {
        watchlistContainer.removeAll();
        for (FlipItem item : watchList)
        {
            watchlistContainer.add(createRow(item, true));
            watchlistContainer.add(Box.createRigidArea(new Dimension(0, 5)));
        }
        watchlistContainer.revalidate();
        watchlistContainer.repaint();
    }

    /**
     * Open trade history in a popup window
     */
    private void openHistoryWindow()
    {
        JFrame historyFrame = new JFrame("Trade History");
        historyFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        HistoryPanel historyPanel = new HistoryPanel(itemManager, clientThread, sessionManager);

        historyFrame.add(historyPanel);
        historyFrame.setSize(400, 600);
        historyFrame.setLocationRelativeTo(this);
        historyFrame.setVisible(true);
    }

    private static class FlipItem
    {
        String name;
        int id;
        int highPrice = 0;
        int lowPrice = 0;
        int profit = 0;
        long volume = 0; // NEW: Store daily volume

        FlipItem(String name, int id)
        {
            this.name = name;
            this.id = id;
        }
    }
}