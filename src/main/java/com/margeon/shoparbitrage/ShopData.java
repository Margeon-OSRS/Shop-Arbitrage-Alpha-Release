package com.margeon.shoparbitrage;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import java.util.List;

@Getter
public class ShopData
{
    // Renamed from 'shopName' to 'name' for cleanliness
    private String name;

    // Structured coordinates
    private Coordinate location;
    private Coordinate nearestBank;

    // NEW: The specific tile where the teleport lands
    private Coordinate teleportLocation;

    // Your specific logic fields
    private int teleportId; // The Item ID required to teleport (e.g., Tablet ID)
    private List<ShopItemData> items;

    /**
     * Calculates the distance between the shop and the bank in tiles.
     * Returns 999 if data is missing.
     */
    public int getDistanceToBank() {
        if (location == null || nearestBank == null) return 999;
        return location.toWorldPoint().distanceTo(nearestBank.toWorldPoint());
    }

    /**
     * Helper to get the WorldPoint of the teleport arrival spot.
     * Returns null if no teleport is defined for this shop.
     */
    public WorldPoint getTeleportLocation() {
        if (teleportLocation == null) return null;
        return teleportLocation.toWorldPoint();
    }

    @Getter
    public static class Coordinate {
        private int x;
        private int y;
        private int plane;

        public WorldPoint toWorldPoint() {
            return new WorldPoint(x, y, plane);
        }

        @Override
        public String toString() {
            return x + "," + y + "," + plane;
        }
    }
}