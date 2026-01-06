package com.margeon.shoparbitrage;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Advanced flip scoring system that combines multiple factors
 * to predict flip profitability and success probability.
 *
 * Factors considered:
 * 1. Margin (raw profit potential)
 * 2. Volume (liquidity/speed of sale)
 * 3. Margin stability (consistency over time)
 * 4. Price trend (avoid buying into crashes)
 * 5. Volatility (risk assessment)
 * 6. RSI (overbought/oversold conditions)
 * 7. Buy limit efficiency (profit per GE slot)
 * 8. Time to flip (based on volume vs buy limit)
 */
@Slf4j
@Singleton
public class FlipScorer
{
    // GE Tax constants
    private static final double GE_TAX_RATE = 0.01;
    private static final long MAX_GE_TAX = 5_000_000;

    // Scoring weights (sum to 100)
    private static final double WEIGHT_MARGIN = 25.0;
    private static final double WEIGHT_VOLUME = 20.0;
    private static final double WEIGHT_STABILITY = 15.0;
    private static final double WEIGHT_TREND = 15.0;
    private static final double WEIGHT_VOLATILITY = 10.0;
    private static final double WEIGHT_RSI = 10.0;
    private static final double WEIGHT_ROI = 5.0;

    private final WikiPriceService wikiPriceService;
    private final PriceHistoryService priceHistoryService;

    // Buy limits cache (item ID -> buy limit per 4 hours)
    private final Map<Integer, Integer> buyLimits = new HashMap<>();

    @Inject
    public FlipScorer(WikiPriceService wikiPriceService, PriceHistoryService priceHistoryService)
    {
        this.wikiPriceService = wikiPriceService;
        this.priceHistoryService = priceHistoryService;
        initializeBuyLimits();
    }

    /**
     * Calculate a comprehensive flip score for an item
     *
     * @param itemId The item to score
     * @return FlipScore with overall score and component breakdowns
     */
    public FlipScore calculateScore(int itemId)
    {
        WikiPriceService.WikiPrice currentPrice = wikiPriceService.getPrice(itemId);
        PriceHistoryService.ItemMetrics metrics = priceHistoryService.getMetrics(itemId);

        FlipScore score = new FlipScore();
        score.itemId = itemId;

        if (currentPrice == null || currentPrice.high <= 0 || currentPrice.low <= 0)
        {
            score.confidence = Confidence.VERY_LOW;
            score.recommendation = Recommendation.AVOID;
            score.reason = "Insufficient price data";
            return score;
        }

        // Calculate base metrics
        int rawMargin = currentPrice.high - currentPrice.low;
        int geTax = calculateGETax(currentPrice.high);
        int netMargin = rawMargin - geTax;

        score.buyPrice = currentPrice.low;
        score.sellPrice = currentPrice.high;
        score.rawMargin = rawMargin;
        score.geTax = geTax;
        score.netMargin = netMargin;
        score.dailyVolume = currentPrice.getDailyVolume();

        // Calculate ROI
        if (currentPrice.low > 0)
        {
            score.roi = (double) netMargin / currentPrice.low * 100;
        }

        // Get buy limit
        score.buyLimit = buyLimits.getOrDefault(itemId, 0);

        // Calculate time to flip (hours to sell buy limit quantity)
        if (score.dailyVolume > 0 && score.buyLimit > 0)
        {
            // Volume is per 24 hours, so per hour volume = dailyVolume / 24
            double hourlyVolume = score.dailyVolume / 24.0;
            score.estimatedFlipTimeHours = score.buyLimit / hourlyVolume;
        }

        // Calculate potential profit per 4-hour cycle
        if (score.buyLimit > 0)
        {
            score.profitPerCycle = (long) netMargin * score.buyLimit;
            // Estimate hourly profit based on flip time
            if (score.estimatedFlipTimeHours > 0)
            {
                score.estimatedHourlyProfit = (long) (score.profitPerCycle / Math.max(score.estimatedFlipTimeHours, 0.5));
            }
        }

        // === COMPONENT SCORES ===

        // 1. Margin Score (0-100)
        // Higher margins = better, but normalize based on item price
        double marginPercent = currentPrice.low > 0 ? (double) netMargin / currentPrice.low * 100 : 0;
        score.marginScore = Math.min(100, marginPercent * 10); // 10% margin = 100 score

        // 2. Volume Score (0-100)
        // Higher volume = faster sales, more reliable prices
        // 10k+ daily volume = excellent
        score.volumeScore = Math.min(100, (score.dailyVolume / 100.0));

        // 3. Stability Score (0-100)
        // Lower margin variability = more predictable profits
        if (metrics != null)
        {
            score.stabilityScore = Math.max(0, 100 - metrics.marginStability);
        }
        else
        {
            score.stabilityScore = 50; // Unknown = neutral
        }

        // 4. Trend Score (0-100)
        // Uptrend = good for buyers, sideways = stable, downtrend = risky
        if (metrics != null)
        {
            // Convert trend strength to 0-100 scale
            // -5% trend = 0, +5% trend = 100, 0 = 50
            score.trendScore = Math.max(0, Math.min(100, 50 + (metrics.trendStrength * 10)));
        }
        else
        {
            score.trendScore = 50;
        }

        // 5. Volatility Score (0-100)
        // Lower volatility = safer, more predictable
        if (metrics != null)
        {
            score.volatilityScore = Math.max(0, 100 - (metrics.volatility * 10));
        }
        else
        {
            score.volatilityScore = 50;
        }

        // 6. RSI Score (0-100)
        // RSI near 50 = neutral, <30 = oversold (buy signal), >70 = overbought (risky)
        if (metrics != null)
        {
            // Best RSI for flipping is 30-50 (slight oversold to neutral)
            if (metrics.rsi < 30)
            {
                score.rsiScore = 90; // Oversold = good buying opportunity
            }
            else if (metrics.rsi < 50)
            {
                score.rsiScore = 80; // Slightly oversold = good
            }
            else if (metrics.rsi < 70)
            {
                score.rsiScore = 60; // Neutral to slightly overbought
            }
            else
            {
                score.rsiScore = 30; // Overbought = risky to buy
            }
            score.rsi = metrics.rsi;
        }
        else
        {
            score.rsiScore = 50;
            score.rsi = 50;
        }

        // 7. ROI Score (0-100)
        // Higher ROI = better capital efficiency
        score.roiScore = Math.min(100, score.roi * 20); // 5% ROI = 100 score

        // === CALCULATE OVERALL SCORE ===
        score.overallScore = (
                score.marginScore * WEIGHT_MARGIN +
                        score.volumeScore * WEIGHT_VOLUME +
                        score.stabilityScore * WEIGHT_STABILITY +
                        score.trendScore * WEIGHT_TREND +
                        score.volatilityScore * WEIGHT_VOLATILITY +
                        score.rsiScore * WEIGHT_RSI +
                        score.roiScore * WEIGHT_ROI
        ) / 100.0;

        // === DETERMINE CONFIDENCE ===
        score.confidence = calculateConfidence(score, metrics);

        // === GENERATE RECOMMENDATION ===
        score.recommendation = generateRecommendation(score, metrics);

        // === GENERATE REASON ===
        score.reason = generateReason(score, metrics);

        // === WARNING FLAGS ===
        score.warnings = generateWarnings(score, metrics);

        return score;
    }

    /**
     * Calculate all scores for items meeting minimum criteria
     */
    public List<FlipScore> calculateAllScores(long minVolume, long maxPrice, int limit)
    {
        Map<Integer, WikiPriceService.WikiPrice> allPrices = wikiPriceService.getAllPrices();
        List<FlipScore> scores = new ArrayList<>();

        for (Map.Entry<Integer, WikiPriceService.WikiPrice> entry : allPrices.entrySet())
        {
            WikiPriceService.WikiPrice price = entry.getValue();

            // Quick filters before full calculation
            if (price.low <= 0 || price.high <= 0) continue;
            if (price.low > maxPrice) continue;
            if (price.getDailyVolume() < minVolume) continue;

            int netMargin = price.high - price.low - calculateGETax(price.high);
            if (netMargin <= 0) continue;

            FlipScore score = calculateScore(entry.getKey());
            if (score.overallScore > 30) // Only include decent opportunities
            {
                scores.add(score);
            }
        }

        // Sort by overall score descending
        scores.sort((a, b) -> Double.compare(b.overallScore, a.overallScore));

        // Return top results
        return scores.size() > limit ? scores.subList(0, limit) : scores;
    }

    private int calculateGETax(int sellPrice)
    {
        return (int) Math.min(Math.floor(sellPrice * GE_TAX_RATE), MAX_GE_TAX);
    }

    private Confidence calculateConfidence(FlipScore score, PriceHistoryService.ItemMetrics metrics)
    {
        int confidenceFactors = 0;

        // Volume gives confidence
        if (score.dailyVolume > 5000) confidenceFactors += 2;
        else if (score.dailyVolume > 1000) confidenceFactors += 1;

        // Stability gives confidence
        if (score.stabilityScore > 70) confidenceFactors += 2;
        else if (score.stabilityScore > 50) confidenceFactors += 1;

        // Having metrics data gives confidence
        if (metrics != null)
        {
            confidenceFactors += 1;
            if (metrics.hasStableMargin()) confidenceFactors += 1;
        }

        // Low volatility gives confidence
        if (score.volatilityScore > 70) confidenceFactors += 1;

        if (confidenceFactors >= 6) return Confidence.VERY_HIGH;
        if (confidenceFactors >= 4) return Confidence.HIGH;
        if (confidenceFactors >= 2) return Confidence.MEDIUM;
        if (confidenceFactors >= 1) return Confidence.LOW;
        return Confidence.VERY_LOW;
    }

    private Recommendation generateRecommendation(FlipScore score, PriceHistoryService.ItemMetrics metrics)
    {
        // Strong buy signals
        if (score.overallScore >= 75 && score.confidence.ordinal() >= Confidence.HIGH.ordinal())
        {
            return Recommendation.STRONG_BUY;
        }

        // Good opportunity
        if (score.overallScore >= 60 && score.confidence.ordinal() >= Confidence.MEDIUM.ordinal())
        {
            return Recommendation.BUY;
        }

        // Risky but potential
        if (score.overallScore >= 45)
        {
            // Check for red flags
            if (metrics != null && metrics.isDowntrend())
            {
                return Recommendation.CAUTION;
            }
            if (score.volatilityScore < 30)
            {
                return Recommendation.CAUTION;
            }
            return Recommendation.CONSIDER;
        }

        // Not recommended
        if (score.overallScore >= 30)
        {
            return Recommendation.CAUTION;
        }

        return Recommendation.AVOID;
    }

    private String generateReason(FlipScore score, PriceHistoryService.ItemMetrics metrics)
    {
        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();

        // Analyze each component
        if (score.marginScore >= 70) positives.add("excellent margin");
        else if (score.marginScore < 30) negatives.add("low margin");

        if (score.volumeScore >= 70) positives.add("high liquidity");
        else if (score.volumeScore < 30) negatives.add("low volume");

        if (score.stabilityScore >= 70) positives.add("stable spread");
        else if (score.stabilityScore < 30) negatives.add("unstable margin");

        if (score.trendScore >= 60) positives.add("uptrend");
        else if (score.trendScore < 40) negatives.add("downtrend");

        if (score.volatilityScore >= 70) positives.add("low risk");
        else if (score.volatilityScore < 30) negatives.add("high volatility");

        if (score.rsi < 35) positives.add("oversold");
        else if (score.rsi > 65) negatives.add("overbought");

        StringBuilder reason = new StringBuilder();
        if (!positives.isEmpty())
        {
            reason.append("Good: ").append(String.join(", ", positives));
        }
        if (!negatives.isEmpty())
        {
            if (reason.length() > 0) reason.append(". ");
            reason.append("Risk: ").append(String.join(", ", negatives));
        }

        return reason.length() > 0 ? reason.toString() : "Average opportunity";
    }

    private List<String> generateWarnings(FlipScore score, PriceHistoryService.ItemMetrics metrics)
    {
        List<String> warnings = new ArrayList<>();

        if (score.dailyVolume < 500)
        {
            warnings.add("Very low volume - may take long to sell");
        }

        if (score.stabilityScore < 30)
        {
            warnings.add("Margin is highly variable");
        }

        if (metrics != null)
        {
            if (metrics.isDowntrend())
            {
                warnings.add("Price is in a downtrend");
            }
            if (metrics.volatility > 5)
            {
                warnings.add("High price volatility");
            }
            if (metrics.rsi > 75)
            {
                warnings.add("RSI indicates overbought");
            }
            if (metrics.volumeTrend < -30)
            {
                warnings.add("Trading volume declining");
            }
        }

        if (score.estimatedFlipTimeHours > 4)
        {
            warnings.add("May take >" + (int)score.estimatedFlipTimeHours + "h to flip");
        }

        return warnings;
    }

    /**
     * Initialize common buy limits
     * In practice, you'd want to fetch these from a database or API
     */
    private void initializeBuyLimits()
    {
        // Common flipping items with their 4-hour buy limits
        // This is a subset - in production, use a more complete database

        // Bonds
        buyLimits.put(13190, 5); // Old school bond

        // Runes
        buyLimits.put(554, 25000); // Fire rune
        buyLimits.put(555, 25000); // Water rune
        buyLimits.put(556, 25000); // Air rune
        buyLimits.put(557, 25000); // Earth rune
        buyLimits.put(558, 25000); // Mind rune
        buyLimits.put(560, 18000); // Death rune
        buyLimits.put(561, 18000); // Nature rune
        buyLimits.put(562, 18000); // Chaos rune
        buyLimits.put(563, 18000); // Law rune
        buyLimits.put(564, 18000); // Cosmic rune
        buyLimits.put(565, 11000); // Blood rune
        buyLimits.put(566, 11000); // Soul rune
        buyLimits.put(9075, 18000); // Astral rune
        buyLimits.put(21880, 11000); // Wrath rune

        // Herbs
        buyLimits.put(199, 13000); // Grimy guam
        buyLimits.put(201, 13000); // Grimy marrentill
        buyLimits.put(203, 13000); // Grimy tarromin
        buyLimits.put(205, 13000); // Grimy harralander
        buyLimits.put(207, 13000); // Grimy ranarr
        buyLimits.put(209, 13000); // Grimy irit
        buyLimits.put(211, 13000); // Grimy avantoe
        buyLimits.put(213, 13000); // Grimy kwuarm
        buyLimits.put(215, 13000); // Grimy cadantine
        buyLimits.put(217, 13000); // Grimy dwarf weed
        buyLimits.put(219, 13000); // Grimy torstol
        buyLimits.put(2485, 13000); // Grimy lantadyme

        // Potions
        buyLimits.put(2434, 2000); // Prayer potion(4)
        buyLimits.put(3024, 2000); // Super restore(4)
        buyLimits.put(2444, 2000); // Ranging potion(4)
        buyLimits.put(2436, 2000); // Super attack(4)
        buyLimits.put(2440, 2000); // Super strength(4)
        buyLimits.put(2442, 2000); // Super defence(4)
        buyLimits.put(12695, 2000); // Super combat potion(4)
        buyLimits.put(3040, 2000); // Magic potion(4)
        buyLimits.put(12625, 2000); // Stamina potion(4)
        buyLimits.put(23373, 2000); // Divine super combat potion(4)

        // Food
        buyLimits.put(385, 13000); // Shark
        buyLimits.put(3144, 13000); // Cooked karambwan
        buyLimits.put(13441, 10000); // Anglerfish
        buyLimits.put(6685, 10000); // Saradomin brew(4)
        buyLimits.put(21510, 10000); // Dark crab
        buyLimits.put(391, 13000); // Manta ray
        buyLimits.put(7946, 13000); // Monkfish

        // Bones
        buyLimits.put(536, 13000); // Dragon bones
        buyLimits.put(22124, 13000); // Superior dragon bones
        buyLimits.put(11943, 13000); // Lava dragon bones
        buyLimits.put(22783, 5000); // Hydra bones
        buyLimits.put(6812, 7500); // Wyvern bones

        // Scales
        buyLimits.put(12934, 25000); // Zulrah's scales

        // Ore/Bars
        buyLimits.put(440, 25000); // Iron ore
        buyLimits.put(453, 25000); // Coal
        buyLimits.put(444, 25000); // Gold ore
        buyLimits.put(447, 25000); // Mithril ore
        buyLimits.put(449, 18000); // Adamantite ore
        buyLimits.put(451, 11000); // Runite ore
        buyLimits.put(2351, 13000); // Iron bar
        buyLimits.put(2353, 13000); // Steel bar
        buyLimits.put(2357, 11000); // Gold bar
        buyLimits.put(2359, 11000); // Mithril bar
        buyLimits.put(2361, 9000); // Adamantite bar
        buyLimits.put(2363, 7000); // Runite bar

        // Logs
        buyLimits.put(1515, 25000); // Yew logs
        buyLimits.put(1513, 11000); // Magic logs
        buyLimits.put(19669, 7000); // Redwood logs

        // Seeds
        buyLimits.put(5295, 200); // Ranarr seed
        buyLimits.put(5304, 200); // Snapdragon seed
        buyLimits.put(5296, 200); // Torstol seed
        buyLimits.put(22877, 200); // Dragonfruit tree seed

        // High value equipment
        buyLimits.put(4151, 8); // Abyssal whip
        buyLimits.put(11802, 8); // Armadyl godsword
        buyLimits.put(11804, 8); // Bandos godsword
        buyLimits.put(11806, 8); // Saradomin godsword
        buyLimits.put(11808, 8); // Zamorak godsword
        buyLimits.put(11785, 8); // Armadyl crossbow
        buyLimits.put(12002, 8); // Occult necklace
        buyLimits.put(11832, 8); // Bandos chestplate
        buyLimits.put(11834, 8); // Bandos tassets
        buyLimits.put(11826, 8); // Armadyl helmet
        buyLimits.put(11828, 8); // Armadyl chestplate
        buyLimits.put(11830, 8); // Armadyl chainskirt
        buyLimits.put(12825, 8); // Spectral spirit shield
        buyLimits.put(12817, 8); // Arcane spirit shield
        buyLimits.put(12821, 8); // Elysian spirit shield
        buyLimits.put(13576, 8); // Dragon warhammer
        buyLimits.put(22322, 8); // Scythe of vitur
        buyLimits.put(22324, 8); // Ghrazi rapier
        buyLimits.put(22326, 8); // Justiciar faceguard
        buyLimits.put(22327, 8); // Justiciar chestguard
        buyLimits.put(22328, 8); // Justiciar legguards
        buyLimits.put(21006, 8); // Twisted bow
        buyLimits.put(21018, 8); // Ancestral hat
        buyLimits.put(21021, 8); // Ancestral robe top
        buyLimits.put(21024, 8); // Ancestral robe bottom
        buyLimits.put(21012, 8); // Dragon claws
        buyLimits.put(22978, 8); // Hydra's claw (untradeable, but keeping for reference)
        buyLimits.put(22981, 8); // Ferocious gloves
        buyLimits.put(24268, 8); // Basilisk jaw
        buyLimits.put(19544, 8); // Tormented bracelet
        buyLimits.put(19547, 8); // Necklace of anguish
        buyLimits.put(19553, 8); // Amulet of torture
        buyLimits.put(19550, 8); // Ring of suffering
        buyLimits.put(12924, 8); // Toxic blowpipe
        buyLimits.put(12900, 8); // Serpentine helm
        buyLimits.put(12927, 8); // Trident of the swamp
        buyLimits.put(12929, 8); // Magma helm
        buyLimits.put(12931, 8); // Tanzanite helm
        buyLimits.put(24422, 8); // Sanguinesti staff
        buyLimits.put(21003, 8); // Elder maul
        buyLimits.put(20714, 8); // Dragonfire ward
        buyLimits.put(11920, 8); // Dragon pickaxe

        // Add more as needed...
        log.info("Initialized {} buy limits", buyLimits.size());
    }

    /**
     * Get buy limit for an item (returns 0 if unknown)
     */
    public int getBuyLimit(int itemId)
    {
        return buyLimits.getOrDefault(itemId, 0);
    }

    // === Data Classes ===

    public static class FlipScore
    {
        public int itemId;
        public String itemName; // Populated externally

        // Price info
        public int buyPrice;
        public int sellPrice;
        public int rawMargin;
        public int geTax;
        public int netMargin;
        public long dailyVolume;
        public double roi; // Return on investment %

        // GE limits
        public int buyLimit;
        public double estimatedFlipTimeHours;
        public long profitPerCycle; // Profit per 4-hour cycle
        public long estimatedHourlyProfit;

        // Component scores (0-100)
        public double marginScore;
        public double volumeScore;
        public double stabilityScore;
        public double trendScore;
        public double volatilityScore;
        public double rsiScore;
        public double roiScore;
        public double rsi;

        // Overall
        public double overallScore; // 0-100
        public Confidence confidence;
        public Recommendation recommendation;
        public String reason;
        public List<String> warnings;

        @Override
        public String toString()
        {
            return String.format("%s: Score=%.1f, Margin=%d, Volume=%d, Confidence=%s, Rec=%s",
                    itemName != null ? itemName : "Item " + itemId,
                    overallScore, netMargin, dailyVolume, confidence, recommendation);
        }
    }

    public enum Confidence
    {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }

    public enum Recommendation
    {
        STRONG_BUY,  // High confidence, excellent opportunity
        BUY,         // Good opportunity
        CONSIDER,    // Decent but not amazing
        CAUTION,     // Some red flags
        AVOID        // Not recommended
    }
}