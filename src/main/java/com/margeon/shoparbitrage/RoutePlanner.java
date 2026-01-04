package com.margeon.shoparbitrage;

import net.runelite.api.coords.WorldPoint;
import java.util.*;

/**
 * Calculates optimal routes through multiple shops using nearest-neighbor algorithm
 * considering both walking and teleportation paths.
 */
public class RoutePlanner
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoutePlanner.class);

    // Average running speed in tiles per second (with obstacles)
    private static final double RUNNING_SPEED = 2.5;

    // Time spent at each shop (buying, banking)
    private static final int SHOP_STOP_TIME_SECONDS = 30;

    // Penalty for teleporting (animation time + menu interaction) converted to "tile distance"
    // 4 seconds @ 2.5 tiles/sec = ~10 tiles.
    // If walking is < 10 tiles longer than the run from teleport, just walk.
    private static final int TELEPORT_PENALTY_TILES = 10;

    /**
     * Calculate optimal route using nearest-neighbor algorithm
     *
     * @param shops List of shops to visit
     * @param startLocation Starting location (e.g., player's current position)
     * @return Optimized route
     */
    public static PlannedRoute calculateRoute(List<ShopData> shops, WorldPoint startLocation)
    {
        if (shops == null || shops.isEmpty())
        {
            log.warn("Cannot calculate route with empty shop list");
            return new PlannedRoute(new ArrayList<>(), 0, 0);
        }

        List<ShopData> remaining = new ArrayList<>(shops);
        List<RouteStop> route = new ArrayList<>();

        WorldPoint currentLocation = startLocation;
        int totalDistance = 0;

        // If only 1 shop, we still need to decide if we walk or teleport to it
        if (shops.size() == 1)
        {
            return calculateSingleStop(shops.get(0), startLocation);
        }

        while (!remaining.isEmpty())
        {
            ShopData nearest = null;
            int bestCost = Integer.MAX_VALUE;
            boolean shouldTeleportToNearest = false;
            int actualRunDistance = 0; // The distance we actually run (excluding the jump across map)

            for (ShopData shop : remaining)
            {
                WorldPoint shopLocation = shop.getLocation().toWorldPoint();

                // Option A: Walk from current location
                int walkDist = calculateDistance(currentLocation, shopLocation);

                // Option B: Teleport (Teleport Spot -> Shop)
                // Note: You must add getTeleportLocation() to your ShopData class
                WorldPoint teleportSpot = shop.getTeleportLocation();
                int teleRunDist = (teleportSpot != null) ? calculateDistance(teleportSpot, shopLocation) : Integer.MAX_VALUE;

                // Cost of teleporting includes the run from the spot + the time penalty for casting
                int teleCost = (teleportSpot != null) ? teleRunDist + TELEPORT_PENALTY_TILES : Integer.MAX_VALUE;

                // Determine the "Effective Cost" to get to this shop
                int currentShopCost;
                boolean useTeleport;
                int currentRunDist;

                if (teleCost < walkDist)
                {
                    currentShopCost = teleCost;
                    useTeleport = true;
                    currentRunDist = teleRunDist;
                }
                else
                {
                    currentShopCost = walkDist;
                    useTeleport = false;
                    currentRunDist = walkDist;
                }

                // Check if this is the "closest" shop (lowest effort to reach)
                if (currentShopCost < bestCost)
                {
                    bestCost = currentShopCost;
                    nearest = shop;
                    shouldTeleportToNearest = useTeleport;
                    actualRunDistance = currentRunDist;
                }
            }

            if (nearest != null)
            {
                route.add(new RouteStop(nearest, actualRunDistance, shouldTeleportToNearest));
                totalDistance += actualRunDistance;

                // Update current location for the next iteration
                currentLocation = nearest.getLocation().toWorldPoint();
                remaining.remove(nearest);

                log.debug("Added {} to route. Method: {}, Distance: {}",
                        nearest.getName(), shouldTeleportToNearest ? "Teleport" : "Walk", actualRunDistance);
            }
            else
            {
                break;
            }
        }

        int estimatedTime = estimateTime(totalDistance, shops.size());

        log.info("Route calculated: {} shops, {} run tiles, ~{} minutes",
                route.size(), totalDistance, estimatedTime);

        return new PlannedRoute(route, totalDistance, estimatedTime);
    }

    private static PlannedRoute calculateSingleStop(ShopData shop, WorldPoint start)
    {
        WorldPoint shopLoc = shop.getLocation().toWorldPoint();
        WorldPoint teleLoc = shop.getTeleportLocation();

        int walkDist = calculateDistance(start, shopLoc);
        int teleDist = (teleLoc != null) ? calculateDistance(teleLoc, shopLoc) : Integer.MAX_VALUE;
        int teleCost = (teleLoc != null) ? teleDist + TELEPORT_PENALTY_TILES : Integer.MAX_VALUE;

        boolean useTeleport = teleCost < walkDist;
        int finalDist = useTeleport ? teleDist : walkDist;

        RouteStop stop = new RouteStop(shop, finalDist, useTeleport);
        List<RouteStop> stops = Collections.singletonList(stop);

        return new PlannedRoute(stops, finalDist, estimateTime(finalDist, 1));
    }

    /**
     * Calculate distance between two world points.
     * Uses 9999 for nulls/invalid planes to discourage pathing through them.
     */
    private static int calculateDistance(WorldPoint a, WorldPoint b)
    {
        if (a == null || b == null || a.getPlane() != b.getPlane())
        {
            return 9999;
        }
        return a.distanceTo(b);
    }

    private static int estimateTime(int totalDistance, int numShops)
    {
        int runningTimeSeconds = (int) (totalDistance / RUNNING_SPEED);
        int shopTimeSeconds = numShops * SHOP_STOP_TIME_SECONDS;
        // Add a buffer for menuing/teleport animations (approx 3s per shop)
        int overheadSeconds = numShops * 3;

        int totalSeconds = runningTimeSeconds + shopTimeSeconds + overheadSeconds;
        return (int) Math.ceil(totalSeconds / 60.0);
    }

    public static class PlannedRoute
    {
        private final List<RouteStop> stops;
        private final int totalDistance;
        private final int estimatedTimeMinutes;

        public PlannedRoute(List<RouteStop> stops, int totalDistance, int estimatedTimeMinutes)
        {
            this.stops = stops;
            this.totalDistance = totalDistance;
            this.estimatedTimeMinutes = estimatedTimeMinutes;
        }

        public List<RouteStop> getStops() { return stops; }
        public int getTotalDistance() { return totalDistance; }
        public int getEstimatedTimeMinutes() { return estimatedTimeMinutes; }

        public Set<Integer> getTeleportsNeeded()
        {
            Set<Integer> teleports = new HashSet<>();
            for (RouteStop stop : stops)
            {
                // Only add the teleport if the route actually calls for it
                if (stop.isTeleportRequired() && stop.getShop().getTeleportId() > 0)
                {
                    teleports.add(stop.getShop().getTeleportId());
                }
            }
            return teleports;
        }
    }

    public static class RouteStop
    {
        private final ShopData shop;
        private final int distanceToNext; // Running distance from arrival point (teleport or prev shop)
        private final boolean teleportRequired;

        public RouteStop(ShopData shop, int distanceToNext, boolean teleportRequired)
        {
            this.shop = shop;
            this.distanceToNext = distanceToNext;
            this.teleportRequired = teleportRequired;
        }

        public ShopData getShop() { return shop; }
        public int getDistanceToNext() { return distanceToNext; }
        public boolean isTeleportRequired() { return teleportRequired; }
    }
}
