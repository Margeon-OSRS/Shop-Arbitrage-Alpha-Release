package com.margeon.shoparbitrage;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Slayer monster with drop table and profitability data
 */
public class SlayerMonster
{
    private final String name;
    private final int combatLevel;
    private final int slayerLevel;
    private final int killsPerHour;
    private final boolean isSlayerTask; // Can be assigned as task
    private final List<Drop> dropTable;

    // Calculated values
    private long profitPerHour;
    private long profitPerKill;

    public SlayerMonster(String name, int combatLevel, int slayerLevel, int killsPerHour, boolean isSlayerTask)
    {
        this.name = name;
        this.combatLevel = combatLevel;
        this.slayerLevel = slayerLevel;
        this.killsPerHour = killsPerHour;
        this.isSlayerTask = isSlayerTask;
        this.dropTable = new ArrayList<>();
    }

    /**
     * Add a drop to this monster's drop table
     */
    public void addDrop(Drop drop)
    {
        dropTable.add(drop);
    }

    /**
     * Represents a single drop from a monster
     */
    public static class Drop
    {
        private final int itemId;
        private final String itemName;
        private final int minQuantity;
        private final int maxQuantity;
        private final double dropRate; // e.g., 0.5 = 1/2, 0.01 = 1/100
        private final boolean isAlchable;
        private final int alchValue; // High alch value

        public Drop(int itemId, String itemName, int minQuantity, int maxQuantity,
                    double dropRate, boolean isAlchable, int alchValue)
        {
            this.itemId = itemId;
            this.itemName = itemName;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
            this.dropRate = dropRate;
            this.isAlchable = isAlchable;
            this.alchValue = alchValue;
        }

        // Average quantity per drop
        public double getAverageQuantity()
        {
            return (minQuantity + maxQuantity) / 2.0;
        }

        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getMinQuantity() { return minQuantity; }
        public int getMaxQuantity() { return maxQuantity; }
        public double getDropRate() { return dropRate; }
        public boolean isAlchable() { return isAlchable; }
        public int getAlchValue() { return alchValue; }
    }

    // Getters
    public String getName() { return name; }
    public int getCombatLevel() { return combatLevel; }
    public int getSlayerLevel() { return slayerLevel; }
    public int getKillsPerHour() { return killsPerHour; }
    public boolean isSlayerTask() { return isSlayerTask; }
    public List<Drop> getDropTable() { return dropTable; }
    public long getProfitPerHour() { return profitPerHour; }
    public long getProfitPerKill() { return profitPerKill; }

    public void setProfitPerHour(long profitPerHour) { this.profitPerHour = profitPerHour; }
    public void setProfitPerKill(long profitPerKill) { this.profitPerKill = profitPerKill; }
}