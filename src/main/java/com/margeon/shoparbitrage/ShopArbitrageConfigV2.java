package com.margeon.shoparbitrage;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("shoparbitrage")
public interface ShopArbitrageConfigV2 extends ShopArbitrageConfig
{
    // ====================
    // SECTIONS
    // ====================

    @ConfigSection(
            name = "Display Settings",
            description = "General display and filtering options",
            position = 0
    )
    String displaySection = "displaySection";

    @ConfigSection(
            name = "Prediction Settings",
            description = "Configure the flip prediction algorithm",
            position = 1
    )
    String predictionSection = "predictionSection";

    @ConfigSection(
            name = "Profit Calculation",
            description = "Fine-tune profit calculations",
            position = 2
    )
    String profitSection = "profitSection";

    @ConfigSection(
            name = "Risk Management",
            description = "Set risk tolerance and warnings",
            position = 3
    )
    String riskSection = "riskSection";

    @ConfigSection(
            name = "Color Coding",
            description = "Customize profit color thresholds",
            position = 4
    )
    String colorSection = "colorSection";

    // ====================
    // DISPLAY SETTINGS
    // ====================

    // minDailyVolume() and resultLimit() inherited from ShopArbitrageConfig

    @ConfigItem(
            keyName = "showWarnings",
            name = "Show Warnings",
            description = "Display warning icons for risky flips.",
            position = 3,
            section = displaySection
    )
    default boolean showWarnings()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoRefresh",
            name = "Auto-Refresh Interval (min)",
            description = "Automatically refresh prices every X minutes. 0 = disabled.",
            position = 4,
            section = displaySection
    )
    @Range(min = 0, max = 60)
    default int autoRefreshMinutes()
    {
        return 5;
    }

    // ====================
    // PREDICTION SETTINGS
    // ====================

    @ConfigItem(
            keyName = "minScore",
            name = "Minimum Prediction Score",
            description = "Only show items with a prediction score above this threshold.<br>" +
                    "Score combines margin, volume, stability, trend, and risk factors.",
            position = 1,
            section = predictionSection
    )
    @Range(min = 0, max = 100)
    default int minPredictionScore()
    {
        return 40;
    }

    @ConfigItem(
            keyName = "minConfidence",
            name = "Minimum Confidence",
            description = "Filter predictions by confidence level.<br>" +
                    "Higher confidence = more data points and stable metrics.",
            position = 2,
            section = predictionSection
    )
    default ConfidenceLevel minConfidence()
    {
        return ConfidenceLevel.LOW;
    }

    @ConfigItem(
            keyName = "weightMargin",
            name = "Weight: Margin",
            description = "How much margin (profit per item) affects the score. Default: 25",
            position = 3,
            section = predictionSection
    )
    @Range(min = 0, max = 50)
    default int weightMargin()
    {
        return 25;
    }

    @ConfigItem(
            keyName = "weightVolume",
            name = "Weight: Volume",
            description = "How much trading volume affects the score. Default: 20",
            position = 4,
            section = predictionSection
    )
    @Range(min = 0, max = 50)
    default int weightVolume()
    {
        return 20;
    }

    @ConfigItem(
            keyName = "weightStability",
            name = "Weight: Stability",
            description = "How much margin stability affects the score. Default: 15",
            position = 5,
            section = predictionSection
    )
    @Range(min = 0, max = 50)
    default int weightStability()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "weightTrend",
            name = "Weight: Trend",
            description = "How much price trend affects the score. Default: 15",
            position = 6,
            section = predictionSection
    )
    @Range(min = 0, max = 50)
    default int weightTrend()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "weightVolatility",
            name = "Weight: Volatility",
            description = "How much price volatility affects the score. Default: 10",
            position = 7,
            section = predictionSection
    )
    @Range(min = 0, max = 50)
    default int weightVolatility()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "weightRSI",
            name = "Weight: RSI",
            description = "How much RSI (overbought/oversold) affects the score. Default: 10",
            position = 8,
            section = predictionSection
    )
    @Range(min = 0, max = 50)
    default int weightRSI()
    {
        return 10;
    }

    // ====================
    // PROFIT CALCULATION
    // ====================

    // hopsPerHour(), maxBuyPerHop(), runningSpeed(), bankTime(), shopTime()
    // inherited from ShopArbitrageConfig

    @ConfigItem(
            keyName = "flipTimeMultiplier",
            name = "Flip Time Multiplier",
            description = "Multiply estimated flip time by this factor for safety margin.<br>" +
                    "1.0 = optimistic, 1.5 = realistic, 2.0 = conservative",
            position = 6,
            section = profitSection
    )
    @Range(min = 1, max = 3)
    default double flipTimeMultiplier()
    {
        return 1.5;
    }

    // ====================
    // RISK MANAGEMENT
    // ====================

    @ConfigItem(
            keyName = "maxVolatility",
            name = "Max Volatility (%)",
            description = "Warn/filter items with price volatility above this percentage.",
            position = 1,
            section = riskSection
    )
    @Range(min = 1, max = 50)
    default int maxVolatility()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "minMarginStability",
            name = "Min Margin Stability (%)",
            description = "Warn/filter items with margin stability below this percentage.<br>" +
                    "100 = very stable, 0 = highly variable",
            position = 2,
            section = riskSection
    )
    @Range(min = 0, max = 100)
    default int minMarginStability()
    {
        return 50;
    }

    @ConfigItem(
            keyName = "avoidDowntrend",
            name = "Avoid Downtrends",
            description = "Warn when an item's price is trending downward.",
            position = 3,
            section = riskSection
    )
    default boolean avoidDowntrends()
    {
        return true;
    }

    @ConfigItem(
            keyName = "avoidOverbought",
            name = "Avoid Overbought (RSI > 70)",
            description = "Warn when RSI indicates item may be overbought.",
            position = 4,
            section = riskSection
    )
    default boolean avoidOverbought()
    {
        return true;
    }

    @ConfigItem(
            keyName = "warnLowVolume",
            name = "Low Volume Warning Threshold",
            description = "Warn when daily volume is below this number.",
            position = 5,
            section = riskSection
    )
    @Range(min = 100, max = 10000)
    default int lowVolumeWarning()
    {
        return 500;
    }

    @ConfigItem(
            keyName = "maxFlipTime",
            name = "Max Flip Time Warning (hours)",
            description = "Warn when estimated flip time exceeds this many hours.",
            position = 6,
            section = riskSection
    )
    @Range(min = 1, max = 24)
    default int maxFlipTimeWarning()
    {
        return 4;
    }

    // ====================
    // COLOR CODING
    // ====================

    // highProfitThreshold() and mediumProfitThreshold() inherited from ShopArbitrageConfig

    @ConfigItem(
            keyName = "highScoreThreshold",
            name = "High Score Threshold",
            description = "Prediction scores above this are highlighted as excellent.",
            position = 3,
            section = colorSection
    )
    @Range(min = 50, max = 100)
    default int highScoreThreshold()
    {
        return 75;
    }

    @ConfigItem(
            keyName = "goodScoreThreshold",
            name = "Good Score Threshold",
            description = "Prediction scores above this are highlighted as good.",
            position = 4,
            section = colorSection
    )
    @Range(min = 30, max = 80)
    default int goodScoreThreshold()
    {
        return 60;
    }

    // ====================
    // SHOP ARBITRAGE SETTINGS
    // ====================

    // minProfitFilter() and maxDistanceFilter() inherited from ShopArbitrageConfig

    // ====================
    // ENUMS
    // ====================

    enum ConfidenceLevel
    {
        VERY_LOW("Very Low"),
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High"),
        VERY_HIGH("Very High");

        private final String displayName;

        ConfidenceLevel(String displayName)
        {
            this.displayName = displayName;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }
}