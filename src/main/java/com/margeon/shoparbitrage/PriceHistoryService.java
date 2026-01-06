package com.margeon.shoparbitrage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Advanced price history service that tracks item prices over time
 * for trend analysis and prediction.
 *
 * Uses OSRS Wiki API endpoints:
 * - /5m - 5-minute interval data (short-term)
 * - /1h - 1-hour interval data (medium-term)
 * - /timeseries - Historical data for specific items (long-term)
 */
@Slf4j
@Singleton
public class PriceHistoryService
{
    private static final String WIKI_API_BASE = "https://prices.runescape.wiki/api/v1/osrs/";
    private static final String USER_AGENT = "ShopArbitragePlugin - Discord: philly_9859";
    private static final File HISTORY_FILE = new File(RuneLite.RUNELITE_DIR, "price-history-cache.json");

    // Cache durations
    private static final long FIVE_MIN_CACHE_MS = 5 * 60 * 1000;      // 5 minutes
    private static final long ONE_HOUR_CACHE_MS = 60 * 60 * 1000;     // 1 hour
    private static final long TIMESERIES_CACHE_MS = 6 * 60 * 60 * 1000; // 6 hours

    private final OkHttpClient okHttpClient;
    private final Gson gson;

    // Price data caches
    private final Map<Integer, List<PricePoint>> fiveMinHistory = new ConcurrentHashMap<>();
    private final Map<Integer, List<PricePoint>> oneHourHistory = new ConcurrentHashMap<>();
    private final Map<Integer, List<PricePoint>> timeSeriesHistory = new ConcurrentHashMap<>();

    // Last fetch timestamps
    private long lastFiveMinFetch = 0;
    private long lastOneHourFetch = 0;
    private final Map<Integer, Long> lastTimeSeriesFetch = new ConcurrentHashMap<>();

    // Calculated metrics cache
    private final Map<Integer, ItemMetrics> metricsCache = new ConcurrentHashMap<>();

    @Inject
    public PriceHistoryService(OkHttpClient okHttpClient, Gson gson)
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        loadCachedHistory();
    }

    /**
     * Fetch 5-minute interval data for all items
     * Best for: Short-term trend detection, recent price movements
     */
    public void fetchFiveMinuteData(Runnable onComplete)
    {
        long now = System.currentTimeMillis();
        if (now - lastFiveMinFetch < FIVE_MIN_CACHE_MS && !fiveMinHistory.isEmpty())
        {
            log.debug("Using cached 5-minute data");
            if (onComplete != null) onComplete.run();
            return;
        }

        Request request = new Request.Builder()
                .url(WIKI_API_BASE + "5m")
                .header("User-Agent", USER_AGENT)
                .build();

        log.info("Fetching 5-minute price data from Wiki API...");

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to fetch 5-minute data: {}", e.getMessage());
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try
                {
                    if (!response.isSuccessful())
                    {
                        log.warn("5-minute API returned error: {}", response.code());
                        return;
                    }

                    String body = response.body().string();
                    JsonObject json = gson.fromJson(body, JsonObject.class);

                    if (json.has("data"))
                    {
                        JsonObject data = json.getAsJsonObject("data");
                        long timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : System.currentTimeMillis() / 1000;

                        for (Map.Entry<String, JsonElement> entry : data.entrySet())
                        {
                            try
                            {
                                int itemId = Integer.parseInt(entry.getKey());
                                JsonObject itemData = entry.getValue().getAsJsonObject();

                                PricePoint point = new PricePoint();
                                point.timestamp = timestamp;
                                point.avgHighPrice = itemData.has("avgHighPrice") && !itemData.get("avgHighPrice").isJsonNull()
                                        ? itemData.get("avgHighPrice").getAsInt() : 0;
                                point.avgLowPrice = itemData.has("avgLowPrice") && !itemData.get("avgLowPrice").isJsonNull()
                                        ? itemData.get("avgLowPrice").getAsInt() : 0;
                                point.highPriceVolume = itemData.has("highPriceVolume") && !itemData.get("highPriceVolume").isJsonNull()
                                        ? itemData.get("highPriceVolume").getAsLong() : 0;
                                point.lowPriceVolume = itemData.has("lowPriceVolume") && !itemData.get("lowPriceVolume").isJsonNull()
                                        ? itemData.get("lowPriceVolume").getAsLong() : 0;

                                // Add to history (keep last 288 points = 24 hours of 5-min data)
                                fiveMinHistory.computeIfAbsent(itemId, k -> new ArrayList<>()).add(point);
                                trimHistory(fiveMinHistory.get(itemId), 288);

                            }
                            catch (NumberFormatException ignored) {}
                        }

                        lastFiveMinFetch = System.currentTimeMillis();
                        log.info("Fetched 5-minute data for {} items", data.size());
                    }
                }
                catch (Exception e)
                {
                    log.error("Error parsing 5-minute data", e);
                }
                finally
                {
                    response.close();
                    if (onComplete != null) onComplete.run();
                }
            }
        });
    }

    /**
     * Fetch 1-hour interval data for all items
     * Best for: Medium-term trends, daily patterns
     */
    public void fetchOneHourData(Runnable onComplete)
    {
        long now = System.currentTimeMillis();
        if (now - lastOneHourFetch < ONE_HOUR_CACHE_MS && !oneHourHistory.isEmpty())
        {
            log.debug("Using cached 1-hour data");
            if (onComplete != null) onComplete.run();
            return;
        }

        Request request = new Request.Builder()
                .url(WIKI_API_BASE + "1h")
                .header("User-Agent", USER_AGENT)
                .build();

        log.info("Fetching 1-hour price data from Wiki API...");

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to fetch 1-hour data: {}", e.getMessage());
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try
                {
                    if (!response.isSuccessful())
                    {
                        log.warn("1-hour API returned error: {}", response.code());
                        return;
                    }

                    String body = response.body().string();
                    JsonObject json = gson.fromJson(body, JsonObject.class);

                    if (json.has("data"))
                    {
                        JsonObject data = json.getAsJsonObject("data");
                        long timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : System.currentTimeMillis() / 1000;

                        for (Map.Entry<String, JsonElement> entry : data.entrySet())
                        {
                            try
                            {
                                int itemId = Integer.parseInt(entry.getKey());
                                JsonObject itemData = entry.getValue().getAsJsonObject();

                                PricePoint point = new PricePoint();
                                point.timestamp = timestamp;
                                point.avgHighPrice = itemData.has("avgHighPrice") && !itemData.get("avgHighPrice").isJsonNull()
                                        ? itemData.get("avgHighPrice").getAsInt() : 0;
                                point.avgLowPrice = itemData.has("avgLowPrice") && !itemData.get("avgLowPrice").isJsonNull()
                                        ? itemData.get("avgLowPrice").getAsInt() : 0;
                                point.highPriceVolume = itemData.has("highPriceVolume") && !itemData.get("highPriceVolume").isJsonNull()
                                        ? itemData.get("highPriceVolume").getAsLong() : 0;
                                point.lowPriceVolume = itemData.has("lowPriceVolume") && !itemData.get("lowPriceVolume").isJsonNull()
                                        ? itemData.get("lowPriceVolume").getAsLong() : 0;

                                // Add to history (keep last 168 points = 7 days of hourly data)
                                oneHourHistory.computeIfAbsent(itemId, k -> new ArrayList<>()).add(point);
                                trimHistory(oneHourHistory.get(itemId), 168);

                            }
                            catch (NumberFormatException ignored) {}
                        }

                        lastOneHourFetch = System.currentTimeMillis();
                        log.info("Fetched 1-hour data for {} items", data.size());
                    }
                }
                catch (Exception e)
                {
                    log.error("Error parsing 1-hour data", e);
                }
                finally
                {
                    response.close();
                    if (onComplete != null) onComplete.run();
                }
            }
        });
    }

    /**
     * Fetch detailed timeseries for a specific item
     * Best for: Deep analysis of individual items, long-term trends
     */
    public void fetchTimeSeries(int itemId, String timestep, Runnable onComplete)
    {
        Long lastFetch = lastTimeSeriesFetch.get(itemId);
        if (lastFetch != null && System.currentTimeMillis() - lastFetch < TIMESERIES_CACHE_MS)
        {
            log.debug("Using cached timeseries for item {}", itemId);
            if (onComplete != null) onComplete.run();
            return;
        }

        // timestep can be "5m", "1h", "6h", "24h"
        Request request = new Request.Builder()
                .url(WIKI_API_BASE + "timeseries?timestep=" + timestep + "&id=" + itemId)
                .header("User-Agent", USER_AGENT)
                .build();

        log.info("Fetching timeseries for item {} with timestep {}", itemId, timestep);

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to fetch timeseries for item {}: {}", itemId, e.getMessage());
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try
                {
                    if (!response.isSuccessful())
                    {
                        log.warn("Timeseries API returned error: {}", response.code());
                        return;
                    }

                    String body = response.body().string();
                    JsonObject json = gson.fromJson(body, JsonObject.class);

                    if (json.has("data"))
                    {
                        JsonArray dataArray = json.getAsJsonArray("data");
                        List<PricePoint> points = new ArrayList<>();

                        for (JsonElement elem : dataArray)
                        {
                            JsonObject point = elem.getAsJsonObject();
                            PricePoint pp = new PricePoint();
                            pp.timestamp = point.get("timestamp").getAsLong();
                            pp.avgHighPrice = point.has("avgHighPrice") && !point.get("avgHighPrice").isJsonNull()
                                    ? point.get("avgHighPrice").getAsInt() : 0;
                            pp.avgLowPrice = point.has("avgLowPrice") && !point.get("avgLowPrice").isJsonNull()
                                    ? point.get("avgLowPrice").getAsInt() : 0;
                            pp.highPriceVolume = point.has("highPriceVolume") && !point.get("highPriceVolume").isJsonNull()
                                    ? point.get("highPriceVolume").getAsLong() : 0;
                            pp.lowPriceVolume = point.has("lowPriceVolume") && !point.get("lowPriceVolume").isJsonNull()
                                    ? point.get("lowPriceVolume").getAsLong() : 0;
                            points.add(pp);
                        }

                        timeSeriesHistory.put(itemId, points);
                        lastTimeSeriesFetch.put(itemId, System.currentTimeMillis());
                        log.info("Fetched {} timeseries points for item {}", points.size(), itemId);
                    }
                }
                catch (Exception e)
                {
                    log.error("Error parsing timeseries data", e);
                }
                finally
                {
                    response.close();
                    if (onComplete != null) onComplete.run();
                }
            }
        });
    }

    /**
     * Get calculated metrics for an item
     */
    public ItemMetrics getMetrics(int itemId)
    {
        return metricsCache.get(itemId);
    }

    /**
     * Calculate and cache metrics for all tracked items
     */
    public void calculateAllMetrics()
    {
        Set<Integer> allItems = new HashSet<>();
        allItems.addAll(fiveMinHistory.keySet());
        allItems.addAll(oneHourHistory.keySet());

        for (int itemId : allItems)
        {
            ItemMetrics metrics = calculateMetrics(itemId);
            if (metrics != null)
            {
                metricsCache.put(itemId, metrics);
            }
        }

        log.info("Calculated metrics for {} items", metricsCache.size());
    }

    /**
     * Calculate comprehensive metrics for a single item
     */
    public ItemMetrics calculateMetrics(int itemId)
    {
        List<PricePoint> shortTerm = fiveMinHistory.get(itemId);
        List<PricePoint> mediumTerm = oneHourHistory.get(itemId);

        if ((shortTerm == null || shortTerm.isEmpty()) && (mediumTerm == null || mediumTerm.isEmpty()))
        {
            return null;
        }

        ItemMetrics metrics = new ItemMetrics();
        metrics.itemId = itemId;
        metrics.calculatedAt = System.currentTimeMillis();

        // Use whichever data we have
        List<PricePoint> primaryData = shortTerm != null && !shortTerm.isEmpty() ? shortTerm : mediumTerm;

        if (primaryData.size() >= 2)
        {
            // Current prices (most recent)
            PricePoint latest = primaryData.get(primaryData.size() - 1);
            metrics.currentHigh = latest.avgHighPrice;
            metrics.currentLow = latest.avgLowPrice;
            metrics.currentMargin = latest.avgHighPrice - latest.avgLowPrice;
            metrics.currentVolume = latest.highPriceVolume + latest.lowPriceVolume;

            // Calculate spread percentage
            if (latest.avgLowPrice > 0)
            {
                metrics.spreadPercent = (double) metrics.currentMargin / latest.avgLowPrice * 100;
            }

            // Price changes
            if (primaryData.size() >= 12) // At least 1 hour of 5-min data
            {
                PricePoint hourAgo = primaryData.get(primaryData.size() - 12);
                metrics.priceChange1h = latest.avgHighPrice - hourAgo.avgHighPrice;
                metrics.priceChangePercent1h = hourAgo.avgHighPrice > 0
                        ? (double) metrics.priceChange1h / hourAgo.avgHighPrice * 100 : 0;
            }

            if (primaryData.size() >= 72) // 6 hours
            {
                PricePoint sixHoursAgo = primaryData.get(primaryData.size() - 72);
                metrics.priceChange6h = latest.avgHighPrice - sixHoursAgo.avgHighPrice;
                metrics.priceChangePercent6h = sixHoursAgo.avgHighPrice > 0
                        ? (double) metrics.priceChange6h / sixHoursAgo.avgHighPrice * 100 : 0;
            }

            // Volatility (standard deviation of price changes)
            metrics.volatility = calculateVolatility(primaryData);

            // Trend strength (using simple linear regression slope)
            metrics.trendStrength = calculateTrendStrength(primaryData);

            // Volume trend
            metrics.volumeTrend = calculateVolumeTrend(primaryData);

            // Margin stability (how consistent is the spread)
            metrics.marginStability = calculateMarginStability(primaryData);

            // RSI (Relative Strength Index)
            metrics.rsi = calculateRSI(primaryData, 14);

            // Moving averages
            metrics.sma12 = calculateSMA(primaryData, 12);
            metrics.sma24 = calculateSMA(primaryData, 24);
            metrics.ema12 = calculateEMA(primaryData, 12);
        }

        return metrics;
    }

    /**
     * Calculate price volatility (coefficient of variation)
     */
    private double calculateVolatility(List<PricePoint> data)
    {
        if (data.size() < 2) return 0;

        // Calculate returns
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < data.size(); i++)
        {
            if (data.get(i - 1).avgHighPrice > 0)
            {
                double ret = (double)(data.get(i).avgHighPrice - data.get(i - 1).avgHighPrice)
                        / data.get(i - 1).avgHighPrice;
                returns.add(ret);
            }
        }

        if (returns.isEmpty()) return 0;

        // Calculate standard deviation
        double mean = returns.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = returns.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);

        return Math.sqrt(variance) * 100; // As percentage
    }

    /**
     * Calculate trend strength using linear regression
     * Positive = uptrend, Negative = downtrend, Near 0 = sideways
     */
    private double calculateTrendStrength(List<PricePoint> data)
    {
        if (data.size() < 5) return 0;

        int n = data.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++)
        {
            double x = i;
            double y = data.get(i).avgHighPrice;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double avgPrice = sumY / n;

        // Normalize slope by average price to get percentage trend per period
        return avgPrice > 0 ? (slope / avgPrice) * 100 : 0;
    }

    /**
     * Calculate volume trend (is volume increasing or decreasing?)
     */
    private double calculateVolumeTrend(List<PricePoint> data)
    {
        if (data.size() < 10) return 0;

        // Compare recent volume (last 5) to earlier volume (5 before that)
        long recentVolume = 0;
        long earlierVolume = 0;

        int size = data.size();
        for (int i = size - 5; i < size; i++)
        {
            recentVolume += data.get(i).highPriceVolume + data.get(i).lowPriceVolume;
        }
        for (int i = size - 10; i < size - 5; i++)
        {
            earlierVolume += data.get(i).highPriceVolume + data.get(i).lowPriceVolume;
        }

        if (earlierVolume == 0) return 0;
        return ((double)(recentVolume - earlierVolume) / earlierVolume) * 100;
    }

    /**
     * Calculate margin stability (lower = more stable)
     */
    private double calculateMarginStability(List<PricePoint> data)
    {
        if (data.size() < 5) return 100; // Unknown = unstable

        List<Integer> margins = new ArrayList<>();
        for (PricePoint point : data)
        {
            margins.add(point.avgHighPrice - point.avgLowPrice);
        }

        double mean = margins.stream().mapToInt(i -> i).average().orElse(0);
        if (mean == 0) return 100;

        double variance = margins.stream().mapToDouble(m -> Math.pow(m - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        // Coefficient of variation (lower = more stable)
        return (stdDev / mean) * 100;
    }

    /**
     * Calculate RSI (Relative Strength Index)
     * < 30 = oversold (potential buy)
     * > 70 = overbought (potential sell)
     */
    private double calculateRSI(List<PricePoint> data, int periods)
    {
        if (data.size() < periods + 1) return 50; // Neutral

        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        for (int i = data.size() - periods; i < data.size(); i++)
        {
            double change = data.get(i).avgHighPrice - data.get(i - 1).avgHighPrice;
            if (change > 0)
            {
                gains.add(change);
                losses.add(0.0);
            }
            else
            {
                gains.add(0.0);
                losses.add(Math.abs(change));
            }
        }

        double avgGain = gains.stream().mapToDouble(d -> d).average().orElse(0);
        double avgLoss = losses.stream().mapToDouble(d -> d).average().orElse(0);

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /**
     * Calculate Simple Moving Average
     */
    private int calculateSMA(List<PricePoint> data, int periods)
    {
        if (data.size() < periods) return 0;

        double sum = 0;
        for (int i = data.size() - periods; i < data.size(); i++)
        {
            sum += data.get(i).avgHighPrice;
        }
        return (int) (sum / periods);
    }

    /**
     * Calculate Exponential Moving Average
     */
    private int calculateEMA(List<PricePoint> data, int periods)
    {
        if (data.size() < periods) return 0;

        double multiplier = 2.0 / (periods + 1);
        double ema = data.get(data.size() - periods).avgHighPrice;

        for (int i = data.size() - periods + 1; i < data.size(); i++)
        {
            ema = (data.get(i).avgHighPrice - ema) * multiplier + ema;
        }

        return (int) ema;
    }

    private void trimHistory(List<PricePoint> history, int maxSize)
    {
        while (history.size() > maxSize)
        {
            history.remove(0);
        }
    }

    /**
     * Save history to disk for persistence
     */
    public void saveHistory()
    {
        try (Writer writer = new FileWriter(HISTORY_FILE))
        {
            HistoryCache cache = new HistoryCache();
            cache.fiveMinHistory = new HashMap<>(fiveMinHistory);
            cache.oneHourHistory = new HashMap<>(oneHourHistory);
            cache.savedAt = System.currentTimeMillis();
            gson.toJson(cache, writer);
            log.debug("Saved price history cache");
        }
        catch (IOException e)
        {
            log.error("Failed to save price history", e);
        }
    }

    private void loadCachedHistory()
    {
        if (!HISTORY_FILE.exists()) return;

        try (Reader reader = new FileReader(HISTORY_FILE))
        {
            HistoryCache cache = gson.fromJson(reader, HistoryCache.class);
            if (cache != null)
            {
                // Only load if cache is less than 24 hours old
                if (System.currentTimeMillis() - cache.savedAt < TimeUnit.HOURS.toMillis(24))
                {
                    if (cache.fiveMinHistory != null) fiveMinHistory.putAll(cache.fiveMinHistory);
                    if (cache.oneHourHistory != null) oneHourHistory.putAll(cache.oneHourHistory);
                    log.info("Loaded {} 5-min and {} 1-hour cached items",
                            fiveMinHistory.size(), oneHourHistory.size());
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load price history cache: {}", e.getMessage());
        }
    }

    // Data classes
    public static class PricePoint
    {
        public long timestamp;
        public int avgHighPrice;
        public int avgLowPrice;
        public long highPriceVolume;
        public long lowPriceVolume;

        public long getTotalVolume()
        {
            return highPriceVolume + lowPriceVolume;
        }

        public int getMargin()
        {
            return avgHighPrice - avgLowPrice;
        }
    }

    public static class ItemMetrics
    {
        public int itemId;
        public long calculatedAt;

        // Current state
        public int currentHigh;
        public int currentLow;
        public int currentMargin;
        public long currentVolume;
        public double spreadPercent;

        // Price changes
        public int priceChange1h;
        public double priceChangePercent1h;
        public int priceChange6h;
        public double priceChangePercent6h;

        // Technical indicators
        public double volatility;       // Price volatility (lower = more stable)
        public double trendStrength;    // Positive = uptrend, negative = downtrend
        public double volumeTrend;      // Volume change percentage
        public double marginStability;  // Margin consistency (lower = more stable)
        public double rsi;              // RSI indicator (30-70 = neutral)

        // Moving averages
        public int sma12;
        public int sma24;
        public int ema12;

        /**
         * Is this item in an uptrend?
         */
        public boolean isUptrend()
        {
            return trendStrength > 0.1 && currentHigh > sma12;
        }

        /**
         * Is this item in a downtrend?
         */
        public boolean isDowntrend()
        {
            return trendStrength < -0.1 && currentHigh < sma12;
        }

        /**
         * Is the margin stable enough for reliable flipping?
         */
        public boolean hasStableMargin()
        {
            return marginStability < 30; // Less than 30% variation
        }

        /**
         * Is there enough volume for quick flips?
         */
        public boolean hasGoodLiquidity()
        {
            return currentVolume > 100; // Arbitrary threshold
        }
    }

    private static class HistoryCache
    {
        Map<Integer, List<PricePoint>> fiveMinHistory;
        Map<Integer, List<PricePoint>> oneHourHistory;
        long savedAt;
    }
}