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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Flipping Panel with prediction scores and confidence ratings
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

    // UI Components
    private final JPanel cardPanel = new JPanel(new CardLayout());
    private final JPanel watchlistPanel = new JPanel(new BorderLayout());
    private final JPanel scannerPanel = new JPanel(new BorderLayout());
    private final JPanel analysisPanel = new JPanel(new BorderLayout());

    private final JPanel watchlistContainer = new JPanel();
    private final IconTextField searchBar = new IconTextField();
    private final List<FlipItem> watchList = new ArrayList<>();

    private final JPanel scannerContainer = new JPanel();
    private final JLabel scannerStatus = new JLabel("Click Scan to find predictions");
    private final JTextField maxCashInput = new JTextField("100M");
    private final JTextField minVolumeInput = new JTextField("500");

    // Sorting
    private final JComboBox<String> sortDropdown = new JComboBox<>(new String[]{
            "Score (High → Low)",
            "Score (Low → High)",
            "Margin (High → Low)",
            "Profit/Hr (High → Low)",
            "Volume (High → Low)",
            "ROI (High → Low)",
            "Name (A → Z)"
    });

    // Cached results for sorting
    private List<FlipScorer.FlipScore> cachedPredictions = new ArrayList<>();

    // Analysis panel components
    private final JPanel analysisContainer = new JPanel();
    private FlipScorer.FlipScore selectedItem;

    private final JLabel sessionProfitLabel = new JLabel("Session: 0 gp");
    private final JButton viewHistoryButton = new JButton("History");

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
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Max price input
        JPanel priceRow = new JPanel(new BorderLayout(5, 0));
        priceRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel priceLabel = new JLabel("Max Price:");
        priceLabel.setForeground(Color.LIGHT_GRAY);
        priceLabel.setFont(FontManager.getRunescapeSmallFont());
        maxCashInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        maxCashInput.setForeground(Color.WHITE);
        maxCashInput.setToolTipText("e.g. 10m, 500k, 1b");
        priceRow.add(priceLabel, BorderLayout.WEST);
        priceRow.add(maxCashInput, BorderLayout.CENTER);

        // Min volume input
        JPanel volumeRow = new JPanel(new BorderLayout(5, 0));
        volumeRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        volumeRow.setBorder(new EmptyBorder(5, 0, 0, 0));
        JLabel volumeLabel = new JLabel("Min Volume:");
        volumeLabel.setForeground(Color.LIGHT_GRAY);
        volumeLabel.setFont(FontManager.getRunescapeSmallFont());
        minVolumeInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        minVolumeInput.setForeground(Color.WHITE);
        minVolumeInput.setToolTipText("Daily trading volume");
        volumeRow.add(volumeLabel, BorderLayout.WEST);
        volumeRow.add(minVolumeInput, BorderLayout.CENTER);

        // Scan button
        JButton scanBtn = new JButton("🔮 Run Prediction Scan");
        scanBtn.setFocusPainted(false);
        scanBtn.setBackground(new Color(40, 100, 60));
        scanBtn.setForeground(Color.WHITE);
        scanBtn.setFont(FontManager.getRunescapeBoldFont());
        scanBtn.setBorder(new EmptyBorder(10, 0, 10, 0));
        scanBtn.addActionListener(e -> runPredictionScanner());

        JPanel scanBtnWrapper = new JPanel(new BorderLayout());
        scanBtnWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scanBtnWrapper.setBorder(new EmptyBorder(10, 0, 0, 0));
        scanBtnWrapper.add(scanBtn, BorderLayout.CENTER);

        // Sort dropdown row
        JPanel sortRow = new JPanel(new BorderLayout(5, 0));
        sortRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sortRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setForeground(Color.LIGHT_GRAY);
        sortLabel.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortDropdown.setForeground(Color.WHITE);
        sortDropdown.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.addActionListener(e -> applySortToResults());
        sortRow.add(sortLabel, BorderLayout.WEST);
        sortRow.add(sortDropdown, BorderLayout.CENTER);

        // Status
        scannerStatus.setHorizontalAlignment(SwingConstants.CENTER);
        scannerStatus.setBorder(new EmptyBorder(5, 0, 0, 0));
        scannerStatus.setForeground(Color.GRAY);
        scannerStatus.setFont(FontManager.getRunescapeSmallFont());

        // Legend
        JPanel legendPanel = createLegendPanel();
        legendPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        header.add(priceRow);
        header.add(volumeRow);
        header.add(scanBtnWrapper);
        header.add(sortRow);
        header.add(scannerStatus);
        header.add(legendPanel);

        scannerPanel.add(header, BorderLayout.NORTH);

        scannerContainer.setLayout(new BoxLayout(scannerContainer, BoxLayout.Y_AXIS));
        scannerContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scannerContainer.setBorder(new EmptyBorder(5, 5, 5, 5));
        scannerContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(scannerContainer);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scannerPanel.add(scroll, BorderLayout.CENTER);
    }

    private JPanel createLegendPanel()
    {
        JPanel legendContainer = new JPanel();
        legendContainer.setLayout(new BoxLayout(legendContainer, BoxLayout.Y_AXIS));
        legendContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        legend.setBackground(ColorScheme.DARK_GRAY_COLOR);

        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.STRONG_BUY), "Strong"));
        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.BUY), "Buy"));
        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.CONSIDER), "Consider"));
        legend.add(createLegendItem("●", getRecommendationColor(FlipScorer.Recommendation.CAUTION), "Caution"));

        JLabel clickHint = new JLabel("Click any item for detailed analysis");
        clickHint.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
        clickHint.setForeground(new Color(150, 150, 150));
        clickHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        hintPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        hintPanel.add(clickHint);

        legendContainer.add(legend);
        legendContainer.add(hintPanel);

        return legendContainer;
    }

    private JLabel createLegendItem(String icon, Color color, String text)
    {
        JLabel label = new JLabel(icon + " " + text);
        label.setForeground(color);
        label.setFont(FontManager.getRunescapeSmallFont());
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

        JLabel hint1 = new JLabel("To analyze an item:");
        hint1.setForeground(Color.LIGHT_GRAY);
        hint1.setFont(FontManager.getRunescapeSmallFont());
        hint1.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint1.setBorder(new EmptyBorder(15, 0, 5, 0));

        JLabel hint2 = new JLabel("1. Go to the 'Predict' tab");
        hint2.setForeground(Color.GRAY);
        hint2.setFont(FontManager.getRunescapeSmallFont());
        hint2.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint3 = new JLabel("2. Run a prediction scan");
        hint3.setForeground(Color.GRAY);
        hint3.setFont(FontManager.getRunescapeSmallFont());
        hint3.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint4 = new JLabel("3. Click any item card for details");
        hint4.setForeground(Color.GRAY);
        hint4.setFont(FontManager.getRunescapeSmallFont());
        hint4.setAlignmentX(Component.CENTER_ALIGNMENT);

        placeholderPanel.add(Box.createVerticalGlue());
        placeholderPanel.add(icon);
        placeholderPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        placeholderPanel.add(title);
        placeholderPanel.add(hint1);
        placeholderPanel.add(hint2);
        placeholderPanel.add(hint3);
        placeholderPanel.add(hint4);
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
     * Run the prediction scanner
     */
    private void runPredictionScanner()
    {
        scannerStatus.setText("Fetching price data...");
        scannerStatus.setForeground(Color.YELLOW);
        scannerContainer.removeAll();
        scannerContainer.revalidate();

        long maxPrice = parseValue(maxCashInput.getText());
        long minVolume = parseValue(minVolumeInput.getText());
        int resultLimit = config.resultLimit();

        // Step 1: Fetch latest prices
        wikiPriceService.fetchLivePrices(() -> {
            SwingUtilities.invokeLater(() -> scannerStatus.setText("Fetching 5-minute data..."));

            // Step 2: Fetch 5-minute historical data
            priceHistoryService.fetchFiveMinuteData(() -> {
                SwingUtilities.invokeLater(() -> scannerStatus.setText("Calculating predictions..."));

                // Step 3: Calculate metrics
                priceHistoryService.calculateAllMetrics();

                // Step 4: Calculate scores
                List<FlipScorer.FlipScore> scores = flipScorer.calculateAllScores(minVolume, maxPrice, resultLimit);

                // Step 5: Get item names on client thread (required for ItemManager)
                clientThread.invoke(() -> {
                    for (FlipScorer.FlipScore score : scores)
                    {
                        try
                        {
                            score.itemName = itemManager.getItemComposition(score.itemId).getName();
                        }
                        catch (Exception e)
                        {
                            log.warn("Failed to get name for item {}", score.itemId);
                            score.itemName = "Unknown Item";
                        }
                    }

                    // Step 6: Update UI on Swing thread
                    SwingUtilities.invokeLater(() -> {
                        displayScanResults(scores);
                    });
                });
            });
        });
    }

    private void displayScanResults(List<FlipScorer.FlipScore> scores)
    {
        // Cache results for sorting
        cachedPredictions = new ArrayList<>(scores);

        // Apply current sort
        applySortToResults();
    }

    /**
     * Sort and redisplay the cached prediction results
     */
    private void applySortToResults()
    {
        if (cachedPredictions.isEmpty())
        {
            return;
        }

        // Sort based on dropdown selection
        String sortOption = (String) sortDropdown.getSelectedItem();
        List<FlipScorer.FlipScore> sorted = new ArrayList<>(cachedPredictions);

        if ("Score (High → Low)".equals(sortOption))
        {
            sorted.sort((a, b) -> Double.compare(b.overallScore, a.overallScore));
        }
        else if ("Score (Low → High)".equals(sortOption))
        {
            sorted.sort((a, b) -> Double.compare(a.overallScore, b.overallScore));
        }
        else if ("Margin (High → Low)".equals(sortOption))
        {
            sorted.sort((a, b) -> Long.compare(b.netMargin, a.netMargin));
        }
        else if ("Profit/Hr (High → Low)".equals(sortOption))
        {
            sorted.sort((a, b) -> Long.compare(b.estimatedHourlyProfit, a.estimatedHourlyProfit));
        }
        else if ("Volume (High → Low)".equals(sortOption))
        {
            sorted.sort((a, b) -> Long.compare(b.dailyVolume, a.dailyVolume));
        }
        else if ("ROI (High → Low)".equals(sortOption))
        {
            sorted.sort((a, b) -> Double.compare(b.roi, a.roi));
        }
        else if ("Name (A → Z)".equals(sortOption))
        {
            sorted.sort((a, b) -> {
                String nameA = a.itemName != null ? a.itemName : "";
                String nameB = b.itemName != null ? b.itemName : "";
                return nameA.compareTo(nameB);
            });
        }

        // Update UI
        scannerContainer.removeAll();

        if (sorted.isEmpty())
        {
            scannerStatus.setText("No predictions found matching criteria");
            scannerStatus.setForeground(Color.RED);
        }
        else
        {
            scannerStatus.setText("Found " + sorted.size() + " predictions");
            scannerStatus.setForeground(Color.GREEN);

            for (FlipScorer.FlipScore score : sorted)
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

        // Fix alignment - set both preferred and maximum size
        Dimension cardSize = new Dimension(PluginPanel.PANEL_WIDTH - 10, 85);
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
        centerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Name row
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String displayName = score.itemName != null ? score.itemName : "Unknown";
        JLabel nameLabel = new JLabel(truncate(displayName, 20));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameRow.add(nameLabel);

        // Recommendation label
        JPanel recRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        recRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel recLabel = new JLabel(formatRecommendation(score.recommendation));
        recLabel.setForeground(getRecommendationColor(score.recommendation));
        recLabel.setFont(FontManager.getRunescapeSmallFont());
        recRow.add(recLabel);

        // Score bar - mark the entire row to preserve colors
        JPanel scoreBarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        scoreBarRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scoreBarRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        scoreBarRow.putClientProperty("isScoreBarFill", true); // Don't change this row's background
        scoreBarRow.add(createScoreBar(score.overallScore, 100));

        // Profit info
        JPanel profitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        profitRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        profitRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel profitLabel = new JLabel(
                "Margin: " + QuantityFormatter.quantityToStackSize(score.netMargin) +
                        " | ~" + QuantityFormatter.quantityToStackSize(score.estimatedHourlyProfit) + "/hr"
        );
        profitLabel.setForeground(Color.LIGHT_GRAY);
        profitLabel.setFont(FontManager.getRunescapeSmallFont());
        profitRow.add(profitLabel);

        // Confidence row
        JPanel confRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        confRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        confRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel confLabel = new JLabel("Confidence: " + score.confidence);
        confLabel.setForeground(getConfidenceColor(score.confidence));
        confLabel.setFont(FontManager.getRunescapeSmallFont());
        confRow.add(confLabel);

        centerPanel.add(nameRow);
        centerPanel.add(recRow);
        centerPanel.add(scoreBarRow);
        centerPanel.add(profitRow);
        centerPanel.add(confRow);

        // Right: Score number
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rightPanel.setPreferredSize(new Dimension(45, 50));

        JLabel scoreLabel = new JLabel(String.format("%.0f", score.overallScore));
        scoreLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        scoreLabel.setForeground(getScoreColor(score.overallScore));
        scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel scoreSubLabel = new JLabel("SCORE");
        scoreSubLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));
        scoreSubLabel.setForeground(Color.GRAY);
        scoreSubLabel.setHorizontalAlignment(SwingConstants.CENTER);

        rightPanel.add(scoreLabel, BorderLayout.CENTER);
        rightPanel.add(scoreSubLabel, BorderLayout.SOUTH);

        card.add(iconWrapper, BorderLayout.WEST);
        card.add(centerPanel, BorderLayout.CENTER);
        card.add(rightPanel, BorderLayout.EAST);

        // Build tooltip
        StringBuilder tooltip = new StringBuilder("<html>");
        tooltip.append("<b>").append(displayName).append("</b><br>");
        tooltip.append("Buy: ").append(QuantityFormatter.formatNumber(score.buyPrice)).append(" gp<br>");
        tooltip.append("Sell: ").append(QuantityFormatter.formatNumber(score.sellPrice)).append(" gp<br>");
        tooltip.append("Net Margin: ").append(QuantityFormatter.formatNumber(score.netMargin)).append(" gp<br>");
        tooltip.append("GE Tax: ").append(QuantityFormatter.formatNumber(score.geTax)).append(" gp<br>");
        tooltip.append("Daily Volume: ").append(QuantityFormatter.formatNumber(score.dailyVolume)).append("<br>");
        tooltip.append("ROI: ").append(String.format("%.2f%%", score.roi)).append("<br>");
        if (score.buyLimit > 0)
        {
            tooltip.append("Buy Limit: ").append(QuantityFormatter.formatNumber(score.buyLimit)).append(" / 4hr<br>");
            tooltip.append("Profit/Cycle: ").append(QuantityFormatter.formatNumber(score.profitPerCycle)).append(" gp<br>");
        }
        tooltip.append("<br><b>Component Scores:</b><br>");
        tooltip.append("Margin: ").append(String.format("%.0f", score.marginScore)).append("<br>");
        tooltip.append("Volume: ").append(String.format("%.0f", score.volumeScore)).append("<br>");
        tooltip.append("Stability: ").append(String.format("%.0f", score.stabilityScore)).append("<br>");
        tooltip.append("Trend: ").append(String.format("%.0f", score.trendScore)).append("<br>");
        tooltip.append("Volatility: ").append(String.format("%.0f", score.volatilityScore)).append("<br>");
        tooltip.append("RSI: ").append(String.format("%.0f (%.1f)", score.rsiScore, score.rsi)).append("<br>");
        if (score.warnings != null && !score.warnings.isEmpty())
        {
            tooltip.append("<br><b style='color:orange'>Warnings:</b><br>");
            for (String warning : score.warnings)
            {
                tooltip.append("- ").append(warning).append("<br>");
            }
        }
        tooltip.append("<br><i>").append(score.reason).append("</i>");
        tooltip.append("</html>");

        card.setToolTipText(tooltip.toString());

        // Click to analyze
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
                // Skip score bar fill - marked with client property
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
        barContainer.setPreferredSize(new Dimension(width, 8));
        barContainer.setMaximumSize(new Dimension(width, 8));
        barContainer.putClientProperty("isScoreBarFill", true); // Preserve on hover

        JPanel barBg = new JPanel(new BorderLayout());
        barBg.setBackground(Color.DARK_GRAY); // Fixed dark background for contrast
        barBg.setBorder(new LineBorder(new Color(30, 30, 30), 1));
        barBg.putClientProperty("isScoreBarFill", true);

        JPanel barFill = new JPanel();
        barFill.setBackground(getScoreColor(score));
        barFill.setPreferredSize(new Dimension((int) (width * score / 100), 6));
        barFill.putClientProperty("isScoreBarFill", true);
        barFill.setOpaque(true); // Ensure it's painted

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

        JLabel iconLabel = new JLabel();
        try
        {
            AsyncBufferedImage img = itemManager.getImage(score.itemId);
            if (img != null) img.addTo(iconLabel);
        }
        catch (Exception ignored) {}

        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titlePanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel nameLabel = new JLabel(score.itemName);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);

        JLabel recLabel = new JLabel(score.recommendation + " • Score: " + String.format("%.0f", score.overallScore));
        recLabel.setFont(FontManager.getRunescapeSmallFont());
        recLabel.setForeground(getRecommendationColor(score.recommendation));

        titlePanel.add(nameLabel);
        titlePanel.add(recLabel);

        headerPanel.add(iconLabel, BorderLayout.WEST);
        headerPanel.add(titlePanel, BorderLayout.CENTER);

        analysisContainer.add(headerPanel);
        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Price Info Section
        analysisContainer.add(createSectionHeader("Price Information"));
        analysisContainer.add(createInfoRow("Buy Price", QuantityFormatter.formatNumber(score.buyPrice) + " gp"));
        analysisContainer.add(createInfoRow("Sell Price", QuantityFormatter.formatNumber(score.sellPrice) + " gp"));
        analysisContainer.add(createInfoRow("Raw Margin", QuantityFormatter.formatNumber(score.rawMargin) + " gp"));
        analysisContainer.add(createInfoRow("GE Tax", QuantityFormatter.formatNumber(score.geTax) + " gp"));
        analysisContainer.add(createInfoRow("Net Margin", QuantityFormatter.formatNumber(score.netMargin) + " gp", Color.GREEN));
        analysisContainer.add(createInfoRow("ROI", String.format("%.2f%%", score.roi)));

        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Volume & Limits Section
        analysisContainer.add(createSectionHeader("Volume & Limits"));
        analysisContainer.add(createInfoRow("Daily Volume", QuantityFormatter.formatNumber(score.dailyVolume)));
        if (score.buyLimit > 0)
        {
            analysisContainer.add(createInfoRow("Buy Limit", QuantityFormatter.formatNumber(score.buyLimit) + " / 4hr"));
            analysisContainer.add(createInfoRow("Est. Flip Time", String.format("%.1f hours", score.estimatedFlipTimeHours)));
            analysisContainer.add(createInfoRow("Profit/Cycle", QuantityFormatter.formatNumber(score.profitPerCycle) + " gp"));
            analysisContainer.add(createInfoRow("Est. Hourly", QuantityFormatter.formatNumber(score.estimatedHourlyProfit) + " gp/hr", Color.YELLOW));
        }

        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Technical Analysis Section
        analysisContainer.add(createSectionHeader("Technical Analysis"));
        analysisContainer.add(createScoreRow("Margin Score", score.marginScore));
        analysisContainer.add(createScoreRow("Volume Score", score.volumeScore));
        analysisContainer.add(createScoreRow("Stability Score", score.stabilityScore));
        analysisContainer.add(createScoreRow("Trend Score", score.trendScore));
        analysisContainer.add(createScoreRow("Volatility Score", score.volatilityScore));
        analysisContainer.add(createScoreRow("RSI Score", score.rsiScore));
        analysisContainer.add(createInfoRow("RSI Value", String.format("%.1f", score.rsi)));

        // Warnings Section
        if (score.warnings != null && !score.warnings.isEmpty())
        {
            analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));
            analysisContainer.add(createSectionHeader("⚠ Warnings"));
            for (String warning : score.warnings)
            {
                analysisContainer.add(createWarningRow(warning));
            }
        }

        // Reason
        analysisContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        JTextArea reasonArea = new JTextArea(score.reason);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setLineWrap(true);
        reasonArea.setOpaque(false);
        reasonArea.setEditable(false);
        reasonArea.setForeground(Color.LIGHT_GRAY);
        reasonArea.setFont(FontManager.getRunescapeSmallFont());
        reasonArea.setBorder(new EmptyBorder(5, 5, 5, 5));
        analysisContainer.add(reasonArea);

        // Switch to analysis panel
        CardLayout cl = (CardLayout) cardPanel.getLayout();
        cl.show(cardPanel, "ANALYSIS");

        analysisContainer.revalidate();
        analysisContainer.repaint();
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

        // Mini score bar
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

    // Watchlist methods (simplified - reuse from original)
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

                    // Calculate score
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

        JLabel deleteBtn = new JLabel(" X");
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
        HistoryPanel historyPanel = new HistoryPanel(itemManager, sessionManager);
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