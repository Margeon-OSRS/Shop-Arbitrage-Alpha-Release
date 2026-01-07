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
    private final long totalProfit;
    private final long tripProfit;
    private final Map<Integer, Integer> itemPrices;
    private final ShopArbitrageConfig config;
    private boolean isExpanded = false;
    private final JLabel arrowLabel;
    private final JPanel container;
    private final ItemManager itemManager;
    private final JCheckBox selectionCheckbox;

    // Colors for special indicators
    private static final Color WILDERNESS_COLOR = new Color(200, 50, 50);
    private static final Color MEMBERS_COLOR = new Color(200, 150, 50);
    private static final Color QUEST_COLOR = new Color(100, 150, 200);

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
        header.setBackground(shop.isWilderness() ? new Color(50, 30, 30) : ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 6, 6, 6));

        // LEFT: Arrow + Checkbox
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        leftPanel.setBackground(header.getBackground());
        leftPanel.setPreferredSize(new Dimension(45, 30));

        arrowLabel = new JLabel("▶");
        arrowLabel.setForeground(Color.GRAY);
        arrowLabel.setFont(FontManager.getRunescapeSmallFont());

        selectionCheckbox.setBackground(header.getBackground());
        selectionCheckbox.setToolTipText("Select for route planning");

        leftPanel.add(arrowLabel);
        leftPanel.add(selectionCheckbox);

        // CENTER: Name + Info (stacked)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(header.getBackground());

        // Name with optional indicators
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        nameRow.setBackground(header.getBackground());
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(shop.getName());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setToolTipText(shop.getName());
        nameRow.add(nameLabel);

        // Wilderness indicator
        if (shop.isWilderness())
        {
            JLabel wildyIcon = new JLabel("☠");
            wildyIcon.setForeground(WILDERNESS_COLOR);
            wildyIcon.setFont(FontManager.getRunescapeSmallFont());
            wildyIcon.setToolTipText("WILDERNESS - PvP danger!");
            nameRow.add(wildyIcon);
        }

        // Requirements indicator
        if (shop.getRequirements() != null && !shop.getRequirements().isEmpty())
        {
            JLabel reqIcon = new JLabel("⚑");
            reqIcon.setForeground(QUEST_COLOR);
            reqIcon.setFont(FontManager.getRunescapeSmallFont());
            reqIcon.setToolTipText("Requires: " + shop.getRequirements());
            nameRow.add(reqIcon);
        }

        // Distance and category info
        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        infoRow.setBackground(header.getBackground());
        infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String distanceText = shop.getDistanceToBank() < 999 
                ? shop.getDistanceToBank() + " tiles" 
                : "No bank nearby";
        
        String categoryText = "";
        if (shop.getCategory() != null && !shop.getCategory().isEmpty())
        {
            categoryText = " • " + formatCategory(shop.getCategory());
        }

        JLabel distanceLabel = new JLabel(distanceText + categoryText);
        distanceLabel.setFont(FontManager.getRunescapeSmallFont());
        distanceLabel.setForeground(Color.GRAY);
        infoRow.add(distanceLabel);

        centerPanel.add(nameRow);
        centerPanel.add(infoRow);

        // RIGHT: Profit + Teleport icon
        JPanel rightPanel = new JPanel(new BorderLayout(3, 0));
        rightPanel.setBackground(header.getBackground());

        // Profit label
        JLabel profitLabel = new JLabel(QuantityFormatter.quantityToStackSize(totalProfit) + "/hr");
        profitLabel.setFont(FontManager.getRunescapeBoldFont());
        profitLabel.setForeground(getProfitColor(totalProfit));
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        
        // Build tooltip
        StringBuilder tooltipBuilder = new StringBuilder("<html>");
        tooltipBuilder.append("<b>Hourly:</b> ").append(QuantityFormatter.formatNumber(totalProfit)).append(" gp<br>");
        tooltipBuilder.append("<b>Per Trip:</b> ").append(QuantityFormatter.formatNumber(tripProfit)).append(" gp");
        
        if (shop.getNotes() != null && !shop.getNotes().isEmpty())
        {
            tooltipBuilder.append("<br><br><i>").append(shop.getNotes()).append("</i>");
        }
        tooltipBuilder.append("</html>");
        
        profitLabel.setToolTipText(tooltipBuilder.toString());

        rightPanel.add(profitLabel, BorderLayout.CENTER);

        // Teleport icon
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

        // Add requirements row if present
        if (shop.getRequirements() != null && !shop.getRequirements().isEmpty())
        {
            JPanel reqPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            reqPanel.setBackground(new Color(40, 50, 60));
            reqPanel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 25));
            
            JLabel reqLabel = new JLabel("⚑ Requires: " + shop.getRequirements());
            reqLabel.setForeground(QUEST_COLOR);
            reqLabel.setFont(FontManager.getRunescapeSmallFont());
            reqPanel.add(reqLabel);
            
            container.add(reqPanel);
        }

        // Add notes row if present
        if (shop.getNotes() != null && !shop.getNotes().isEmpty())
        {
            JPanel notesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            notesPanel.setBackground(new Color(50, 50, 40));
            notesPanel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 25));
            
            JLabel notesLabel = new JLabel("ℹ " + shop.getNotes());
            notesLabel.setForeground(new Color(200, 200, 150));
            notesLabel.setFont(FontManager.getRunescapeSmallFont());
            notesPanel.add(notesLabel);
            
            container.add(notesPanel);
        }

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
                if (e.getSource() != selectionCheckbox)
                {
                    toggleExpanded();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                setHeaderBackground(header, shop.isWilderness() 
                        ? new Color(60, 40, 40) 
                        : ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setHeaderBackground(header, shop.isWilderness() 
                        ? new Color(50, 30, 30) 
                        : ColorScheme.DARKER_GRAY_COLOR);
            }
        });
    }

    private String formatCategory(String category)
    {
        if (category == null) return "";
        String formatted = category.replace("_SHOP", "").replace("_", " ");
        if (formatted.length() > 0)
        {
            return formatted.substring(0, 1).toUpperCase() + formatted.substring(1).toLowerCase();
        }
        return formatted;
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
                teleportIcon.setToolTipText("Teleport item available");
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
                + "Stock: " + item.quantity + "<br>"
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
