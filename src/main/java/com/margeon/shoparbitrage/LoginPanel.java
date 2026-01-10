package com.margeon.shoparbitrage;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Login panel for alpha/beta access control.
 * Requires a shared access key to unlock the plugin.
 */
public class LoginPanel extends PluginPanel
{
    // =====================================================
    // CHANGE THIS PASSWORD FOR YOUR ALPHA/BETA DISTRIBUTION
    // =====================================================
    private static final String ACCESS_KEY = "#ctkoo3ckt004";

    private final JPasswordField passwordField;
    private final JLabel errorLabel;
    private final JButton loginButton;

    // Callback for successful login
    private Runnable onLoginSuccess;

    public LoginPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Main content panel (centered)
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Spacer to push content toward center
        contentPanel.add(Box.createVerticalGlue());

        // Lock icon / Logo area
        JLabel lockIcon = new JLabel("🔒");
        lockIcon.setFont(lockIcon.getFont().deriveFont(48f));
        lockIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(lockIcon);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Title
        JLabel titleLabel = new JLabel("Shop Arbitrage");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(titleLabel);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Subtitle
        JLabel subtitleLabel = new JLabel("Alpha Access");
        subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
        subtitleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(subtitleLabel);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        // Access Key Label
        JLabel keyLabel = new JLabel("Enter Access Key:");
        keyLabel.setFont(FontManager.getRunescapeSmallFont());
        keyLabel.setForeground(Color.LIGHT_GRAY);
        keyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(keyLabel);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // Password field
        passwordField = new JPasswordField(15);
        passwordField.setMaximumSize(new Dimension(200, 30));
        passwordField.setPreferredSize(new Dimension(200, 30));
        passwordField.setFont(FontManager.getRunescapeSmallFont());
        passwordField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        passwordField.setForeground(Color.WHITE);
        passwordField.setCaretColor(Color.WHITE);
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passwordField.setHorizontalAlignment(JTextField.CENTER);

        // Enter key listener
        passwordField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    attemptLogin();
                }
            }
        });

        contentPanel.add(passwordField);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Login button
        loginButton = new JButton("Unlock Plugin");
        loginButton.setFont(FontManager.getRunescapeBoldFont());
        loginButton.setBackground(new Color(40, 100, 60));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setBorder(new EmptyBorder(10, 30, 10, 30));
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginButton.addActionListener(e -> attemptLogin());

        // Hover effect
        loginButton.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e)
            {
                loginButton.setBackground(new Color(50, 120, 75));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e)
            {
                loginButton.setBackground(new Color(40, 100, 60));
            }
        });

        contentPanel.add(loginButton);

        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Error label (hidden by default)
        errorLabel = new JLabel(" ");
        errorLabel.setFont(FontManager.getRunescapeSmallFont());
        errorLabel.setForeground(Color.RED);
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(errorLabel);

        // Spacer to push content toward center
        contentPanel.add(Box.createVerticalGlue());

        // Footer
        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel footerLabel = new JLabel("Contact Margeon on Discord for access");
        footerLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
        footerLabel.setForeground(Color.GRAY);
        footerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        footerPanel.add(footerLabel);

        JLabel versionLabel = new JLabel("Alpha Build v0.5");
        versionLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));
        versionLabel.setForeground(new Color(80, 80, 80));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        footerPanel.add(versionLabel);

        add(contentPanel, BorderLayout.CENTER);
        add(footerPanel, BorderLayout.SOUTH);
    }

    /**
     * Set the callback to run when login succeeds
     */
    public void setOnLoginSuccess(Runnable callback)
    {
        this.onLoginSuccess = callback;
    }

    /**
     * Attempt to login with the entered password
     */
    private void attemptLogin()
    {
        String enteredKey = new String(passwordField.getPassword());

        if (enteredKey.isEmpty())
        {
            showError("Please enter an access key");
            shakePasswordField();
            return;
        }

        if (enteredKey.equals(ACCESS_KEY))
        {
            // Success!
            errorLabel.setText(" ");
            loginButton.setEnabled(false);
            loginButton.setText("Unlocking...");

            // Small delay for visual feedback
            Timer timer = new Timer(300, e -> {
                if (onLoginSuccess != null)
                {
                    onLoginSuccess.run();
                }
            });
            timer.setRepeats(false);
            timer.start();
        }
        else
        {
            // Failed
            showError("Invalid access key");
            shakePasswordField();
            passwordField.selectAll();
        }
    }

    /**
     * Display an error message
     */
    private void showError(String message)
    {
        errorLabel.setText(message);

        // Flash red border on password field
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.RED),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        // Reset border after delay
        Timer timer = new Timer(2000, e -> {
            passwordField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
            ));
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Shake animation for wrong password
     */
    private void shakePasswordField()
    {
        final int originalX = passwordField.getX();
        final int shakeDistance = 5;
        final int shakeDuration = 50;

        Timer shakeTimer = new Timer(shakeDuration, null);
        final int[] shakeCount = {0};

        shakeTimer.addActionListener(e -> {
            if (shakeCount[0] >= 6)
            {
                passwordField.setLocation(originalX, passwordField.getY());
                shakeTimer.stop();
                return;
            }

            int offset = (shakeCount[0] % 2 == 0) ? shakeDistance : -shakeDistance;
            passwordField.setLocation(originalX + offset, passwordField.getY());
            shakeCount[0]++;
        });

        shakeTimer.start();
    }

    /**
     * Focus the password field when panel is shown
     */
    @Override
    public void addNotify()
    {
        super.addNotify();
        SwingUtilities.invokeLater(() -> passwordField.requestFocusInWindow());
    }
}