package com.margeon.shoparbitrage;

import net.runelite.client.game.ItemManager;

/**
 * Calculates realistic hourly profit for shop arbitrage methods.
 * Uses empirically-tested constants for world hopping and running methods.
 */
public class ProfitCalculator
{
    // ====================
    // WORLD HOPPING METHOD (for stackable items like runes/arrows)
    // ====================

    /**
     * Realistic hops per hour accounting for:
     * - Loading screens
     * - Click delays
     * - F2P world restrictions
     * - Human reaction time
     *
     * Casual players: ~60/hr
     * Average players: ~75/hr (default)
     * Efficient players: ~90/hr
     */
    public static final int DEFAULT_HOPS_PER_HOUR = 75;

    /**
     * Maximum items to buy per hop before shop prices rise.
     * Most shops increase price after 50-300 purchases.
     * This prevents unrealistic profit calculations on high-stock items.
     */
    public static final int DEFAULT_MAX_BUY_PER_HOP = 300;

    // ====================
    // RUNNING METHOD (for non-stackable items like platebodies/ore)
    // ====================

    /**
     * Running speed in tiles per second.
     * Accounts for:
     * - Stamina potion effects
     * - Obstacles (doors, gates)
     * - Path inefficiencies
     *
     * Pure running: ~4 tiles/sec
     * With obstacles: ~2.5 tiles/sec (default)
     */
    public static final double DEFAULT_RUNNING_TILES_PER_SECOND = 2.5;

    /**
     * Time spent banking (opening, depositing, closing).
     * Includes:
     * - Walking to banker
     * - Interface delays
     * - Inventory management
     */
    public static final int DEFAULT_BANK_INTERACTION_SECONDS = 8;

    /**
     * Time spent at shop (opening, buying, closing).
     * Includes:
     * - Finding the NPC
     * - Right-click delays
     * - Buy-all clicking
     */
    public static final int DEFAULT_SHOP_INTERACTION_SECONDS = 6;

    /**
     * Standard inventory size in OSRS.
     */
    public static final int INVENTORY_SLOTS = 27;

    /**
     * Safety multiplier to account for:
     * - Stamina potion drinking
     * - World lag
     * - Misclicks
     * - Loading times
     */
    public static final double TRIP_TIME_SAFETY_MULTIPLIER = 1.2;

    // ====================
    // CONFIGURABLE FIELDS (can be set externally)
    // ====================

    private static int hopsPerHour = DEFAULT_HOPS_PER_HOUR;
    private static int maxBuyPerHop = DEFAULT_MAX_BUY_PER_HOP;
    private static double runningTilesPerSecond = DEFAULT_RUNNING_TILES_PER_SECOND;
    private static int bankInteractionSeconds = DEFAULT_BANK_INTERACTION_SECONDS;
    private static int shopInteractionSeconds = DEFAULT_SHOP_INTERACTION_SECONDS;

    /**
     * Allows runtime configuration of calculation parameters.
     * Useful for player skill levels or testing different scenarios.
     */
    public static void configure(
            int hopsPerHour,
            int maxBuyPerHop,
            double runningSpeed,
            int bankTime,
            int shopTime)
    {
        ProfitCalculator.hopsPerHour = hopsPerHour;
        ProfitCalculator.maxBuyPerHop = maxBuyPerHop;
        ProfitCalculator.runningTilesPerSecond = runningSpeed;
        ProfitCalculator.bankInteractionSeconds = bankTime;
        ProfitCalculator.shopInteractionSeconds = shopTime;
    }

    /**
     * Resets all parameters to default values.
     */
    public static void resetToDefaults()
    {
        hopsPerHour = DEFAULT_HOPS_PER_HOUR;
        maxBuyPerHop = DEFAULT_MAX_BUY_PER_HOP;
        runningTilesPerSecond = DEFAULT_RUNNING_TILES_PER_SECOND;
        bankInteractionSeconds = DEFAULT_BANK_INTERACTION_SECONDS;
        shopInteractionSeconds = DEFAULT_SHOP_INTERACTION_SECONDS;
    }

    /**
     * Calculates realistic hourly profit for a shop item.
     *
     * @param itemManager RuneLite's item manager
     * @param itemId Item ID to check stackability
     * @param margin Net profit per item (after GE tax)
     * @param shopStock How many items the shop has
     * @param distance Tiles from shop to bank
     * @return Estimated GP per hour
     */
    public static long calculateHourlyProfit(
            ItemManager itemManager,
            int itemId,
            int margin,
            int shopStock,
            int distance)
    {
        // Negative margin = no profit
        if (margin <= 0)
        {
            return 0;
        }

        boolean isStackable = itemManager.getItemComposition(itemId).isStackable();

        if (isStackable)
        {
            return calculateHoppingProfit(margin, shopStock);
        }
        else
        {
            return calculateRunningProfit(margin, distance);
        }
    }

    /**
     * Calculates profit using world hopping method (stackable items).
     *
     * Formula: Margin × Min(ShopStock, MaxBuyPerHop) × HopsPerHour
     */
    private static long calculateHoppingProfit(int margin, int shopStock)
    {
        // CRITICAL FIX: Use realistic quantity, not full shop stock
        // Even if shop has 50,000 runes, we only buy ~300 before price rises
        int realisticQuantity = Math.min(shopStock, maxBuyPerHop);

        return (long) margin * realisticQuantity * hopsPerHour;
    }

    /**
     * Calculates profit using running method (non-stackable items).
     *
     * Formula: Margin × InventorySize × TripsPerHour
     */
    private static long calculateRunningProfit(int margin, int distance)
    {
        // Time = (Run There + Run Back) / Speed
        double travelTimeSeconds = (distance * 2.0) / runningTilesPerSecond;

        // Total Trip = Travel + Banking + Shopping
        double totalTripSeconds = travelTimeSeconds
                + bankInteractionSeconds
                + shopInteractionSeconds;

        // Apply safety buffer
        totalTripSeconds *= TRIP_TIME_SAFETY_MULTIPLIER;

        // How many trips fit in an hour?
        int tripsPerHour = (int) (3600.0 / totalTripSeconds);

        return (long) margin * INVENTORY_SLOTS * tripsPerHour;
    }
}