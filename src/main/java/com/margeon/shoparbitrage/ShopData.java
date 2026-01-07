package com.margeon.shoparbitrage;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import java.util.List;

@Getter
public class ShopData
{
    // Basic info
    private String name;
    private String category;  // RUNE_SHOP, CHARTER_SHIP, FOOD_SHOP, etc.

    // Structured coordinates
    private Coordinate location;
    private Coordinate nearestBank;
    private Coordinate teleportLocation;

    // Teleport info
    private int teleportId;  // The Item ID required to teleport (0 for none/spell)
    
    // Shop contents
    private List<ShopItemData> items;

    // Access requirements
    private boolean isMembers;
    private boolean isWilderness;
    private String requirements;  // Quest/skill requirements as display string
    private String notes;  // Additional info for tooltips

    /**
     * Calculates the distance between the shop and the bank in tiles.
     * Returns 999 if data is missing or bank is not defined.
     */
    public int getDistanceToBank() {
        if (location == null || nearestBank == null) return 999;
        // Check for invalid bank coordinates (some shops have no nearby bank)
        if (nearestBank.getX() == 0 && nearestBank.getY() == 0) return 999;
        return location.toWorldPoint().distanceTo(nearestBank.toWorldPoint());
    }

    /**
     * Helper to get the WorldPoint of the teleport arrival spot.
     * Returns null if no teleport is defined for this shop.
     */
    public WorldPoint getTeleportLocation() {
        if (teleportLocation == null) return null;
        // Check for invalid teleport coordinates
        if (teleportLocation.getX() == 0 && teleportLocation.getY() == 0) return null;
        return teleportLocation.toWorldPoint();
    }

    /**
     * Check if this shop has a valid teleport option
     */
    public boolean hasTeleport() {
        return teleportId > 0 || getTeleportLocation() != null;
    }

    /**
     * Get display name with category prefix for sorting/filtering
     */
    public String getDisplayName() {
        if (category != null && !category.isEmpty()) {
            return "[" + formatCategory(category) + "] " + name;
        }
        return name;
    }

    /**
     * Format category for display (RUNE_SHOP -> Rune)
     */
    private String formatCategory(String cat) {
        if (cat == null) return "";
        String formatted = cat.replace("_SHOP", "").replace("_", " ");
        // Title case
        if (formatted.length() > 0) {
            return formatted.substring(0, 1).toUpperCase() + 
                   formatted.substring(1).toLowerCase();
        }
        return formatted;
    }

    /**
     * Check if shop matches a category filter
     */
    public boolean matchesCategory(String filter) {
        if (filter == null || filter.isEmpty() || "ALL".equals(filter)) {
            return true;
        }
        return filter.equalsIgnoreCase(category);
    }

    /**
     * Check if shop is accessible (not wilderness, or user accepts risk)
     */
    public boolean isSafeAccess() {
        return !isWilderness;
    }

    @Getter
    public static class Coordinate {
        private int x;
        private int y;
        private int plane;

        public WorldPoint toWorldPoint() {
            return new WorldPoint(x, y, plane);
        }

        public boolean isValid() {
            return x != 0 || y != 0;
        }

        @Override
        public String toString() {
            return x + "," + y + "," + plane;
        }
    }
}
