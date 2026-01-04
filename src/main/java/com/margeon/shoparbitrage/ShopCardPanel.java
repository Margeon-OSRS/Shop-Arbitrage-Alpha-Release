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
        setBorder(new EmptyBorder(5, 0, 5, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // --- HEADER ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(8, 8, 8, 8));

        // LEFT: Checkbox for route planning
        selectionCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        selectionCheckbox.setToolTipText("Select for route planning");
        selectionCheckbox.setBorder(new EmptyBorder(0, 0, 0, 5));

        // WEST: Arrow + Checkbox
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        arrowLabel = new JLabel("▶");
        arrowLabel.setForeground(Color.GRAY);
        arrowLabel.setBorder(new EmptyBorder(0, 0, 0, 8));

        leftPanel.add(arrowLabel);
        leftPanel.add(selectionCheckbox);

        // CENTER: Name + Location
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel titleInfo = new JPanel(new GridLayout(2, 1));
        titleInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(shop.getName());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);

        String distStr = shop.getDistanceToBank() + " tiles to bank";
        JLabel locationLabel = new JLabel(distStr);
        locationLabel.setFont(FontManager.getRunescapeSmallFont());
        locationLabel.setForeground(Color.GRAY);

        titleInfo.add(nameLabel);
        titleInfo.add(locationLabel);
        centerPanel.add(titleInfo, BorderLayout.CENTER);

        // Teleport Icon (with null safety)
        if (shop.getTeleportId() > 0)
        {
            JLabel teleportIcon = createTeleportIcon(shop.getTeleportId());
            if (teleportIcon != null)
            {
                centerPanel.add(teleportIcon, BorderLayout.EAST);
            }
        }

        // RIGHT: Profit + Shop Icon
        JPanel rightInfo = new JPanel(new BorderLayout());
        rightInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel profitLabel = new JLabel(QuantityFormatter.quantityToStackSize(totalProfit) + "/hr");
        profitLabel.setFont(FontManager.getRunescapeBoldFont());
        profitLabel.setForeground(getProfitColor(totalProfit));
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        profitLabel.setBorder(new EmptyBorder(0, 5, 0, 0));

        // Tooltip showing both hourly and trip profit
        profitLabel.setToolTipText("<html>Hourly: " + QuantityFormatter.formatNumber(totalProfit) + " gp<br>" +
                "Per Trip: " + QuantityFormatter.formatNumber(tripProfit) + " gp</html>");

        // Shop Icon (with null safety)
        JLabel shopIcon = createShopIcon();
        if (shopIcon != null)
        {
            rightInfo.add(shopIcon, BorderLayout.CENTER);
        }

        rightInfo.add(profitLabel, BorderLayout.NORTH);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(centerPanel, BorderLayout.CENTER);
        header.add(rightInfo, BorderLayout.EAST);

        // --- ITEMS LIST ---
        container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        container.setBorder(new EmptyBorder(5, 0, 0, 0));
        container.setVisible(false);

        // IMPROVED: Null safety checks
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
        add(container, BorderLayout.SOUTH);

        // Mouse Listener for Expand/Collapse
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                toggleExpanded();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                setHeaderBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setHeaderBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });
    }

    private void toggleExpanded()
    {
        isExpanded = !isExpanded;
        container.setVisible(isExpanded);
        // FIXED: Proper arrow character encoding
        arrowLabel.setText(isExpanded ? "▼" : "▶");
        revalidate();
        repaint();
    }

    private void setHeaderBackground(Color color)
    {
        Component[] components = getComponents();
        if (components.length > 0 && components[0] instanceof JPanel)
        {
            JPanel header = (JPanel) components[0];
            header.setBackground(color);

            // Update nested panels
            for (Component comp : header.getComponents())
            {
                if (comp instanceof JPanel)
                {
                    ((JPanel) comp).setBackground(color);

                    // Update deeply nested panels
                    for (Component nested : ((JPanel) comp).getComponents())
                    {
                        if (nested instanceof JPanel)
                        {
                            ((JPanel) nested).setBackground(color);
                        }
                    }
                }
            }
        }
    }

    // IMPROVED: Extracted method with null safety
    private JLabel createTeleportIcon(int teleportId)
    {
        try
        {
            JLabel teleportIcon = new JLabel();
            teleportIcon.setBorder(new EmptyBorder(0, 5, 0, 0));
            AsyncBufferedImage teleImg = itemManager.getImage(teleportId);

            if (teleImg != null)
            {
                teleImg.addTo(teleportIcon);
                teleportIcon.setToolTipText("Recommended Teleport");
                return teleportIcon;
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load teleport icon for ID {}: {}", teleportId, e.getMessage());
        }
        return null;
    }

    // IMPROVED: Extracted method with null safety
    private JLabel createShopIcon()
    {
        if (shop.getItems() == null || shop.getItems().isEmpty())
        {
            return null;
        }

        try
        {
            ShopItemData firstItem = shop.getItems().get(0);
            if (firstItem != null)
            {
                JLabel shopIcon = new JLabel();
                AsyncBufferedImage img = itemManager.getImage(firstItem.itemId);

                if (img != null)
                {
                    img.addTo(shopIcon);
                    return shopIcon;
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load shop icon: {}", e.getMessage());
        }
        return null;
    }

    public long getProfit() { return totalProfit; }
    public long getTripProfit() { return tripProfit; }
    public String getName() { return shop.getName(); }
    public int getDistance() { return shop.getDistanceToBank(); }

    /**
     * Check if this shop is selected for route planning
     */
    public boolean isSelected()
    {
        return selectionCheckbox.isSelected();
    }

    /**
     * Set selection state
     */
    public void setSelected(boolean selected)
    {
        selectionCheckbox.setSelected(selected);
    }

    /**
     * Get the shop data
     */
    public ShopData getShopData()
    {
        return shop;
    }

    /**
     * Returns color based on profit tier thresholds from config
     */
    private Color getProfitColor(long profit)
    {
        if (profit >= config.highProfitThreshold())
        {
            return new Color(0, 200, 83); // Bright green for high profit
        }
        else if (profit >= config.mediumProfitThreshold())
        {
            return new Color(255, 144, 0); // Orange for medium profit
        }
        else if (profit > 0)
        {
            return new Color(255, 200, 0); // Yellow for low profit
        }
        else
        {
            return Color.RED; // Red for no profit
        }
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
    }

    private JPanel createItemRow(ShopItemData item, int gePrice)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(2, 15, 2, 5));

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

        JPanel statsPanel = new JPanel(new GridLayout(2, 1));
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsPanel.setPreferredSize(new Dimension(85, 32));

        JLabel profitVal = new JLabel((totalRowProfit > 0 ? "+" : "") +
                QuantityFormatter.quantityToStackSize(totalRowProfit));
        profitVal.setHorizontalAlignment(SwingConstants.RIGHT);
        profitVal.setForeground(totalRowProfit > 0 ? Color.GREEN : Color.RED);
        profitVal.setFont(FontManager.getRunescapeSmallFont());

        JLabel pricesLabel = new JLabel("B: " + QuantityFormatter.quantityToStackSize(item.shopPrice)
                + " | S: " + QuantityFormatter.quantityToStackSize(gePrice));
        pricesLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        pricesLabel.setForeground(Color.GRAY);
        pricesLabel.setFont(FontManager.getRunescapeSmallFont());

        statsPanel.add(profitVal);
        statsPanel.add(pricesLabel);
        statsPanel.setToolTipText(tooltip);

        JLabel nameLabel = new JLabel(item.quantity + " " + item.itemName);
        nameLabel.setForeground(Color.LIGHT_GRAY);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        nameLabel.setToolTipText(tooltip);

        row.add(nameLabel, BorderLayout.CENTER);
        row.add(statsPanel, BorderLayout.EAST);

        return row;
    }
}