package com.margeon.shoparbitrage;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AboutPanel extends JPanel
{
    // Version constant for easy updates
    private static final String VERSION = "Alpha Build v0.5";
    private static final String AUTHOR = "Margeon";

    public AboutPanel()
    {
        // Set the layout of the main panel to BorderLayout to hold the scroll pane
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // 1. Create a specific panel to hold the actual content
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridBagLayout());
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 8, 0); // Reduced spacing

        // Title
        JLabel title = new JLabel("Shop Arbitrage, Flipping, And Slayer");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        contentPanel.add(title, c); // Changed to contentPanel.add
        c.gridy++;

        // Version
        JLabel version = new JLabel(VERSION);
        version.setFont(FontManager.getRunescapeSmallFont());
        version.setHorizontalAlignment(SwingConstants.CENTER);
        version.setForeground(Color.GRAY);
        contentPanel.add(version, c);
        c.gridy++;

        contentPanel.add(createSectionHeader("Shop Features"), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Shop Arbitrage",
                "Find profitable NPC shop items to flip on GE with real-time profit calculations."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Color-Coded Profits",
                "Green (200k+), orange (50k-200k), yellow profits. Fully configurable."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Advanced Filtering",
                "Filter by profit, distance, stackable. Sort by profit, distance, or name."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Route Planner",
                "Multi-shop optimizer with distance, time, and teleport requirements."), c);
        c.gridy++;

        contentPanel.add(createSectionHeader("Flipping Features"), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Live Market Scanner",
                "Scans OSRS Wiki + GE API. Filters by volume and customizable limits."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Watchlist",
                "Track favorite items with live margins. Click-to-add from scanner."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Trade History",
                "Auto-tracks GE profits, last 100 trades. Access via 'View History' button."), c);
        c.gridy++;

        contentPanel.add(createSectionHeader("Slayer Features"), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Slayer Profit Calculator",
                "8 monsters with real drop tables. Shows gp/hr based on GE prices and kills/hr."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Drop Table Analysis",
                "Factors in drop rates, GE prices, alch values. Filter by slayer tasks."), c);
        c.gridy++;

        contentPanel.add(createSectionHeader("Smart Systems"), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Auto GE Tax",
                "1% tax (max 5M) factored into all calculations with buy limit awareness."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Realistic Calculations",
                "Uses 75 hops/hr, 2.5 tiles/sec for accurate gp/hr estimates."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Instant Re-filtering",
                "Results cached. Change filters instantly without re-fetching API data."), c);
        c.gridy++;

        contentPanel.add(createFeatureItem("Thread-Safe Design",
                "Client thread for ItemManager, Swing for UI, async API calls. No crashes."), c);
        c.gridy++;

        contentPanel.add(createSectionHeader("Credits & Support"), c);
        c.gridy++;

        JLabel author = new JLabel("Created by: " + AUTHOR);
        author.setForeground(Color.GRAY);
        author.setHorizontalAlignment(SwingConstants.CENTER);
        author.setFont(FontManager.getRunescapeSmallFont());
        contentPanel.add(author, c);
        c.gridy++;

        JLabel feedback = new JLabel("Contact Margeon on Discord!");
        feedback.setForeground(new Color(150, 150, 150));
        feedback.setHorizontalAlignment(SwingConstants.CENTER);
        feedback.setFont(FontManager.getRunescapeSmallFont());
        contentPanel.add(feedback, c);
        c.gridy++;

        JLabel github = new JLabel("Report bugs or suggest features WIP");
        github.setForeground(new Color(120, 120, 120));
        github.setHorizontalAlignment(SwingConstants.CENTER);
        github.setFont(FontManager.getRunescapeSmallFont());
        contentPanel.add(github, c);
        c.gridy++;

        // Push to top logic
        c.weighty = 1;
        contentPanel.add(new JPanel(), c);

        // 2. Wrap the contentPanel in a JScrollPane
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null); // Removes the border for a cleaner look
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Makes scrolling faster/smoother

        // 3. Add the scrollPane to the main AboutPanel
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createSectionHeader(String text)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 0, 5, 0));

        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(ColorScheme.BRAND_ORANGE);

        panel.add(label, BorderLayout.CENTER);

        JSeparator sep = new JSeparator();
        sep.setBackground(Color.GRAY);
        panel.add(sep, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createFeatureItem(String title, String desc)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // FIXED: Proper bullet character encoding
        JLabel titleLabel = new JLabel("• " + title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont());

        JTextArea descArea = new JTextArea(desc);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setForeground(Color.GRAY);
        descArea.setFont(FontManager.getRunescapeSmallFont());
        descArea.setBorder(new EmptyBorder(2, 10, 0, 0));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(descArea, BorderLayout.CENTER);

        return panel;
    }
}