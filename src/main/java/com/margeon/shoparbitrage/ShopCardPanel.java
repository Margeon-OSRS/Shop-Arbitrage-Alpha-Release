package com.margeon.shoparbitrage;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

public class ShopCardPanel extends JPanel
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShopCardPanel.class);

    private final ShopData shop;
    private final long totalProfit; // Hourly
    private final long tripProfit;  // Per Inventory
    private final Map<Integer, Integer> itemPrices; // itemId -> gePrice
    private final ShopArbitrageConfig config;
    private boolean isExpanded = false;
    private final JLabel arrowLabel;
    private final JPanel container;
    private final ItemManager itemManager;
    private final JCheckBox selectionCheckbox; // For route planning

    public ShopCardPanel(ShopData shop, ItemManager itemManager, long totalProfit, long tripProfit,
                         Map<Integer, Integer> itemPrices, ShopArbitrageConfig config)
    {
        this.shop = shop;
        this.itemManager = itemManager;
        this.totalProfit = totalProfit;
        this.tripProfit = tripProfit;
        this.itemPrices = itemPrices;
        this.config = config;
        this.selectionCheckbox = new JCheckBox();

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(3, 0, 3, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // --- HEADER ---
        JPanel header = new JPanel(new BorderLayout(5, 0));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 6, 6, 6));

        // LEFT: Arrow + Checkbox
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        leftPanel.setPreferredSize(new Dimension(45, 30));

        arrowLabel = new JLabel("▶");
        arrowLabel.setForeground(Color.GRAY);
        arrowLabel.setFont(FontManager.getRunescapeSmallFont());

        selectionCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        selectionCheckbox.setToolTipText("Select for route planning");

        leftPanel.add(arrowLabel);
        leftPanel.add(selectionCheckbox);

        // CENTER: Name + Distance (stacked)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(shop.getName());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setToolTipText(shop.getName()); // Full name on hover

        JLabel distanceLabel = new JLabel(shop.getDistanceToBank() + " tiles to bank");
        distanceLabel.setFont(FontManager.getRunescapeSmallFont());
        distanceLabel.setForeground(Color.GRAY);
        distanceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        centerPanel.add(nameLabel);
        centerPanel.add(distanceLabel);

        // RIGHT: Profit + Teleport icon
        JPanel rightPanel = new JPanel(new BorderLayout(3, 0));
        rightPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Profit label
        JLabel profitLabel = new JLabel(QuantityFormatter.quantityToStackSize(totalProfit) + "/hr");
        profitLabel.setFont(FontManager.getRunescapeBoldFont());
        profitLabel.setForeground(getProfitColor(totalProfit));
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        profitLabel.setToolTipText("<html>Hourly: " + QuantityFormatter.formatNumber(totalProfit) + " gp<br>" +
                "Per Trip: " + QuantityFormatter.formatNumber(tripProfit) + " gp</html>");

        rightPanel.add(profitLabel, BorderLayout.CENTER);

        // Teleport icon (if available)
        if (shop.getTeleportId() > 0)
        {
            JLabel teleportIcon = createTeleportIcon(shop.getTeleportId());
            if (teleportIcon != null)
            {
                teleportIcon.setBorder(new EmptyBorder(0, 5, 0, 0));
                rightPanel.add(teleportIcon, BorderLayout.EAST);
            }
        }

        header.add(leftPanel, BorderLayout.WEST);
        header.add(centerPanel, BorderLayout.CENTER);
        header.add(rightPanel, BorderLayout.EAST);

        // --- ITEMS LIST (expandable) ---
        container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        container.setBorder(new EmptyBorder(5, 0, 5, 0));
        container.setVisible(false);

        // Add item rows
        if (shop.getItems() != null && !shop.getItems().isEmpty())
        {
            for (ShopItemData item : shop.getItems())
            {
                if (item != null)
                {
                    try
                    {
                        int gePrice = itemPrices.getOrDefault(item.itemId, 0);
                        container.add(createItemRow(item, gePrice));
                    }
                    catch (Exception e)
                    {
                        log.error("Failed to create item row for {}", item.itemName, e);
                    }
                }
            }
        }

        add(header, BorderLayout.NORTH);
        add(container, BorderLayout.CENTER);

        // Mouse Listener for Expand/Collapse
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                // Don't toggle if clicking the checkbox
                if (e.getSource() != selectionCheckbox)
                {
                    toggleExpanded();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                setHeaderBackground(header, ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setHeaderBackground(header, ColorScheme.DARKER_GRAY_COLOR);
            }
        });
    }

    private void toggleExpanded()
    {
        isExpanded = !isExpanded;
        container.setVisible(isExpanded);
        arrowLabel.setText(isExpanded ? "▼" : "▶");
        revalidate();
        repaint();
    }

    private void setHeaderBackground(JPanel header, Color color)
    {
        header.setBackground(color);
        for (Component comp : header.getComponents())
        {
            if (comp instanceof JPanel)
            {
                comp.setBackground(color);
                for (Component nested : ((JPanel) comp).getComponents())
                {
                    if (nested instanceof JPanel)
                    {
                        nested.setBackground(color);
                    }
                }
            }
        }
    }

    private JLabel createTeleportIcon(int teleportId)
    {
        try
        {
            JLabel teleportIcon = new JLabel();
            AsyncBufferedImage teleImg = itemManager.getImage(teleportId);

            if (teleImg != null)
            {
                teleImg.addTo(teleportIcon);
                teleportIcon.setToolTipText("Teleport item required");
                return teleportIcon;
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load teleport icon for ID {}: {}", teleportId, e.getMessage());
        }
        return null;
    }

    private JPanel createItemRow(ShopItemData item, int gePrice)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(2, 15, 2, 5));
        row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 28));

        int margin = gePrice - item.shopPrice;
        int totalRowProfit = margin * item.quantity;

        String tooltip = "<html>"
                + "<b>" + item.itemName + "</b><br>"
                + "Shop Buy: " + QuantityFormatter.formatNumber(item.shopPrice) + " gp<br>"
                + "GE Sell: " + QuantityFormatter.formatNumber(gePrice) + " gp<br>"
                + "Margin: " + (margin > 0 ? "+" : "") + margin + " gp<br>"
                + "Total: " + QuantityFormatter.quantityToStackSize(totalRowProfit)
                + "</html>";

        row.setToolTipText(tooltip);

        // Left: Item icon + name
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel iconLabel = new JLabel();
        try
        {
            AsyncBufferedImage img = itemManager.getImage(item.itemId);
            if (img != null)
            {
                img.addTo(iconLabel);
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load item icon: {}", e.getMessage());
        }

        JLabel nameLabel = new JLabel(item.quantity + "x " + item.itemName);
        nameLabel.setForeground(Color.LIGHT_GRAY);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());

        leftPanel.add(iconLabel);
        leftPanel.add(nameLabel);

        // Right: Profit
        JLabel profitVal = new JLabel((totalRowProfit > 0 ? "+" : "") +
                QuantityFormatter.quantityToStackSize(totalRowProfit));
        profitVal.setHorizontalAlignment(SwingConstants.RIGHT);
        profitVal.setForeground(totalRowProfit > 0 ? Color.GREEN : Color.RED);
        profitVal.setFont(FontManager.getRunescapeSmallFont());

        row.add(leftPanel, BorderLayout.CENTER);
        row.add(profitVal, BorderLayout.EAST);

        return row;
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

    // Getters
    public long getProfit() { return totalProfit; }
    public long getTripProfit() { return tripProfit; }
    public String getName() { return shop.getName(); }
    public int getDistance() { return shop.getDistanceToBank(); }

    public boolean isSelected()
    {
        return selectionCheckbox.isSelected();
    }

    public void setSelected(boolean selected)
    {
        selectionCheckbox.setSelected(selected);
    }

    public ShopData getShopData()
    {
        return shop;
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize()
    {
        return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
    }
}