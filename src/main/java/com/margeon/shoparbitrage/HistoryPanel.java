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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HistoryPanel extends PluginPanel
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HistoryPanel.class);
    private static final String UNKNOWN_ITEM_NAME = "Unknown Item";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private final ItemManager itemManager;
    private final FlippingSessionManager sessionManager;
    private final JPanel listContainer = new JPanel();

    public HistoryPanel(ItemManager itemManager, FlippingSessionManager sessionManager)
    {
        super(false);
        this.itemManager = itemManager;
        this.sessionManager = sessionManager;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Trade History");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        header.add(title, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        // List
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        // Listen for updates
        sessionManager.addListener(() -> SwingUtilities.invokeLater(this::rebuildList));

        // Initial build
        rebuildList();
    }

    private void rebuildList()
    {
        listContainer.removeAll();
        List<FlippingSessionManager.FlipTransaction> history = sessionManager.getHistory();

        if (history.isEmpty())
        {
            JLabel empty = new JLabel("No completed trades yet.");
            empty.setForeground(Color.GRAY);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(new EmptyBorder(20, 0, 0, 0));
            listContainer.add(empty);
        }
        else
        {
            for (FlippingSessionManager.FlipTransaction tx : history)
            {
                try
                {
                    listContainer.add(createRow(tx));
                    listContainer.add(Box.createRigidArea(new Dimension(0, 5)));
                }
                catch (Exception e)
                {
                    log.error("Failed to create history row for item {}", tx.itemId, e);
                }
            }
        }

        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel createRow(FlippingSessionManager.FlipTransaction tx)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(5, 5, 5, 5));
        row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 50));

        // Icon
        JLabel iconLabel = new JLabel();
        try
        {
            AsyncBufferedImage img = itemManager.getImage(tx.itemId);
            if (img != null)
            {
                img.addTo(iconLabel);
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load image for item ID {}: {}", tx.itemId, e.getMessage());
        }

        JPanel iconWrapper = new JPanel(new BorderLayout());
        iconWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        iconWrapper.setPreferredSize(new Dimension(40, 32));
        iconWrapper.add(iconLabel, BorderLayout.CENTER);

        // Name & Time
        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoPanel.setBorder(new EmptyBorder(0, 8, 0, 0));

        String itemName = getItemName(tx.itemId);

        JLabel nameLabel = new JLabel(itemName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());

        // FIXED: Proper bullet character encoding
        String timeText = TIME_FORMAT.format(new Date(tx.timestamp)) + " • Qty: " + tx.quantity;
        JLabel timeLabel = new JLabel(timeText);
        timeLabel.setForeground(Color.GRAY);
        timeLabel.setFont(FontManager.getRunescapeSmallFont());

        infoPanel.add(nameLabel);
        infoPanel.add(timeLabel);

        // Profit Value
        JLabel profitLabel = new JLabel(QuantityFormatter.quantityToStackSize(tx.profit));
        profitLabel.setForeground(tx.profit >= 0 ? Color.GREEN : Color.RED);
        profitLabel.setFont(FontManager.getRunescapeSmallFont());
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        profitLabel.setBorder(new EmptyBorder(0, 0, 0, 5));

        row.add(iconWrapper, BorderLayout.WEST);
        row.add(infoPanel, BorderLayout.CENTER);
        row.add(profitLabel, BorderLayout.EAST);

        row.setToolTipText("Profit: " + QuantityFormatter.formatNumber(tx.profit) + " gp");

        return row;
    }

    // IMPROVED: Proper error handling instead of empty catch block
    private String getItemName(int itemId)
    {
        try
        {
            String name = itemManager.getItemComposition(itemId).getName();
            return name != null ? name : UNKNOWN_ITEM_NAME;
        }
        catch (Exception e)
        {
            log.warn("Failed to get item name for ID {}: {}", itemId, e.getMessage());
            return UNKNOWN_ITEM_NAME;
        }
    }
}