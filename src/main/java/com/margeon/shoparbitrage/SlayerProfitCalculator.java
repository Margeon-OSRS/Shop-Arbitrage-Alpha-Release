package com.margeon.shoparbitrage;

/**
 * Calculates profit per hour for slayer monsters based on their drop tables
 */
public class SlayerProfitCalculator
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SlayerProfitCalculator.class);

    private final WikiPriceService wikiPriceService;

    public SlayerProfitCalculator(WikiPriceService wikiPriceService)
    {
        this.wikiPriceService = wikiPriceService;
    }

    /**
     * Calculate profit per hour for a slayer monster
     *
     * Formula:
     * - For each drop: (average qty) * (drop rate) * (GE price or alch value)
     * - Sum all drops = profit per kill
     * - Multiply by kills/hour = profit per hour
     */
    public void calculateProfit(SlayerMonster monster)
    {
        long profitPerKill = 0;

        for (SlayerMonster.Drop drop : monster.getDropTable())
        {
            // Get value per item
            long itemValue = getItemValue(drop);

            // Calculate expected value of this drop
            double averageQty = drop.getAverageQuantity();
            double dropRate = drop.getDropRate();
            long dropExpectedValue = (long) (averageQty * dropRate * itemValue);

            profitPerKill += dropExpectedValue;

            log.debug("{} - Drop: {} ({}x at {}) = {} gp expected per kill",
                    monster.getName(), drop.getItemName(), averageQty, dropRate, dropExpectedValue);
        }

        // Calculate hourly
        long profitPerHour = profitPerKill * monster.getKillsPerHour();

        monster.setProfitPerKill(profitPerKill);
        monster.setProfitPerHour(profitPerHour);

        log.info("{}: {} gp/kill, {} gp/hr ({} kills/hr)",
                monster.getName(), profitPerKill, profitPerHour, monster.getKillsPerHour());
    }

    /**
     * Get the value of an item (GE price or alch value)
     */
    private long getItemValue(SlayerMonster.Drop drop)
    {
        // If alchable, use alch value as minimum
        if (drop.isAlchable() && drop.getAlchValue() > 0)
        {
            // Check GE price
            WikiPriceService.WikiPrice gePrice = wikiPriceService.getPrice(drop.getItemId());
            if (gePrice != null && gePrice.high > 0)
            {
                // Use whichever is higher (GE or alch)
                return Math.max(gePrice.high, drop.getAlchValue());
            }
            return drop.getAlchValue();
        }

        // Non-alchable, use GE price
        WikiPriceService.WikiPrice gePrice = wikiPriceService.getPrice(drop.getItemId());
        if (gePrice != null && gePrice.high > 0)
        {
            return gePrice.high;
        }

        // No price data, use alch value if available
        if (drop.getAlchValue() > 0)
        {
            return drop.getAlchValue();
        }

        // No value data available
        log.warn("No price data for item: {} ({})", drop.getItemName(), drop.getItemId());
        return 0;
    }

    /**
     * Calculate profit for all monsters
     */
    public void calculateAllProfits(java.util.List<SlayerMonster> monsters)
    {
        for (SlayerMonster monster : monsters)
        {
            calculateProfit(monster);
        }
    }
}