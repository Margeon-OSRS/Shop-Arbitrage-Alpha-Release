package com.margeon.shoparbitrage;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class SlayerPanel extends PluginPanel
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SlayerPanel.class);

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final WikiPriceService wikiPriceService;
    private final ShopArbitrageConfig config;

    private final JPanel listContainer = new JPanel();
    private final JLabel statusLabel = new JLabel("Click refresh to scan");

    // Filter/Sort controls
    private final JComboBox<String> sortDropdown = new JComboBox<>(new String[]{
            "Profit (High → Low)",
            "Profit (Low → High)",
            "Kills/Hr (High → Low)",
            "Slayer Level (Low → High)",
            "Combat Level (Low → High)"
    });
    private final JCheckBox slayerTaskOnlyCheckbox = new JCheckBox("Slayer Tasks Only");

    // Cached results
    private List<SlayerMonster> cachedMonsters = new ArrayList<>();

    // UI Assets
    private static final ImageIcon REFRESH_ICON;
    private static final ImageIcon REFRESH_HOVER_ICON;

    static
    {
        try
        {
            final BufferedImage refreshIcon = ImageUtil.loadImageResource(ShopArbitragePanel.class, "/util/arrow_right.png");
            REFRESH_ICON = new ImageIcon(refreshIcon);
            REFRESH_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(refreshIcon, -80));
        }
        catch (Exception e)
        {
            // Fallback to text if icon fails to load
            throw new RuntimeException("Failed to load refresh icon", e);
        }
    }

    public SlayerPanel(ItemManager itemManager, ClientThread clientThread,
                       WikiPriceService wikiPriceService, ShopArbitrageConfig config)
    {
        super(false);
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.wikiPriceService = wikiPriceService;
        this.config = config;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(createHeader(), BorderLayout.NORTH);

        // List container
        listContainer.setLayout(new GridBagLayout());
        listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeader()
    {
        JPanel headerContainer = new JPanel();
        headerContainer.setLayout(new BoxLayout(headerContainer, BoxLayout.Y_AXIS));
        headerContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerContainer.setBorder(new EmptyBorder(10, 10, 5, 10));

        // Top row: Title + Refresh
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Slayer Profit");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFont(FontManager.getRunescapeSmallFont());
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setToolTipText("Calculate Slayer Profits");
        refreshBtn.addActionListener(e -> refreshMonsterData());

        titleRow.add(title, BorderLayout.CENTER);
        titleRow.add(refreshBtn, BorderLayout.EAST);

        // Filter row: Sort + Slayer task checkbox
        JPanel filterRow = new JPanel(new BorderLayout(5, 0));
        filterRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterRow.setBorder(new EmptyBorder(5, 0, 5, 0));

        JPanel sortPanel = new JPanel(new BorderLayout(5, 0));
        sortPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel sortLabel = new JLabel("Sort:");
        sortLabel.setForeground(Color.LIGHT_GRAY);
        sortLabel.setFont(FontManager.getRunescapeSmallFont());

        sortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortDropdown.setForeground(Color.WHITE);
        sortDropdown.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.addActionListener(e -> applyFiltersAndSort());

        sortPanel.add(sortLabel, BorderLayout.WEST);
        sortPanel.add(sortDropdown, BorderLayout.CENTER);

        slayerTaskOnlyCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        slayerTaskOnlyCheckbox.setForeground(Color.LIGHT_GRAY);
        slayerTaskOnlyCheckbox.setFont(FontManager.getRunescapeSmallFont());
        slayerTaskOnlyCheckbox.addActionListener(e -> applyFiltersAndSort());

        filterRow.add(sortPanel, BorderLayout.CENTER);
        filterRow.add(slayerTaskOnlyCheckbox, BorderLayout.EAST);

        // Status label
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setBorder(new EmptyBorder(5, 0, 0, 0));

        headerContainer.add(titleRow);
        headerContainer.add(filterRow);
        headerContainer.add(statusLabel);

        return headerContainer;
    }

    private void refreshMonsterData()
    {
        statusLabel.setText("Fetching prices...");
        statusLabel.setForeground(Color.YELLOW);

        wikiPriceService.fetchLivePrices(() -> {
            clientThread.invoke(() -> {
                try
                {
                    // Load monsters
                    List<SlayerMonster> monsters = SlayerDataLoader.loadMonsters();

                    // Calculate profits
                    SlayerProfitCalculator calculator = new SlayerProfitCalculator(wikiPriceService);
                    calculator.calculateAllProfits(monsters);

                    // Cache and display
                    cachedMonsters = monsters;

                    SwingUtilities.invokeLater(() -> {
                        applyFiltersAndSort();
                    });
                }
                catch (Exception e)
                {
                    log.error("Error calculating slayer profits", e);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error calculating profits");
                        statusLabel.setForeground(Color.RED);
                    });
                }
            });
        });
    }

    private void applyFiltersAndSort()
    {
        if (cachedMonsters.isEmpty())
        {
            return;
        }

        // Filter
        List<SlayerMonster> filtered = new ArrayList<>();
        boolean slayerTaskOnly = slayerTaskOnlyCheckbox.isSelected();

        for (SlayerMonster monster : cachedMonsters)
        {
            if (slayerTaskOnly && !monster.isSlayerTask())
            {
                continue;
            }
            filtered.add(monster);
        }

        // Sort
        String sortOption = (String) sortDropdown.getSelectedItem();

        if ("Profit (High → Low)".equals(sortOption))
        {
            filtered.sort((m1, m2) -> Long.compare(m2.getProfitPerHour(), m1.getProfitPerHour()));
        }
        else if ("Profit (Low → High)".equals(sortOption))
        {
            filtered.sort((m1, m2) -> Long.compare(m1.getProfitPerHour(), m2.getProfitPerHour()));
        }
        else if ("Kills/Hr (High → Low)".equals(sortOption))
        {
            filtered.sort((m1, m2) -> Integer.compare(m2.getKillsPerHour(), m1.getKillsPerHour()));
        }
        else if ("Slayer Level (Low → High)".equals(sortOption))
        {
            filtered.sort((m1, m2) -> Integer.compare(m1.getSlayerLevel(), m2.getSlayerLevel()));
        }
        else if ("Combat Level (Low → High)".equals(sortOption))
        {
            filtered.sort((m1, m2) -> Integer.compare(m1.getCombatLevel(), m2.getCombatLevel()));
        }

        updateMonsterList(filtered);
    }

    private void updateMonsterList(List<SlayerMonster> monsters)
    {
        listContainer.removeAll();
        statusLabel.setText("Found " + monsters.size() + " monsters");
        statusLabel.setForeground(Color.GREEN);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 8, 0);

        for (SlayerMonster monster : monsters)
        {
            listContainer.add(createMonsterCard(monster), c);
            c.gridy++;
        }

        c.weighty = 1;
        listContainer.add(new JPanel(), c);

        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel createMonsterCard(SlayerMonster monster)
    {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Left: Monster info
        JPanel infoPanel = new JPanel(new GridLayout(3, 1));
        infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(monster.getName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());

        JLabel levelLabel = new JLabel("Slayer: " + monster.getSlayerLevel() +
                " | Combat: " + monster.getCombatLevel());
        levelLabel.setForeground(Color.GRAY);
        levelLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel killsLabel = new JLabel(monster.getKillsPerHour() + " kills/hr");
        killsLabel.setForeground(Color.LIGHT_GRAY);
        killsLabel.setFont(FontManager.getRunescapeSmallFont());

        infoPanel.add(nameLabel);
        infoPanel.add(levelLabel);
        infoPanel.add(killsLabel);

        // Right: Profit
        JPanel profitPanel = new JPanel(new GridLayout(2, 1));
        profitPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel hourlyLabel = new JLabel(QuantityFormatter.quantityToStackSize(monster.getProfitPerHour()) + "/hr");
        hourlyLabel.setFont(FontManager.getRunescapeBoldFont());
        hourlyLabel.setForeground(getProfitColor(monster.getProfitPerHour()));
        hourlyLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel perKillLabel = new JLabel(QuantityFormatter.quantityToStackSize(monster.getProfitPerKill()) + "/kill");
        perKillLabel.setFont(FontManager.getRunescapeSmallFont());
        perKillLabel.setForeground(Color.GRAY);
        perKillLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        profitPanel.add(hourlyLabel);
        profitPanel.add(perKillLabel);

        card.add(infoPanel, BorderLayout.CENTER);
        card.add(profitPanel, BorderLayout.EAST);

        return card;
    }

    /**
     * Returns color based on profit tier thresholds from config
     */
    private Color getProfitColor(long profit)
    {
        if (profit >= config.highProfitThreshold())
        {
            return new Color(0, 200, 83); // Bright green
        }
        else if (profit >= config.mediumProfitThreshold())
        {
            return new Color(255, 144, 0); // Orange
        }
        else if (profit > 0)
        {
            return new Color(255, 200, 0); // Yellow
        }
        else
        {
            return Color.RED;
        }
    }

    public void init()
    {
        // Auto-refresh on init
        refreshMonsterData();
    }
}