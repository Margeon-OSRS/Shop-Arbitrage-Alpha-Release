package com.margeon.shoparbitrage;

import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Panel that displays an optimized route through multiple shops
 */
public class RoutePanel extends JPanel
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoutePanel.class);

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final JPanel routeListContainer = new JPanel();

    // Updated constructor to accept ClientThread
    public RoutePanel(ItemManager itemManager, ClientThread clientThread)
    {
        this.itemManager = itemManager;
        this.clientThread = clientThread;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel title = new JLabel("Optimized Route");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        header.add(title, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        // Route list
        routeListContainer.setLayout(new BoxLayout(routeListContainer, BoxLayout.Y_AXIS));
        routeListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(routeListContainer);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Display a planned route
     */
    public void displayRoute(RoutePlanner.PlannedRoute route)
    {
        // This method is called on the EDT (UI Thread), so we can manipulate Swing components directly here.
        routeListContainer.removeAll();

        if (route == null || route.getStops().isEmpty())
        {
            JLabel empty = new JLabel("No route to display");
            empty.setForeground(Color.GRAY);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(new EmptyBorder(20, 0, 0, 0));
            routeListContainer.add(empty);
            routeListContainer.revalidate();
            routeListContainer.repaint();
            return;
        }

        // Summary panel
        JPanel summary = createSummaryPanel(route);
        routeListContainer.add(summary);
        routeListContainer.add(Box.createRigidArea(new Dimension(0, 10)));

        // Route stops
        List<RoutePlanner.RouteStop> stops = route.getStops();
        for (int i = 0; i < stops.size(); i++)
        {
            RoutePlanner.RouteStop stop = stops.get(i);
            routeListContainer.add(createStopPanel(i + 1, stop, i == stops.size() - 1));
            routeListContainer.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        // Teleport list
        if (!route.getTeleportsNeeded().isEmpty())
        {
            routeListContainer.add(createTeleportPanel(route.getTeleportsNeeded()));
        }

        routeListContainer.revalidate();
        routeListContainer.repaint();
    }

    private JPanel createSummaryPanel(RoutePlanner.PlannedRoute route)
    {
        JPanel panel = new JPanel(new GridLayout(3, 1, 0, 5));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 80));

        JLabel shopsLabel = new JLabel("Shops: " + route.getStops().size());
        shopsLabel.setForeground(Color.WHITE);
        shopsLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel distanceLabel = new JLabel("Total Distance: " + route.getTotalDistance() + " tiles");
        distanceLabel.setForeground(Color.LIGHT_GRAY);
        distanceLabel.setFont(FontManager.getRunescapeSmallFont());

        int estimatedMinutes = route.getEstimatedTimeMinutes();
        JLabel timeLabel = new JLabel("Est. Time: " + estimatedMinutes + " min");
        timeLabel.setForeground(Color.LIGHT_GRAY);
        timeLabel.setFont(FontManager.getRunescapeSmallFont());

        panel.add(shopsLabel);
        panel.add(distanceLabel);
        panel.add(timeLabel);

        return panel;
    }

    private JPanel createStopPanel(int number, RoutePlanner.RouteStop stop, boolean isLast)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 60));

        // Number badge
        JLabel numberLabel = new JLabel(String.valueOf(number));
        numberLabel.setForeground(Color.WHITE);
        numberLabel.setFont(FontManager.getRunescapeBoldFont());
        numberLabel.setHorizontalAlignment(SwingConstants.CENTER);
        numberLabel.setPreferredSize(new Dimension(30, 30));
        numberLabel.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 2));

        // Info panel
        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel nameLabel = new JLabel(stop.getShop().getName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());

        String distanceText = isLast
                ? "Final stop"
                : "→ " + stop.getDistanceToNext() + " tiles to next";
        JLabel distanceLabel = new JLabel(distanceText);
        distanceLabel.setForeground(Color.GRAY);
        distanceLabel.setFont(FontManager.getRunescapeSmallFont());

        infoPanel.add(nameLabel);
        infoPanel.add(distanceLabel);

        // Teleport icon (if has one)
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        if (stop.getShop().getTeleportId() > 0)
        {
            int teleportId = stop.getShop().getTeleportId();
            JLabel teleportIcon = new JLabel();

            // 1. Fetch Item Name on Client Thread (async)
            if (clientThread != null)
            {
                clientThread.invokeLater(() ->
                {
                    try
                    {
                        ItemComposition itemComp = itemManager.getItemComposition(teleportId);
                        if (itemComp != null)
                        {
                            String name = itemComp.getName();
                            // 2. Update Swing Component on Event Dispatch Thread
                            SwingUtilities.invokeLater(() -> teleportIcon.setToolTipText(name));
                        }
                    }
                    catch (Exception e)
                    {
                        log.debug("Failed to fetch item name for tooltip: {}", teleportId);
                    }
                });
            }

            // 3. Fetch Image (AsyncBufferedImage is safe on EDT)
            try
            {
                AsyncBufferedImage img = itemManager.getImage(teleportId);
                if (img != null)
                {
                    img.addTo(teleportIcon);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to load teleport icon: {}", e.getMessage());
            }
            rightPanel.add(teleportIcon, BorderLayout.CENTER);
        }

        panel.add(numberLabel, BorderLayout.WEST);
        panel.add(infoPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createTeleportPanel(java.util.Set<Integer> teleportIds)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH, 100));

        JLabel header = new JLabel("Teleports Needed:");
        header.setForeground(ColorScheme.BRAND_ORANGE);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(header);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel iconsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        iconsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        iconsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (Integer teleportId : teleportIds)
        {
            JLabel icon = new JLabel();

            // 1. Fetch Item Name on Client Thread
            if (clientThread != null)
            {
                clientThread.invokeLater(() ->
                {
                    try
                    {
                        ItemComposition itemComp = itemManager.getItemComposition(teleportId);
                        if (itemComp != null)
                        {
                            String name = itemComp.getName();
                            // 2. Update UI on EDT
                            SwingUtilities.invokeLater(() -> icon.setToolTipText(name));
                        }
                    }
                    catch (Exception e)
                    {
                        log.debug("Failed to fetch item name for tooltip: {}", teleportId);
                    }
                });
            }

            // 3. Fetch Image
            try
            {
                AsyncBufferedImage img = itemManager.getImage(teleportId);
                if (img != null)
                {
                    img.addTo(icon);
                }
            }
            catch (Exception e)
            {
                log.warn("Failed to load teleport icon: {}", e.getMessage());
            }
            iconsPanel.add(icon);
        }

        panel.add(iconsPanel);

        return panel;
    }

    public void clear()
    {
        routeListContainer.removeAll();
        routeListContainer.revalidate();
        routeListContainer.repaint();
    }
}