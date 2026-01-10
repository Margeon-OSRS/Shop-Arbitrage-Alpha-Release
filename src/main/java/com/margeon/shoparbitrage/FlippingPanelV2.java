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
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Flipping Panel with comprehensive prediction scores,
 * advanced filtering, and detailed market analysis.
 */
public class FlippingPanelV2 extends PluginPanel
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlippingPanelV2.class);

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final WikiPriceService wikiPriceService;
    private final PriceHistoryService priceHistoryService;
    private final FlipScorer flipScorer;
    private final FlippingSessionManager sessionManager;
    private final ShopArbitrageConfig config;
    private final Gson gson;

    // UI Components - Main Layout
    private final JPanel cardPanel = new JPanel(new CardLayout());
    private final JPanel watchlistPanel = new JPanel(new BorderLayout());
    private final JPanel scannerPanel = new JPanel(new BorderLayout());
    private final JPanel analysisPanel = new JPanel(new BorderLayout());

    // Watchlist
    private final JPanel watchlistContainer = new JPanel();
    private final IconTextField searchBar = new IconTextField();
    private final List<FlipItem> watchList = new ArrayList<>();

    // Scanner
    private final JPanel scannerContainer = new JPanel();
    private final JLabel scannerStatus = new JLabel("Click Scan to find predictions");

    // === BASIC FILTERS ===
    private final JTextField minPriceInput = new JTextField("0");
    private final JTextField maxPriceInput = new JTextField("100M");
    private final JTextField minVolumeInput = new JTextField("500");
    private final JTextField maxVolumeInput = new JTextField("");
    private final JTextField minMarginInput = new JTextField("100");
    private final JTextField minROIInput = new JTextField("0.5");

    // === ADVANCED FILTERS ===
    private final JTextField minScoreInput = new JTextField("30");
    private final JTextField maxVolatilityInput = new JTextField("15");
    private final JTextField minStabilityInput = new JTextField("30");
    private final JTextField maxFlipTimeInput = new JTextField("8");
    private final JTextField minBuyLimitInput = new JTextField("0");

    // RSI Filter
    private final JComboBox<String> rsiFilterDropdown = new JComboBox<>(new String[]{
            "Any RSI",
            "Oversold (< 30)",
            "Slightly Oversold (30-45)",
            "Neutral (45-55)",
            "Slightly Overbought (55-70)",
            "Overbought (> 70)",
            "Buy Zone (< 50)",
            "Sell Zone (> 50)"
    });

    // Trend Filter
    private final JComboBox<String> trendFilterDropdown = new JComboBox<>(new String[]{
            "Any Trend",
            "Strong Uptrend",
            "Uptrend",
            "Sideways",
            "Downtrend",
            "Strong Downtrend",
            "Not Downtrend"
    });

    // Confidence Filter
    private final JComboBox<String> confidenceFilterDropdown = new JComboBox<>(new String[]{
            "Any Confidence",
            "Very High Only",
            "High or Better",
            "Medium or Better",
            "Low or Better"
    });

    // Recommendation Filter
    private final JComboBox<String> recommendationFilterDropdown = new JComboBox<>(new String[]{
            "Any Recommendation",
            "Strong Buy Only",
            "Buy or Better",
            "Consider or Better",
            "Exclude Avoid"
    });

    // Buy Limit Filter
    private final JCheckBox onlyKnownBuyLimitsCheckbox = new JCheckBox("Only Known Buy Limits");

    // Sorting
    private final JComboBox<String> sortDropdown = new JComboBox<>(new String[]{
            "Score (High → Low)",
            "Score (Low → High)",
            "Margin (High → Low)",
            "Margin (Low → High)",
            "ROI (High → Low)",
            "Profit/Hr (High → Low)",
            "Volume (High → Low)",
            "Volume (Low → High)",
            "Volatility (Low → High)",
            "Stability (High → Low)",
            "Flip Time (Low → High)",
            "RSI (Low → High)",
            "Trend (Best → Worst)",
            "Name (A → Z)"
    });

    // Result limit
    private final JTextField resultLimitInput = new JTextField("50");

    // Advanced filters visibility
    private boolean advancedFiltersVisible = false;
    private JPanel advancedFiltersPanel;
    private JButton toggleAdvancedBtn;

    // Cached results for sorting/filtering
    private List<FlipScorer.FlipScore> cachedPredictions = new ArrayList<>();
    private List<FlipScorer.FlipScore> allFetchedResults = new ArrayList<>();

    // Analysis panel
    private final JPanel analysisContainer = new JPanel();
    private FlipScorer.FlipScore selectedItem;

    // Session tracking
    private final JLabel sessionProfitLabel = new JLabel("Session: 0 gp");
    private final JButton viewHistoryButton = new JButton("History");

    // Stats labels
    private final JLabel statsItemsScanned = new JLabel("Items: 0");
    private final JLabel statsMatchingFilters = new JLabel("Matching: 0");
    private final JLabel statsAvgScore = new JLabel("Avg Score: 0");
    private final JLabel statsAvgROI = new JLabel("Avg ROI: 0%");

    private static final File WATCHLIST_FILE = new File(RuneLite.RUNELITE_DIR, "shop-arbitrage-watchlist.json");

    public FlippingPanelV2(ItemManager itemManager, ClientThread clientThread,
                           WikiPriceService wikiPriceService, PriceHistoryService priceHistoryService,
                           FlipScorer flipScorer, FlippingSessionManager sessionManager,
                           ShopArbitrageConfig config)
    {
        super(false);
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.wikiPriceService = wikiPriceService;
        this.priceHistoryService = priceHistoryService;
        this.flipScorer = flipScorer;
        this.sessionManager = sessionManager;
        this.config = config;
        this.gson = new Gson();

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Session Profit Header
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(5, 10, 5, 10));

        sessionProfitLabel.setFont(FontManager.getRunescapeSmallFont());
        sessionProfitLabel.setForeground(Color.YELLOW);

        viewHistoryButton.setFont(FontManager.getRunescapeSmallFont());
        viewHistoryButton.setFocusPainted(false);
        viewHistoryButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        viewHistoryButton.setForeground(Color.WHITE);
        viewHistoryButton.addActionListener(e -> openHistoryWindow());

        statsPanel.add(sessionProfitLabel, BorderLayout.CENTER);
        statsPanel.add(viewHistoryButton, BorderLayout.EAST);

        // Navigation tabs
        JPanel navBar = new JPanel(new GridLayout(1, 3));
        navBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JButton btnWatchlist = createNavButton("Watch");
        JButton btnScanner = createNavButton("Predict");
        JButton btnAnalysis = createNavButton("Analyze");

        btnWatchlist.addActionListener(e -> showCard("WATCHLIST", btnWatchlist, btnScanner, btnAnalysis));
        btnScanner.addActionListener(e -> showCard("SCANNER", btnScanner, btnWatchlist, btnAnalysis));
        btnAnalysis.addActionListener(e -> showCard("ANALYSIS", btnAnalysis, btnWatchlist, btnScanner));

        navBar.add(btnWatchlist);
        navBar.add(btnScanner);
        navBar.add(btnAnalysis);

        JPanel topWrapper = new JPanel(new BorderLayout());
        topWrapper.add(statsPanel, BorderLayout.NORTH);
        topWrapper.add(navBar, BorderLayout.SOUTH);

        add(topWrapper, BorderLayout.NORTH);

        // Initialize panels
        initWatchlistUI();
        initScannerUI();
        initAnalysisUI();

        cardPanel.add(watchlistPanel, "WATCHLIST");
        cardPanel.add(scannerPanel, "SCANNER");
        cardPanel.add(analysisPanel, "ANALYSIS");
        add(cardPanel, BorderLayout.CENTER);

        showCard("SCANNER", btnScanner, btnWatchlist, btnAnalysis);
        loadList();

        sessionManager.addListener(this::updateProfitUI);
    }

    private void updateProfitUI()
    {
        SwingUtilities.invokeLater(() -> {
            long p = sessionManager.getSessionProfit();
            sessionProfitLabel.setText("Session: " + QuantityFormatter.quantityToStackSize(p) + " gp");
            sessionProfitLabel.setForeground(p >= 0 ? Color.GREEN : Color.RED);
        });
    }

    private JButton createNavButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(Color.GRAY);
        btn.setBorder(new EmptyBorder(8, 0, 8, 0));
        btn.setFont(FontManager.getRunescapeSmallFont());
        return btn;
    }

    private void showCard(String name, JButton active, JButton... inactive)
    {
        CardLayout cl = (CardLayout) cardPanel.getLayout();
        cl.show(cardPanel, name);
        active.setForeground(Color.WHITE);
        active.setFont(FontManager.getRunescapeBoldFont());
        for (JButton btn : inactive)
        {
            btn.setForeground(Color.GRAY);
            btn.setFont(FontManager.getRunescapeSmallFont());
        }
    }

    private void initWatchlistUI()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.addActionListener(e -> addItemToWatchlist(searchBar.getText()));

        header.add(searchBar, BorderLayout.CENTER);
        watchlistPanel.add(header, BorderLayout.NORTH);

        watchlistContainer.setLayout(new BoxLayout(watchlistContainer, BoxLayout.Y_AXIS));
        watchlistContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(watchlistContainer);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        watchlistPanel.add(scroll, BorderLayout.CENTER);
    }

    private void initScannerUI()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(5, 5, 5, 5));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // === BASIC FILTERS SECTION ===
        JPanel basicFiltersPanel = createBasicFiltersPanel();
        header.add(basicFiltersPanel);

        // === ADVANCED FILTERS SECTION (Collapsible) ===
        advancedFiltersPanel = createAdvancedFiltersPanel();
        advancedFiltersPanel.setVisible(false);
        header.add(advancedFiltersPanel);

        // Toggle Advanced Button
        toggleAdvancedBtn = new JButton("▶ Advanced Filters");
        toggleAdvancedBtn.setFont(FontManager.getRunescapeSmallFont());
        toggleAdvancedBtn.setFocusPainted(false);
        toggleAdvancedBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        toggleAdvancedBtn.setForeground(Color.LIGHT_GRAY);
        toggleAdvancedBtn.setBorder(new EmptyBorder(5, 5, 5, 5));
        toggleAdvancedBtn.setHorizontalAlignment(SwingConstants.LEFT);
        toggleAdvancedBtn.addActionListener(e -> toggleAdvancedFilters());

        JPanel toggleWrapper = new JPanel(new BorderLayout());
        toggleWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        toggleWrapper.setBorder(new EmptyBorder(3, 0, 3, 0));
        toggleWrapper.add(toggleAdvancedBtn, BorderLayout.WEST);
        header.add(toggleWrapper);

        // === SORTING & RESULTS SECTION ===
        JPanel sortingPanel = createSortingPanel();
        header.add(sortingPanel);

        // === SCAN BUTTON ===
        JButton scanBtn = new JButton("🔮 Run Market Analysis");
        scanBtn.setFocusPainted(false);
        scanBtn.setBackground(new Color(40, 100, 60));
        scanBtn.setForeground(Color.WHITE);
        scanBtn.setFont(FontManager.getRunescapeBoldFont());
        scanBtn.setBorder(new EmptyBorder(12, 0, 12, 0));
        scanBtn.addActionListener(e -> runPredictionScanner());

        JPanel scanBtnWrapper = new JPanel(new BorderLayout());
        scanBtnWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scanBtnWrapper.setBorder(new EmptyBorder(5, 0, 5, 0));
        scanBtnWrapper.add(scanBtn, BorderLayout.CENTER);
        header.add(scanBtnWrapper);

        // Quick Filter Buttons
        JPanel quickFilterPanel = createQuickFilterPanel();
        header.add(quickFilterPanel);

        // === STATS PANEL ===
        JPanel statsPanel = createStatsPanel();
        header.add(statsPanel);

        // Status
        scannerStatus.setHorizontalAlignment(SwingConstants.CENTER);
        scannerStatus.setBorder(new EmptyBorder(5, 0, 5, 0));
        scannerStatus.setForeground(Color.GRAY);
        scannerStatus.setFont(FontManager.getRunescapeSmallFont());
        header.add(scannerStatus);

        // Legend
        JPanel legendPanel = createLegendPanel();
        header.add(legendPanel);

        scannerPanel.add(header, BorderLayout.NORTH);

        // Results container
        scannerContainer.setLayout(new BoxLayout(scannerContainer, BoxLayout.Y_AXIS));
        scannerContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scannerContainer.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane scroll = new JScrollPane(scannerContainer);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scannerPanel.add(scroll, BorderLayout.CENTER);
    }

    private JPanel createBasicFiltersPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                "Basic Filters",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                FontManager.getRunescapeSmallFont(),
                Color.WHITE
        ));

        // Row 1: Price Range
        JPanel priceRow = new JPanel(new GridLayout(1, 4, 5, 0));
        priceRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        priceRow.setBorder(new EmptyBorder(2, 5, 2, 5));

        priceRow.add(createLabeledInput("Min Price:", minPriceInput, "Minimum item price (e.g., 1k, 10m)"));
        priceRow.add(createLabeledInput("Max Price:", maxPriceInput, "Maximum item price (e.g., 100m, 1b)"));

        panel.add(priceRow);

        // Row 2: Volume Range
        JPanel volumeRow = new JPanel(new GridLayout(1, 4, 5, 0));
        volumeRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        volumeRow.setBorder(new EmptyBorder(2, 5, 2, 5));

        volumeRow.add(createLabeledInput("Min Vol:", minVolumeInput, "Minimum daily trading volume"));
        volumeRow.add(createLabeledInput("Max Vol:", maxVolumeInput, "Maximum daily volume (empty = no limit)"));

        panel.add(volumeRow);

        // Row 3: Margin & ROI
        JPanel marginRow = new JPanel(new GridLayout(1, 4, 5, 0));
        marginRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        marginRow.setBorder(new EmptyBorder(2, 5, 2, 5));

        marginRow.add(createLabeledInput("Min Margin:", minMarginInput, "Minimum net margin in GP"));
        marginRow.add(createLabeledInput("Min ROI %:", minROIInput, "Minimum return on investment %"));

        panel.add(marginRow);

        return panel;
    }

    private JPanel createAdvancedFiltersPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 80, 0)),
                "Advanced Filters",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                FontManager.getRunescapeSmallFont(),
                new Color(255, 200, 0)
        ));

        // Row 1: Score & Volatility
        JPanel row1 = new JPanel(new GridLayout(1, 4, 5, 0));
        row1.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row1.setBorder(new EmptyBorder(2, 5, 2, 5));

        row1.add(createLabeledInput("Min Score:", minScoreInput, "Minimum prediction score (0-100)"));
        row1.add(createLabeledInput("Max Volatility:", maxVolatilityInput, "Maximum price volatility %"));

        panel.add(row1);

        // Row 2: Stability & Flip Time
        JPanel row2 = new JPanel(new GridLayout(1, 4, 5, 0));
        row2.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row2.setBorder(new EmptyBorder(2, 5, 2, 5));

        row2.add(createLabeledInput("Min Stability:", minStabilityInput, "Minimum margin stability (0-100)"));
        row2.add(createLabeledInput("Max Flip Time:", maxFlipTimeInput, "Maximum estimated flip time (hours)"));

        panel.add(row2);

        // Row 3: Buy Limit
        JPanel row3 = new JPanel(new GridLayout(1, 2, 5, 0));
        row3.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row3.setBorder(new EmptyBorder(2, 5, 2, 5));

        row3.add(createLabeledInput("Min Buy Limit:", minBuyLimitInput, "Minimum GE buy limit (per 4hr)"));

        onlyKnownBuyLimitsCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        onlyKnownBuyLimitsCheckbox.setForeground(Color.LIGHT_GRAY);
        onlyKnownBuyLimitsCheckbox.setFont(FontManager.getRunescapeSmallFont());
        onlyKnownBuyLimitsCheckbox.setToolTipText("Only show items with known buy limits");
        row3.add(onlyKnownBuyLimitsCheckbox);

        panel.add(row3);

        // Row 4: RSI Filter
        JPanel row4 = new JPanel(new BorderLayout(5, 0));
        row4.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row4.setBorder(new EmptyBorder(2, 5, 2, 5));

        JLabel rsiLabel = new JLabel("RSI Filter:");
        rsiLabel.setForeground(Color.LIGHT_GRAY);
        rsiLabel.setFont(FontManager.getRunescapeSmallFont());
        rsiLabel.setPreferredSize(new Dimension(70, 20));

        rsiFilterDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rsiFilterDropdown.setForeground(Color.WHITE);
        rsiFilterDropdown.setFont(FontManager.getRunescapeSmallFont());
        rsiFilterDropdown.setToolTipText("Filter by RSI (Relative Strength Index)");

        row4.add(rsiLabel, BorderLayout.WEST);
        row4.add(rsiFilterDropdown, BorderLayout.CENTER);

        panel.add(row4);

        // Row 5: Trend Filter
        JPanel row5 = new JPanel(new BorderLayout(5, 0));
        row5.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row5.setBorder(new EmptyBorder(2, 5, 2, 5));

        JLabel trendLabel = new JLabel("Trend Filter:");
        trendLabel.setForeground(Color.LIGHT_GRAY);
        trendLabel.setFont(FontManager.getRunescapeSmallFont());
        trendLabel.setPreferredSize(new Dimension(70, 20));

        trendFilterDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        trendFilterDropdown.setForeground(Color.WHITE);
        trendFilterDropdown.setFont(FontManager.getRunescapeSmallFont());
        trendFilterDropdown.setToolTipText("Filter by price trend direction");

        row5.add(trendLabel, BorderLayout.WEST);
        row5.add(trendFilterDropdown, BorderLayout.CENTER);

        panel.add(row5);

        // Row 6: Confidence Filter
        JPanel row6 = new JPanel(new BorderLayout(5, 0));
        row6.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row6.setBorder(new EmptyBorder(2, 5, 2, 5));

        JLabel confLabel = new JLabel("Confidence:");
        confLabel.setForeground(Color.LIGHT_GRAY);
        confLabel.setFont(FontManager.getRunescapeSmallFont());
        confLabel.setPreferredSize(new Dimension(70, 20));

        confidenceFilterDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        confidenceFilterDropdown.setForeground(Color.WHITE);
        confidenceFilterDropdown.setFont(FontManager.getRunescapeSmallFont());
        confidenceFilterDropdown.setToolTipText("Filter by prediction confidence level");

        row6.add(confLabel, BorderLayout.WEST);
        row6.add(confidenceFilterDropdown, BorderLayout.CENTER);

        panel.add(row6);

        // Row 7: Recommendation Filter
        JPanel row7 = new JPanel(new BorderLayout(5, 0));
        row7.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row7.setBorder(new EmptyBorder(2, 5, 2, 5));

        JLabel recLabel = new JLabel("Recommend:");
        recLabel.setForeground(Color.LIGHT_GRAY);
        recLabel.setFont(FontManager.getRunescapeSmallFont());
        recLabel.setPreferredSize(new Dimension(70, 20));

        recommendationFilterDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recommendationFilterDropdown.setForeground(Color.WHITE);
        recommendationFilterDropdown.setFont(FontManager.getRunescapeSmallFont());
        recommendationFilterDropdown.setToolTipText("Filter by recommendation level");

        row7.add(recLabel, BorderLayout.WEST);
        row7.add(recommendationFilterDropdown, BorderLayout.CENTER);

        panel.add(row7);

        return panel;
    }

    private JPanel createSortingPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel leftPanel = new JPanel(new BorderLayout(3, 0));
        leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setForeground(Color.LIGHT_GRAY);
        sortLabel.setFont(FontManager.getRunescapeSmallFont());

        sortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortDropdown.setForeground(Color.WHITE);
        sortDropdown.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.addActionListener(e -> applyFiltersAndSort());

        leftPanel.add(sortLabel, BorderLayout.WEST);
        leftPanel.add(sortDropdown, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(3, 0));
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightPanel.setPreferredSize(new Dimension(80, 25));

        JLabel limitLabel = new JLabel("Limit:");
        limitLabel.setForeground(Color.LIGHT_GRAY);
        limitLabel.setFont(FontManager.getRunescapeSmallFont());

        resultLimitInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        resultLimitInput.setForeground(Color.WHITE);
        resultLimitInput.setFont(FontManager.getRunescapeSmallFont());
        resultLimitInput.setToolTipText("Maximum results to display");
        resultLimitInput.setPreferredSize(new Dimension(40, 20));

        rightPanel.add(limitLabel, BorderLayout.WEST);
        rightPanel.add(resultLimitInput, BorderLayout.CENTER);

        panel.add(leftPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createQuickFilterPanel()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 2));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(2, 0, 2, 0));

        panel.add(createQuickFilterButton("Low Risk", () -> {
            maxVolatilityInput.setText("5");
            minStabilityInput.setText("70");
            confidenceFilterDropdown.setSelectedItem("High or Better");
            trendFilterDropdown.setSelectedItem("Not Downtrend");
        }));

        panel.add(createQuickFilterButton("High ROI", () -> {
            minROIInput.setText("2");
            minScoreInput.setText("50");
        }));

        panel.add(createQuickFilterButton("Quick Flips", () -> {
            maxFlipTimeInput.setText("2");
            minVolumeInput.setText("2000");
        }));

        panel.add(createQuickFilterButton("Oversold", () -> {
            rsiFilterDropdown.setSelectedItem("Oversold (< 30)");
        }));

        panel.add(createQuickFilterButton("Reset", () -> resetFilters()));

        return panel;
    }

    private JButton createQuickFilterButton(String text, Runnable action)
    {
        JButton btn = new JButton(text);
        btn.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
        btn.setFocusPainted(false);
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(Color.LIGHT_GRAY);
        btn.setBorder(new EmptyBorder(3, 6, 3, 6));
        btn.setPreferredSize(new Dimension(65, 20));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void resetFilters()
    {
        minPriceInput.setText("0");
        maxPriceInput.setText("100M");
        minVolumeInput.setText("500");
        maxVolumeInput.setText("");
        minMarginInput.setText("100");
        minROIInput.setText("0.5");
        minScoreInput.setText("30");
        maxVolatilityInput.setText("15");
        minStabilityInput.setText("30");
        maxFlipTimeInput.setText("8");
        minBuyLimitInput.setText("0");
        onlyKnownBuyLimitsCheckbox.setSelected(false);
        rsiFilterDropdown.setSelectedIndex(0);
        trendFilterDropdown.setSelectedIndex(0);
        confidenceFilterDropdown.setSelectedIndex(0);
        recommendationFilterDropdown.setSelectedIndex(0);
        sortDropdown.setSelectedIndex(0);
        resultLimitInput.setText("50");
    }

    private JPanel createStatsPanel()
    {
        JPanel panel = new JPanel(new GridLayout(1, 4, 5, 0));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        statsItemsScanned.setFont(FontManager.getRunescapeSmallFont());
        statsItemsScanned.setForeground(Color.LIGHT_GRAY);
        statsItemsScanned.setHorizontalAlignment(SwingConstants.CENTER);

        statsMatchingFilters.setFont(FontManager.getRunescapeSmallFont());
        statsMatchingFilters.setForeground(Color.LIGHT_GRAY);
        statsMatchingFilters.setHorizontalAlignment(SwingConstants.CENTER);

        statsAvgScore.setFont(FontManager.getRunescapeSmallFont());
        statsAvgScore.setForeground(Color.LIGHT_GRAY);
        statsAvgScore.setHorizontalAlignment(SwingConstants.CENTER);

        statsAvgROI.setFont(FontManager.getRunescapeSmallFont());
        statsAvgROI.setForeground(Color.LIGHT_GRAY);
        statsAvgROI.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(statsItemsScanned);
        panel.add(statsMatchingFilters);
        panel.add(statsAvgScore);
        panel.add(statsAvgROI);

        return panel;
    }

    private JPanel createLabeledInput(String labelText, JTextField input, String tooltip)
    {
        JPanel panel = new JPanel(new BorderLayout(2, 0));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.LIGHT_GRAY);
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));

        input.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        input.setForeground(Color.WHITE);
        input.setFont(FontManager.getRunescapeSmallFont());
        input.setToolTipText(tooltip);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));

        panel.add(label, BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);

        return panel;
    }

    private void toggleAdvancedFilters()
    {
        advancedFiltersVisible = !advancedFiltersVisible;
        advancedFiltersPanel.setVisible(advancedFiltersVisible);
        toggleAdvancedBtn.setText((advancedFiltersVisible ? "▼" : "▶") + " Advanced Filters");
        revalidate();
        repaint();
    }

    private JPanel createLegendPanel()
    {
        JPanel legendContainer = new JPanel();
        legendContainer.setLayout(new BoxLayout(legendContainer, BoxLayout.Y_AXIS));
        legendContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        legend.setBackground(ColorScheme.DARK_GRAY_COLOR);

        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.STRONG_BUY), "Strong"));
        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.BUY), "Buy"));
        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.CONSIDER), "Consider"));
        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.CAUTION), "Caution"));

        legendContainer.add(legend);

        return legendContainer;
    }

    private JLabel createLegendItem(String icon, Color color, String text)
    {
        JLabel label = new JLabel(icon + " " + text);
        label.setForeground(color);
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
        return label;
    }

    private void initAnalysisUI()
    {
        analysisPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel placeholderPanel = new JPanel();
        placeholderPanel.setLayout(new BoxLayout(placeholderPanel, BoxLayout.Y_AXIS));
        placeholderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        placeholderPanel.setBorder(new EmptyBorder(20, 10, 10, 10));

        JLabel icon = new JLabel("📊");
        icon.setFont(icon.getFont().deriveFont(32f));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Item Analysis");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint1 = new JLabel("Click any item from Predict tab");
        hint1.setForeground(Color.GRAY);
        hint1.setFont(FontManager.getRunescapeSmallFont());
        hint1.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint1.setBorder(new EmptyBorder(15, 0, 5, 0));

        placeholderPanel.add(Box.createVerticalGlue());
        placeholderPanel.add(icon);
        placeholderPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        placeholderPanel.add(title);
        placeholderPanel.add(hint1);
        placeholderPanel.add(Box.createVerticalGlue());

        analysisContainer.setLayout(new BoxLayout(analysisContainer, BoxLayout.Y_AXIS));
        analysisContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        analysisContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        analysisContainer.add(placeholderPanel);

        JScrollPane scroll = new JScrollPane(analysisContainer);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        analysisPanel.add(scroll, BorderLayout.CENTER);
    }

    /**
     * Run the prediction scanner with all current filter settings
     */
    private void runPredictionScanner()
    {
        scannerStatus.setText("Fetching price data...");
        scannerStatus.setForeground(Color.YELLOW);
        scannerContainer.removeAll();
        scannerContainer.revalidate();

        // Parse filter values
        long minPrice = parseValue(minPriceInput.getText());
        long maxPrice = parseValue(maxPriceInput.getText());
        long minVolume = parseValue(minVolumeInput.getText());

        // Step 1: Fetch latest prices
        wikiPriceService.fetchLivePrices(() -> {
            SwingUtilities.invokeLater(() -> scannerStatus.setText("Fetching historical data..."));

            // Step 2: Fetch 5-minute historical data
            priceHistoryService.fetchFiveMinuteData(() -> {
                SwingUtilities.invokeLater(() -> scannerStatus.setText("Calculating predictions..."));

                // Step 3: Calculate metrics
                priceHistoryService.calculateAllMetrics();

                // Step 4: Calculate scores for ALL items (we'll filter later)
                // Use very permissive initial filtering to get raw data
                List<FlipScorer.FlipScore> allScores = flipScorer.calculateAllScores(
                        Math.min(minVolume, 100),  // Lower threshold for initial fetch
                        Math.max(maxPrice, 1000000000L),  // Higher threshold
                        10000  // Get lots of results
                );

                // Step 5: Get item names on client thread
                clientThread.invoke(() -> {
                    for (FlipScorer.FlipScore score : allScores)
                    {
                        try
                        {
                            score.itemName = itemManager.getItemComposition(score.itemId).getName();
                        }
                        catch (Exception e)
                        {
                            score.itemName = "Unknown Item";
                        }
                    }

                    // Store all results for filtering
                    allFetchedResults = new ArrayList<>(allScores);

                    // Step 6: Apply filters and update UI
                    SwingUtilities.invokeLater(() -> {
                        statsItemsScanned.setText("Scanned: " + allScores.size());
                        applyFiltersAndSort();
                    });
                });
            });
        });
    }

    /**
     * Apply all current filters and sort settings to the results
     */
    private void applyFiltersAndSort()
    {
        if (allFetchedResults.isEmpty())
        {
            scannerStatus.setText("No data - run scan first");
            scannerStatus.setForeground(Color.GRAY);
            return;
        }

        // Parse all filter values
        long minPrice = parseValue(minPriceInput.getText());
        long maxPrice = parseValue(maxPriceInput.getText());
        long minVolume = parseValue(minVolumeInput.getText());
        long maxVolume = parseValue(maxVolumeInput.getText());
        if (maxVolume == Long.MAX_VALUE) maxVolume = Long.MAX_VALUE;

        int minMargin = (int) parseValue(minMarginInput.getText());
        double minROI = parseDouble(minROIInput.getText());
        double minScore = parseDouble(minScoreInput.getText());
        double maxVolatility = parseDouble(maxVolatilityInput.getText());
        double minStability = parseDouble(minStabilityInput.getText());
        double maxFlipTime = parseDouble(maxFlipTimeInput.getText());
        int minBuyLimit = (int) parseValue(minBuyLimitInput.getText());
        boolean onlyKnownLimits = onlyKnownBuyLimitsCheckbox.isSelected();

        String rsiFilter = (String) rsiFilterDropdown.getSelectedItem();
        String trendFilter = (String) trendFilterDropdown.getSelectedItem();
        String confidenceFilter = (String) confidenceFilterDropdown.getSelectedItem();
        String recommendationFilter = (String) recommendationFilterDropdown.getSelectedItem();

        int resultLimit = (int) parseValue(resultLimitInput.getText());
        if (resultLimit <= 0) resultLimit = 50;

        // Apply filters
        final long fMaxVolume = maxVolume;
        final int fMinMargin = minMargin;
        final double fMinROI = minROI;
        final double fMinScore = minScore;
        final double fMaxVolatility = maxVolatility;
        final double fMinStability = minStability;
        final double fMaxFlipTime = maxFlipTime;
        final int fMinBuyLimit = minBuyLimit;
        final int fResultLimit = resultLimit;

        List<FlipScorer.FlipScore> filtered = allFetchedResults.stream()
                .filter(s -> s.buyPrice >= minPrice && s.buyPrice <= maxPrice)
                .filter(s -> s.dailyVolume >= minVolume && s.dailyVolume <= fMaxVolume)
                .filter(s -> s.netMargin >= fMinMargin)
                .filter(s -> s.roi >= fMinROI)
                .filter(s -> s.overallScore >= fMinScore)
                .filter(s -> s.volatilityScore >= (100 - fMaxVolatility * 10)) // Convert volatility to score comparison
                .filter(s -> s.stabilityScore >= fMinStability)
                .filter(s -> fMaxFlipTime <= 0 || s.estimatedFlipTimeHours <= fMaxFlipTime || s.estimatedFlipTimeHours == 0)
                .filter(s -> s.buyLimit >= fMinBuyLimit)
                .filter(s -> !onlyKnownLimits || s.buyLimit > 0)
                .filter(s -> passesRsiFilter(s, rsiFilter))
                .filter(s -> passesTrendFilter(s, trendFilter))
                .filter(s -> passesConfidenceFilter(s, confidenceFilter))
                .filter(s -> passesRecommendationFilter(s, recommendationFilter))
                .collect(Collectors.toList());

        // Cache filtered results
        cachedPredictions = new ArrayList<>(filtered);

        // Apply sorting
        String sortOption = (String) sortDropdown.getSelectedItem();
        applySorting(filtered, sortOption);

        // Limit results
        if (filtered.size() > fResultLimit)
        {
            filtered = filtered.subList(0, fResultLimit);
        }

        // Update stats
        updateStats(filtered);

        // Display results
        displayFilteredResults(filtered);
    }

    private boolean passesRsiFilter(FlipScorer.FlipScore score, String filter)
    {
        if (filter == null || "Any RSI".equals(filter)) return true;

        double rsi = score.rsi;
        switch (filter)
        {
            case "Oversold (< 30)":
                return rsi < 30;
            case "Slightly Oversold (30-45)":
                return rsi >= 30 && rsi < 45;
            case "Neutral (45-55)":
                return rsi >= 45 && rsi <= 55;
            case "Slightly Overbought (55-70)":
                return rsi > 55 && rsi <= 70;
            case "Overbought (> 70)":
                return rsi > 70;
            case "Buy Zone (< 50)":
                return rsi < 50;
            case "Sell Zone (> 50)":
                return rsi > 50;
            default:
                return true;
        }
    }

    private boolean passesTrendFilter(FlipScorer.FlipScore score, String filter)
    {
        if (filter == null || "Any Trend".equals(filter)) return true;

        double trend = score.trendScore;
        switch (filter)
        {
            case "Strong Uptrend":
                return trend >= 70;
            case "Uptrend":
                return trend >= 55;
            case "Sideways":
                return trend >= 45 && trend <= 55;
            case "Downtrend":
                return trend <= 45;
            case "Strong Downtrend":
                return trend <= 30;
            case "Not Downtrend":
                return trend >= 45;
            default:
                return true;
        }
    }

    private boolean passesConfidenceFilter(FlipScorer.FlipScore score, String filter)
    {
        if (filter == null || "Any Confidence".equals(filter)) return true;

        switch (filter)
        {
            case "Very High Only":
                return score.confidence == FlipScorer.Confidence.VERY_HIGH;
            case "High or Better":
                return score.confidence.ordinal() >= FlipScorer.Confidence.HIGH.ordinal();
            case "Medium or Better":
                return score.confidence.ordinal() >= FlipScorer.Confidence.MEDIUM.ordinal();
            case "Low or Better":
                return score.confidence.ordinal() >= FlipScorer.Confidence.LOW.ordinal();
            default:
                return true;
        }
    }

    private boolean passesRecommendationFilter(FlipScorer.FlipScore score, String filter)
    {
        if (filter == null || "Any Recommendation".equals(filter)) return true;

        switch (filter)
        {
            case "Strong Buy Only":
                return score.recommendation == FlipScorer.Recommendation.STRONG_BUY;
            case "Buy or Better":
                return score.recommendation.ordinal() <= FlipScorer.Recommendation.BUY.ordinal();
            case "Consider or Better":
                return score.recommendation.ordinal() <= FlipScorer.Recommendation.CONSIDER.ordinal();
            case "Exclude Avoid":
                return score.recommendation != FlipScorer.Recommendation.AVOID;
            default:
                return true;
        }
    }

    private void applySorting(List<FlipScorer.FlipScore> list, String sortOption)
    {
        if (sortOption == null) return;

        switch (sortOption)
        {
            case "Score (High → Low)":
                list.sort((a, b) -> Double.compare(b.overallScore, a.overallScore));
                break;
            case "Score (Low → High)":
                list.sort((a, b) -> Double.compare(a.overallScore, b.overallScore));
                break;
            case "Margin (High → Low)":
                list.sort((a, b) -> Integer.compare(b.netMargin, a.netMargin));
                break;
            case "Margin (Low → High)":
                list.sort((a, b) -> Integer.compare(a.netMargin, b.netMargin));
                break;
            case "ROI (High → Low)":
                list.sort((a, b) -> Double.compare(b.roi, a.roi));
                break;
            case "Profit/Hr (High → Low)":
                list.sort((a, b) -> Long.compare(b.estimatedHourlyProfit, a.estimatedHourlyProfit));
                break;
            case "Volume (High → Low)":
                list.sort((a, b) -> Long.compare(b.dailyVolume, a.dailyVolume));
                break;
            case "Volume (Low → High)":
                list.sort((a, b) -> Long.compare(a.dailyVolume, b.dailyVolume));
                break;
            case "Volatility (Low → High)":
                list.sort((a, b) -> Double.compare(b.volatilityScore, a.volatilityScore)); // Higher score = lower volatility
                break;
            case "Stability (High → Low)":
                list.sort((a, b) -> Double.compare(b.stabilityScore, a.stabilityScore));
                break;
            case "Flip Time (Low → High)":
                list.sort((a, b) -> Double.compare(a.estimatedFlipTimeHours, b.estimatedFlipTimeHours));
                break;
            case "RSI (Low → High)":
                list.sort((a, b) -> Double.compare(a.rsi, b.rsi));
                break;
            case "Trend (Best → Worst)":
                list.sort((a, b) -> Double.compare(b.trendScore, a.trendScore));
                break;
            case "Name (A → Z)":
                list.sort((a, b) -> {
                    String nameA = a.itemName != null ? a.itemName : "";
                    String nameB = b.itemName != null ? b.itemName : "";
                    return nameA.compareToIgnoreCase(nameB);
                });
                break;
        }
    }

    private void updateStats(List<FlipScorer.FlipScore> results)
    {
        statsMatchingFilters.setText("Match: " + results.size());

        if (!results.isEmpty())
        {
            double avgScore = results.stream().mapToDouble(s -> s.overallScore).average().orElse(0);
            double avgROI = results.stream().mapToDouble(s -> s.roi).average().orElse(0);

            statsAvgScore.setText("Avg: " + String.format("%.1f", avgScore));
            statsAvgROI.setText("ROI: " + String.format("%.2f%%", avgROI));

            statsAvgScore.setForeground(getScoreColor(avgScore));
        }
        else
        {
            statsAvgScore.setText("Avg: -");
            statsAvgROI.setText("ROI: -");
        }
    }

    private void displayFilteredResults(List<FlipScorer.FlipScore> results)
    {
        scannerContainer.removeAll();

        if (results.isEmpty())
        {
            scannerStatus.setText("No items match your filters");
            scannerStatus.setForeground(Color.RED);

            JLabel noResults = new JLabel("Try adjusting your filter settings");
            noResults.setForeground(Color.GRAY);
            noResults.setFont(FontManager.getRunescapeSmallFont());
            noResults.setAlignmentX(Component.CENTER_ALIGNMENT);
            noResults.setBorder(new EmptyBorder(20, 0, 0, 0));
            scannerContainer.add(noResults);
        }
        else
        {
            scannerStatus.setText("Showing " + results.size() + " predictions");
            scannerStatus.setForeground(Color.GREEN);

            for (FlipScorer.FlipScore score : results)
            {
                JPanel card = createPredictionCard(score);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                scannerContainer.add(card);
                scannerContainer.add(Box.createRigidArea(new Dimension(0, 5)));
            }
        }

        scannerContainer.revalidate();
        scannerContainer.repaint();
    }

    /**
     * Create a prediction card with score visualization
     */
    private JPanel createPredictionCard(FlipScorer.FlipScore score)
    {
        JPanel card = new JPanel(new BorderLayout(5, 0));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(getRecommendationColor(score.recommendation), 1),
                new EmptyBorder(5, 5, 5, 5)
        ));

        Dimension cardSize = new Dimension(PluginPanel.PANEL_WIDTH - 10, 95);
        card.setPreferredSize(cardSize);
        card.setMaximumSize(cardSize);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Left: Icon
        JLabel iconLabel = new JLabel();
        try
        {
            AsyncBufferedImage img = itemManager.getImage(score.itemId);
            if (img != null) img.addTo(iconLabel);
        }
        catch (Exception ignored) {}

        JPanel iconWrapper = new JPanel(new BorderLayout());
        iconWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        iconWrapper.setPreferredSize(new Dimension(36, 36));
        iconWrapper.add(iconLabel, BorderLayout.CENTER);

        // Center: Info
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        centerPanel.setBorder(new EmptyBorder(0, 5, 0, 5));

        // Name
        String displayName = score.itemName != null ? score.itemName : "Unknown";
        JLabel nameLabel = new JLabel(truncate(displayName, 22));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());

        // Recommendation
        JLabel recLabel = new JLabel(formatRecommendation(score.recommendation) +
                " • " + score.confidence);
        recLabel.setForeground(getRecommendationColor(score.recommendation));
        recLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));

        // Score bar
        JPanel scoreBarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        scoreBarRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scoreBarRow.putClientProperty("isScoreBarFill", true);
        scoreBarRow.add(createScoreBar(score.overallScore, 100));

        // Key metrics line 1
        JLabel metricsLabel1 = new JLabel(String.format(
                "Margin: %s | ROI: %.2f%% | Vol: %s",
                QuantityFormatter.quantityToStackSize(score.netMargin),
                score.roi,
                QuantityFormatter.quantityToStackSize(score.dailyVolume)
        ));
        metricsLabel1.setForeground(Color.LIGHT_GRAY);
        metricsLabel1.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));

        // Key metrics line 2
        String flipTimeStr = score.estimatedFlipTimeHours > 0
                ? String.format("%.1fh", score.estimatedFlipTimeHours)
                : "N/A";
        JLabel metricsLabel2 = new JLabel(String.format(
                "RSI: %.0f | Flip: %s | ~%s/hr",
                score.rsi,
                flipTimeStr,
                QuantityFormatter.quantityToStackSize(score.estimatedHourlyProfit)
        ));
        metricsLabel2.setForeground(Color.GRAY);
        metricsLabel2.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));

        centerPanel.add(nameLabel);
        centerPanel.add(recLabel);
        centerPanel.add(scoreBarRow);
        centerPanel.add(metricsLabel1);
        centerPanel.add(metricsLabel2);

        // Right: Score number
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rightPanel.setPreferredSize(new Dimension(40, 50));

        JLabel scoreLabel = new JLabel(String.format("%.0f", score.overallScore));
        scoreLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        scoreLabel.setForeground(getScoreColor(score.overallScore));
        scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel scoreSubLabel = new JLabel("SCORE");
        scoreSubLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(8f));
        scoreSubLabel.setForeground(Color.GRAY);
        scoreSubLabel.setHorizontalAlignment(SwingConstants.CENTER);

        rightPanel.add(scoreLabel, BorderLayout.CENTER);
        rightPanel.add(scoreSubLabel, BorderLayout.SOUTH);

        card.add(iconWrapper, BorderLayout.WEST);
        card.add(centerPanel, BorderLayout.CENTER);
        card.add(rightPanel, BorderLayout.EAST);

        // Build comprehensive tooltip
        StringBuilder tooltip = new StringBuilder("<html><body style='width:250px'>");
        tooltip.append("<b style='font-size:12px'>").append(displayName).append("</b><br><br>");

        tooltip.append("<b>Price Information:</b><br>");
        tooltip.append("• Buy: ").append(QuantityFormatter.formatNumber(score.buyPrice)).append(" gp<br>");
        tooltip.append("• Sell: ").append(QuantityFormatter.formatNumber(score.sellPrice)).append(" gp<br>");
        tooltip.append("• Raw Margin: ").append(QuantityFormatter.formatNumber(score.rawMargin)).append(" gp<br>");
        tooltip.append("• GE Tax: ").append(QuantityFormatter.formatNumber(score.geTax)).append(" gp<br>");
        tooltip.append("• Net Margin: <b style='color:green'>").append(QuantityFormatter.formatNumber(score.netMargin)).append(" gp</b><br>");
        tooltip.append("• ROI: ").append(String.format("%.2f%%", score.roi)).append("<br><br>");

        tooltip.append("<b>Volume & Timing:</b><br>");
        tooltip.append("• Daily Volume: ").append(QuantityFormatter.formatNumber(score.dailyVolume)).append("<br>");
        if (score.buyLimit > 0)
        {
            tooltip.append("• Buy Limit: ").append(QuantityFormatter.formatNumber(score.buyLimit)).append(" / 4hr<br>");
            tooltip.append("• Est. Flip Time: ").append(String.format("%.1f hours", score.estimatedFlipTimeHours)).append("<br>");
            tooltip.append("• Profit/Cycle: ").append(QuantityFormatter.formatNumber(score.profitPerCycle)).append(" gp<br>");
        }
        tooltip.append("• Est. Hourly: <b style='color:yellow'>").append(QuantityFormatter.formatNumber(score.estimatedHourlyProfit)).append(" gp/hr</b><br><br>");

        tooltip.append("<b>Technical Scores (0-100):</b><br>");
        tooltip.append("• Margin: ").append(String.format("%.0f", score.marginScore)).append("<br>");
        tooltip.append("• Volume: ").append(String.format("%.0f", score.volumeScore)).append("<br>");
        tooltip.append("• Stability: ").append(String.format("%.0f", score.stabilityScore)).append("<br>");
        tooltip.append("• Trend: ").append(String.format("%.0f", score.trendScore)).append("<br>");
        tooltip.append("• Volatility: ").append(String.format("%.0f", score.volatilityScore)).append("<br>");
        tooltip.append("• RSI Score: ").append(String.format("%.0f", score.rsiScore)).append(" (RSI: ").append(String.format("%.1f", score.rsi)).append(")<br><br>");

        if (score.warnings != null && !score.warnings.isEmpty())
        {
            tooltip.append("<b style='color:orange'>⚠ Warnings:</b><br>");
            for (String warning : score.warnings)
            {
                tooltip.append("• ").append(warning).append("<br>");
            }
            tooltip.append("<br>");
        }

        tooltip.append("<i>").append(score.reason).append("</i><br>");
        tooltip.append("<br><b style='color:cyan'>Click for detailed analysis</b>");
        tooltip.append("</body></html>");

        card.setToolTipText(tooltip.toString());

        // Click handler
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                showDetailedAnalysis(score);
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                card.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
                updateCardBackground(card, ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                updateCardBackground(card, ColorScheme.DARKER_GRAY_COLOR);
            }
        });

        return card;
    }

    private void updateCardBackground(Container container, Color color)
    {
        for (Component comp : container.getComponents())
        {
            if (comp instanceof JPanel)
            {
                JPanel panel = (JPanel) comp;
                Object isScoreBar = panel.getClientProperty("isScoreBarFill");
                if (isScoreBar == null || !((Boolean) isScoreBar))
                {
                    panel.setBackground(color);
                }
                updateCardBackground(panel, color);
            }
        }
    }

    private String formatRecommendation(FlipScorer.Recommendation rec)
    {
        switch (rec)
        {
            case STRONG_BUY: return "STRONG BUY";
            case BUY: return "BUY";
            case CONSIDER: return "CONSIDER";
            case CAUTION: return "CAUTION";
            case AVOID: return "AVOID";
            default: return rec.toString();
        }
    }

    private JPanel createScoreBar(double score, int width)
    {
        JPanel barContainer = new JPanel(new BorderLayout());
        barContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        barContainer.setPreferredSize(new Dimension(width, 6));
        barContainer.setMaximumSize(new Dimension(width, 6));
        barContainer.putClientProperty("isScoreBarFill", true);

        JPanel barBg = new JPanel(new BorderLayout());
        barBg.setBackground(Color.DARK_GRAY);
        barBg.setBorder(new LineBorder(new Color(30, 30, 30), 1));
        barBg.putClientProperty("isScoreBarFill", true);

        JPanel barFill = new JPanel();
        barFill.setBackground(getScoreColor(score));
        barFill.setPreferredSize(new Dimension((int) (width * score / 100), 4));
        barFill.putClientProperty("isScoreBarFill", true);
        barFill.setOpaque(true);

        barBg.add(barFill, BorderLayout.WEST);
        barContainer.add(barBg, BorderLayout.CENTER);

        return barContainer;
    }

    private void showDetailedAnalysis(FlipScorer.FlipScore score)
    {
        this.selectedItem = score;
        analysisContainer.removeAll();

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        headerPanel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 70));

        JLabel iconLabel = new JLabel();
        try
        {
            AsyncBufferedImage img = itemManager.getImage(score.itemId);
            if (img != null) img.addTo(iconLabel);
        }
        catch (Exception ignored) {}

        JPanel titlePanel = new JPanel(new GridLayout(3, 1));
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titlePanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel nameLabel = new JLabel(score.itemName);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);

        JLabel recLabel = new JLabel(score.recommendation + " • Score: " + String.format("%.0f", score.overallScore));
        recLabel.setFont(FontManager.getRunescapeSmallFont());
        recLabel.setForeground(getRecommendationColor(score.recommendation));

        JLabel confLabel = new JLabel("Confidence: " + score.confidence);
        confLabel.setFont(FontManager.getRunescapeSmallFont());
        confLabel.setForeground(getConfidenceColor(score.confidence));

        titlePanel.add(nameLabel);
        titlePanel.add(recLabel);
        titlePanel.add(confLabel);

        headerPanel.add(iconLabel, BorderLayout.WEST);
        headerPanel.add(titlePanel, BorderLayout.CENTER);

        analysisContainer.add(headerPanel);
        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Price Info Section
        analysisContainer.add(createSectionHeader("💰 Price Information"));
        analysisContainer.add(createInfoRow("Buy Price", QuantityFormatter.formatNumber(score.buyPrice) + " gp"));
        analysisContainer.add(createInfoRow("Sell Price", QuantityFormatter.formatNumber(score.sellPrice) + " gp"));
        analysisContainer.add(createInfoRow("Raw Margin", QuantityFormatter.formatNumber(score.rawMargin) + " gp"));
        analysisContainer.add(createInfoRow("GE Tax (1%)", QuantityFormatter.formatNumber(score.geTax) + " gp"));
        analysisContainer.add(createInfoRow("Net Margin", QuantityFormatter.formatNumber(score.netMargin) + " gp", Color.GREEN));
        analysisContainer.add(createInfoRow("ROI", String.format("%.2f%%", score.roi), score.roi > 1 ? Color.GREEN : Color.YELLOW));

        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Volume & Timing Section
        analysisContainer.add(createSectionHeader("📊 Volume & Timing"));
        analysisContainer.add(createInfoRow("Daily Volume", QuantityFormatter.formatNumber(score.dailyVolume)));
        if (score.buyLimit > 0)
        {
            analysisContainer.add(createInfoRow("GE Buy Limit", QuantityFormatter.formatNumber(score.buyLimit) + " / 4hr"));
            analysisContainer.add(createInfoRow("Est. Flip Time", String.format("%.1f hours", score.estimatedFlipTimeHours)));
            analysisContainer.add(createInfoRow("Profit per Cycle", QuantityFormatter.formatNumber(score.profitPerCycle) + " gp"));
        }
        else
        {
            analysisContainer.add(createInfoRow("GE Buy Limit", "Unknown", Color.GRAY));
        }
        analysisContainer.add(createInfoRow("Est. Hourly Profit", QuantityFormatter.formatNumber(score.estimatedHourlyProfit) + " gp/hr", Color.YELLOW));

        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Technical Analysis Section
        analysisContainer.add(createSectionHeader("📈 Technical Analysis"));
        analysisContainer.add(createScoreRow("Margin Score", score.marginScore));
        analysisContainer.add(createScoreRow("Volume Score", score.volumeScore));
        analysisContainer.add(createScoreRow("Stability Score", score.stabilityScore));
        analysisContainer.add(createScoreRow("Trend Score", score.trendScore));
        analysisContainer.add(createScoreRow("Volatility Score", score.volatilityScore));
        analysisContainer.add(createScoreRow("RSI Score", score.rsiScore));

        analysisContainer.add(Box.createRigidArea(new Dimension(0, 5)));
        analysisContainer.add(createInfoRow("RSI Value", String.format("%.1f", score.rsi), getRsiColor(score.rsi)));
        analysisContainer.add(createInfoRow("RSI Signal", getRsiSignal(score.rsi), getRsiColor(score.rsi)));

        // Warnings Section
        if (score.warnings != null && !score.warnings.isEmpty())
        {
            analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));
            analysisContainer.add(createSectionHeader("⚠️ Warnings"));
            for (String warning : score.warnings)
            {
                analysisContainer.add(createWarningRow(warning));
            }
        }

        // Analysis Reason
        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        analysisContainer.add(createSectionHeader("📝 Analysis"));
        JTextArea reasonArea = new JTextArea(score.reason);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setLineWrap(true);
        reasonArea.setOpaque(false);
        reasonArea.setEditable(false);
        reasonArea.setForeground(Color.LIGHT_GRAY);
        reasonArea.setFont(FontManager.getRunescapeSmallFont());
        reasonArea.setBorder(new EmptyBorder(5, 10, 5, 10));
        analysisContainer.add(reasonArea);

        // Add to watchlist button
        JButton addToWatchlistBtn = new JButton("➕ Add to Watchlist");
        addToWatchlistBtn.setFont(FontManager.getRunescapeSmallFont());
        addToWatchlistBtn.setFocusPainted(false);
        addToWatchlistBtn.setBackground(new Color(40, 80, 100));
        addToWatchlistBtn.setForeground(Color.WHITE);
        addToWatchlistBtn.setBorder(new EmptyBorder(8, 15, 8, 15));
        addToWatchlistBtn.addActionListener(e -> {
            addItemDirectlyFromScore(score);
            JOptionPane.showMessageDialog(this, "Added " + score.itemName + " to watchlist!");
        });

        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        btnWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
        btnWrapper.add(addToWatchlistBtn);
        analysisContainer.add(btnWrapper);

        // Switch to analysis panel
        CardLayout cl = (CardLayout) cardPanel.getLayout();
        cl.show(cardPanel, "ANALYSIS");

        analysisContainer.revalidate();
        analysisContainer.repaint();
    }

    private String getRsiSignal(double rsi)
    {
        if (rsi < 20) return "Extremely Oversold - Strong Buy";
        if (rsi < 30) return "Oversold - Buy Signal";
        if (rsi < 40) return "Slightly Oversold";
        if (rsi < 60) return "Neutral";
        if (rsi < 70) return "Slightly Overbought";
        if (rsi < 80) return "Overbought - Sell Signal";
        return "Extremely Overbought - Strong Sell";
    }

    private Color getRsiColor(double rsi)
    {
        if (rsi < 30) return new Color(0, 200, 83);
        if (rsi < 45) return new Color(100, 200, 100);
        if (rsi < 55) return Color.LIGHT_GRAY;
        if (rsi < 70) return new Color(255, 180, 0);
        return Color.RED;
    }

    private JPanel createSectionHeader(String text)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 25));

        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(ColorScheme.BRAND_ORANGE);
        panel.add(label, BorderLayout.WEST);

        return panel;
    }

    private JPanel createInfoRow(String label, String value)
    {
        return createInfoRow(label, value, Color.WHITE);
    }

    private JPanel createInfoRow(String label, String value, Color valueColor)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(2, 10, 2, 10));
        row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 20));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(FontManager.getRunescapeSmallFont());
        labelComp.setForeground(Color.GRAY);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(FontManager.getRunescapeSmallFont());
        valueComp.setForeground(valueColor);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.EAST);

        return row;
    }

    private JPanel createScoreRow(String label, double score)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(2, 10, 2, 10));
        row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 18));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(FontManager.getRunescapeSmallFont());
        labelComp.setForeground(Color.GRAY);
        labelComp.setPreferredSize(new Dimension(90, 14));

        JPanel barPanel = createScoreBar(score, 80);
        barPanel.setPreferredSize(new Dimension(80, 6));

        JLabel valueComp = new JLabel(String.format("%.0f", score));
        valueComp.setFont(FontManager.getRunescapeSmallFont());
        valueComp.setForeground(getScoreColor(score));
        valueComp.setPreferredSize(new Dimension(25, 14));

        row.add(labelComp, BorderLayout.WEST);
        row.add(barPanel, BorderLayout.CENTER);
        row.add(valueComp, BorderLayout.EAST);

        return row;
    }

    private JPanel createWarningRow(String warning)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(2, 10, 2, 10));
        row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 20));

        JLabel label = new JLabel("• " + warning);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(new Color(255, 180, 0));

        row.add(label, BorderLayout.WEST);
        return row;
    }

    private Color getRecommendationColor(FlipScorer.Recommendation rec)
    {
        switch (rec)
        {
            case STRONG_BUY:
                return new Color(0, 200, 83);
            case BUY:
                return new Color(100, 200, 100);
            case CONSIDER:
                return new Color(255, 200, 0);
            case CAUTION:
                return new Color(255, 144, 0);
            case AVOID:
                return Color.RED;
            default:
                return Color.GRAY;
        }
    }

    private Color getConfidenceColor(FlipScorer.Confidence conf)
    {
        switch (conf)
        {
            case VERY_HIGH:
                return new Color(0, 200, 83);
            case HIGH:
                return new Color(100, 200, 100);
            case MEDIUM:
                return new Color(255, 200, 0);
            case LOW:
                return new Color(255, 144, 0);
            case VERY_LOW:
                return Color.RED;
            default:
                return Color.GRAY;
        }
    }

    private Color getScoreColor(double score)
    {
        if (score >= 75) return new Color(0, 200, 83);
        if (score >= 60) return new Color(100, 200, 100);
        if (score >= 45) return new Color(255, 200, 0);
        if (score >= 30) return new Color(255, 144, 0);
        return Color.RED;
    }

    private String truncate(String text, int maxLen)
    {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + "…" : text;
    }

    private long parseValue(String input)
    {
        if (input == null || input.trim().isEmpty()) return Long.MAX_VALUE;
        String clean = input.toLowerCase().replaceAll("[^0-9.kmbt]", "");
        if (clean.isEmpty()) return Long.MAX_VALUE;

        double multiplier = 1;
        if (clean.endsWith("k")) multiplier = 1_000;
        else if (clean.endsWith("m")) multiplier = 1_000_000;
        else if (clean.endsWith("b")) multiplier = 1_000_000_000;
        else if (clean.endsWith("t")) multiplier = 1_000_000_000_000L;

        String number = clean.replaceAll("[^0-9.]", "");
        if (number.isEmpty()) return Long.MAX_VALUE;

        try
        {
            return (long) (Double.parseDouble(number) * multiplier);
        }
        catch (Exception e)
        {
            return Long.MAX_VALUE;
        }
    }

    private double parseDouble(String input)
    {
        if (input == null || input.trim().isEmpty()) return 0;
        try
        {
            return Double.parseDouble(input.trim());
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    // ===== WATCHLIST METHODS =====

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

                    FlipScorer.FlipScore score = flipScorer.calculateScore(result.getId());
                    newItem.score = score.overallScore;
                    newItem.recommendation = score.recommendation.toString();

                    WikiPriceService.WikiPrice price = wikiPriceService.getPrice(result.getId());
                    if (price != null)
                    {
                        newItem.highPrice = price.high;
                        newItem.lowPrice = price.low;
                        newItem.profit = price.high - price.low - (int) Math.min(price.high * 0.01, 5000000);
                    }

                    if (watchList.stream().noneMatch(i -> i.id == newItem.id))
                    {
                        watchList.add(0, newItem);
                        saveList();
                    }

                    SwingUtilities.invokeLater(() -> {
                        searchBar.setText("");
                        searchBar.setEditable(true);
                        searchBar.setIcon(IconTextField.Icon.SEARCH);
                        rebuildWatchlist();
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
                SwingUtilities.invokeLater(() -> {
                    searchBar.setEditable(true);
                    searchBar.setIcon(IconTextField.Icon.ERROR);
                });
            }
        });
    }

    private void addItemDirectlyFromScore(FlipScorer.FlipScore score)
    {
        if (watchList.stream().anyMatch(i -> i.id == score.itemId)) return;

        FlipItem newItem = new FlipItem(score.itemName, score.itemId);
        newItem.highPrice = score.sellPrice;
        newItem.lowPrice = score.buyPrice;
        newItem.profit = score.netMargin;
        newItem.score = score.overallScore;
        newItem.recommendation = score.recommendation.toString();

        watchList.add(0, newItem);
        saveList();
        rebuildWatchlist();
    }

    private void rebuildWatchlist()
    {
        watchlistContainer.removeAll();
        for (FlipItem item : watchList)
        {
            watchlistContainer.add(createWatchlistRow(item));
            watchlistContainer.add(Box.createRigidArea(new Dimension(0, 5)));
        }
        watchlistContainer.revalidate();
        watchlistContainer.repaint();
    }

    private JPanel createWatchlistRow(FlipItem item)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(5, 5, 5, 5));
        row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 55));

        JLabel iconLabel = new JLabel();
        AsyncBufferedImage img = itemManager.getImage(item.id);
        img.addTo(iconLabel);

        JPanel iconWrapper = new JPanel(new BorderLayout());
        iconWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        iconWrapper.setPreferredSize(new Dimension(40, 32));
        iconWrapper.add(iconLabel, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoPanel.setBorder(new EmptyBorder(0, 8, 0, 0));

        JLabel nameLabel = new JLabel(item.name);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel profitLabel = new JLabel("Margin: " + QuantityFormatter.quantityToStackSize(item.profit) +
                " | Score: " + String.format("%.0f", item.score));
        profitLabel.setForeground(item.profit > 0 ? Color.GREEN : Color.RED);
        profitLabel.setFont(FontManager.getRunescapeSmallFont());

        infoPanel.add(nameLabel);
        infoPanel.add(profitLabel);

        JLabel deleteBtn = new JLabel(" ✕");
        deleteBtn.setForeground(Color.RED);
        deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

        row.add(iconWrapper, BorderLayout.WEST);
        row.add(infoPanel, BorderLayout.CENTER);
        row.add(deleteBtn, BorderLayout.EAST);

        return row;
    }

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
        if (!WATCHLIST_FILE.exists()) return;
        try (Reader reader = new FileReader(WATCHLIST_FILE))
        {
            Type listType = new TypeToken<ArrayList<FlipItem>>(){}.getType();
            List<FlipItem> loaded = gson.fromJson(reader, listType);
            if (loaded != null)
            {
                watchList.clear();
                watchList.addAll(loaded);
            }
        }
        catch (IOException e)
        {
            log.error("Failed to load watchlist", e);
        }
        rebuildWatchlist();
    }

    private void openHistoryWindow()
    {
        JFrame historyFrame = new JFrame("Trade History");
        historyFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // FIXED: Pass clientThread to HistoryPanel for thread-safe item name fetching
        HistoryPanel historyPanel = new HistoryPanel(itemManager, clientThread, sessionManager);
        historyFrame.add(historyPanel);
        historyFrame.setSize(400, 600);
        historyFrame.setLocationRelativeTo(this);
        historyFrame.setVisible(true);
    }

    public void init()
    {
        // Auto-run initial scan
        runPredictionScanner();
    }

    private static class FlipItem
    {
        String name;
        int id;
        int highPrice;
        int lowPrice;
        int profit;
        double score;
        String recommendation;

        FlipItem(String name, int id)
        {
            this.name = name;
            this.id = id;
        }
    }
}