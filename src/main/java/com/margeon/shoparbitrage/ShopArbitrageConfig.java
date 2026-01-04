package com.margeon.shoparbitrage;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("shoparbitrage")
public interface ShopArbitrageConfig extends Config
{
    // ====================
    // DISPLAY SETTINGS
    // ====================

    @ConfigItem(
            keyName = "minVolume",
            name = "Minimum Daily Volume",
            description = "Items must be traded at least this many times per day to be shown.",
            position = 1
    )
    default int minDailyVolume()
    {
        return 100;
    }

    @ConfigItem(
            keyName = "resultLimit",
            name = "Result Limit",
            description = "Maximum number of profitable items to display in the list.",
            position = 2
    )
    @Range(min = 10, max = 200)
    default int resultLimit()
    {
        return 50;
    }

    @ConfigItem(
            keyName = "minProfit",
            name = "Minimum Profit (gp/hr)",
            description = "Only show shops with at least this much profit per hour.",
            position = 3
    )
    @Range(min = 0, max = 1000000)
    default int minProfitFilter()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "maxDistance",
            name = "Maximum Distance (tiles)",
            description = "Only show shops within this distance from a bank. 0 = no limit.",
            position = 4
    )
    @Range(min = 0, max = 500)
    default int maxDistanceFilter()
    {
        return 0; // 0 means no limit
    }

    // ====================
    // COLOR CODING THRESHOLDS
    // ====================

    @ConfigItem(
            keyName = "highProfitThreshold",
            name = "High Profit Threshold",
            description = "Shops with profit above this are colored green.",
            position = 20
    )
    @Range(min = 50000, max = 1000000)
    default int highProfitThreshold()
    {
        return 200000; // 200k gp/hr
    }

    @ConfigItem(
            keyName = "mediumProfitThreshold",
            name = "Medium Profit Threshold",
            description = "Shops with profit above this are colored orange.",
            position = 21
    )
    @Range(min = 10000, max = 500000)
    default int mediumProfitThreshold()
    {
        return 50000; // 50k gp/hr
    }

    // ====================
    // PROFIT CALCULATION SETTINGS
    // ====================

    @ConfigItem(
            keyName = "hopsPerHour",
            name = "World Hops Per Hour",
            description = "How many world hops you can do per hour (affects stackable items like runes).<br>" +
                    "Casual: ~60, Average: ~75, Efficient: ~90",
            position = 10
    )
    @Range(min = 30, max = 120)
    default int hopsPerHour()
    {
        return 75;
    }

    @ConfigItem(
            keyName = "maxBuyPerHop",
            name = "Max Buy Per Hop",
            description = "Maximum items to buy per hop before shop prices increase.<br>" +
                    "Most shops increase price after 50-300 purchases.",
            position = 11
    )
    @Range(min = 50, max = 1000)
    default int maxBuyPerHop()
    {
        return 300;
    }

    @ConfigItem(
            keyName = "runningSpeed",
            name = "Running Speed (tiles/sec)",
            description = "Your running speed including obstacles and delays.<br>" +
                    "Pure running: ~4, With obstacles: ~2.5",
            position = 12
    )
    @Range(min = 1, max = 5)
    default double runningSpeed()
    {
        return 2.5;
    }

    @ConfigItem(
            keyName = "bankTime",
            name = "Bank Interaction Time (sec)",
            description = "Seconds spent banking (open, deposit, close).",
            position = 13
    )
    @Range(min = 3, max = 20)
    default int bankTime()
    {
        return 8;
    }

    @ConfigItem(
            keyName = "shopTime",
            name = "Shop Interaction Time (sec)",
            description = "Seconds spent at shop (find NPC, right-click, buy-all).",
            position = 14
    )
    @Range(min = 3, max = 20)
    default int shopTime()
    {
        return 6;
    }
}