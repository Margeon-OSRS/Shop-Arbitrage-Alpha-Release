package com.margeon.shoparbitrage;

import com.google.gson.Gson;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class FlippingSessionManager
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlippingSessionManager.class);
    private static final File HISTORY_FILE = new File(RuneLite.RUNELITE_DIR, "shop-arbitrage-history.json");

    // Memory management constants
    private static final int MAX_BUY_HISTORY_PER_ITEM = 1000;
    private static final int MAX_COMPLETED_FLIPS = 100;
    private static final long MAX_GE_TAX = 5_000_000L;
    private static final double GE_TAX_RATE = 0.01;

    private final Gson gson;
    private SessionData data = new SessionData();
    private final List<Runnable> listeners = new ArrayList<>();

    @Inject
    public FlippingSessionManager(Gson gson)
    {
        this.gson = gson;
        loadData();
    }

    public void addListener(Runnable callback)
    {
        this.listeners.add(callback);
    }

    private void notifyListeners()
    {
        for (Runnable r : listeners)
        {
            try
            {
                r.run();
            }
            catch (Exception e)
            {
                log.error("Error notifying listener", e);
            }
        }
    }

    public long getSessionProfit() { return data.totalProfit; }

    public List<FlipTransaction> getHistory() { return new ArrayList<>(data.completedFlips); }

    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        GrandExchangeOffer offer = event.getOffer();
        if (offer.getState() != GrandExchangeOfferState.BOUGHT &&
                offer.getState() != GrandExchangeOfferState.SOLD)
        {
            return;
        }

        int itemId = offer.getItemId();
        int price = offer.getPrice();
        int qty = offer.getTotalQuantity();

        boolean dataChanged = false;

        if (offer.getState() == GrandExchangeOfferState.BOUGHT)
        {
            dataChanged = recordBuy(itemId, price, qty);
        }
        else if (offer.getState() == GrandExchangeOfferState.SOLD)
        {
            dataChanged = recordSell(itemId, price, qty);
        }

        if (dataChanged)
        {
            saveDataAsync();
        }
    }

    private boolean recordBuy(int itemId, int price, int quantity)
    {
        data.buyHistory.putIfAbsent(itemId, new ArrayList<>());
        List<Integer> prices = data.buyHistory.get(itemId);

        for (int i = 0; i < quantity; i++)
        {
            prices.add(price);
        }

        // FIXED: Prevent memory leak by limiting buy history size
        while (prices.size() > MAX_BUY_HISTORY_PER_ITEM)
        {
            prices.remove(0);
            log.debug("Trimmed old buy history for item {} to prevent memory leak", itemId);
        }

        return true;
    }

    private boolean recordSell(int itemId, int price, int quantity)
    {
        if (!data.buyHistory.containsKey(itemId) || data.buyHistory.get(itemId).isEmpty())
        {
            log.debug("Sell recorded for item {} but no matching buy history found", itemId);
            return false;
        }

        List<Integer> buys = data.buyHistory.get(itemId);
        long totalBuyCost = 0;
        int matchedCount = 0;

        while (matchedCount < quantity && !buys.isEmpty())
        {
            totalBuyCost += buys.remove(0);
            matchedCount++;
        }

        if (matchedCount > 0)
        {
            long totalRevenue = (long) price * matchedCount;
            long tradeTax = calculateGETax(totalRevenue);
            long profit = totalRevenue - totalBuyCost - tradeTax;

            data.totalProfit += profit;
            data.totalTax += tradeTax;

            FlipTransaction transaction = new FlipTransaction();
            transaction.itemId = itemId;
            transaction.quantity = matchedCount;
            transaction.profit = profit;
            transaction.timestamp = System.currentTimeMillis();

            data.completedFlips.add(0, transaction);

            // Trim history to prevent unbounded growth
            while (data.completedFlips.size() > MAX_COMPLETED_FLIPS)
            {
                data.completedFlips.remove(data.completedFlips.size() - 1);
            }

            notifyListeners();
            log.info("Flip completed: Item {}, Qty {}, Profit {}", itemId, matchedCount, profit);
            return true;
        }

        return false;
    }

    private long calculateGETax(long revenue)
    {
        return Math.min((long) Math.floor(revenue * GE_TAX_RATE), MAX_GE_TAX);
    }

    // IMPROVED: Async file saving to avoid blocking the UI thread
    private void saveDataAsync()
    {
        CompletableFuture.runAsync(() -> {
            try (Writer writer = new FileWriter(HISTORY_FILE))
            {
                gson.toJson(data, writer);
                log.debug("Session data saved successfully");
            }
            catch (IOException e)
            {
                log.error("Failed to save session data to {}", HISTORY_FILE.getAbsolutePath(), e);
            }
        });
    }

    private void loadData()
    {
        if (!HISTORY_FILE.exists())
        {
            log.info("No existing session data found, starting fresh");
            return;
        }

        try (Reader reader = new FileReader(HISTORY_FILE))
        {
            SessionData loaded = gson.fromJson(reader, SessionData.class);
            if (loaded != null)
            {
                this.data = loaded;

                // Ensure collections are initialized
                if (data.buyHistory == null)
                {
                    data.buyHistory = new HashMap<>();
                }
                if (data.completedFlips == null)
                {
                    data.completedFlips = new ArrayList<>();
                }

                log.info("Session data loaded: {} flips, total profit: {}",
                        data.completedFlips.size(), data.totalProfit);
            }
        }
        catch (IOException e)
        {
            log.error("Failed to load session data from {}", HISTORY_FILE.getAbsolutePath(), e);
        }
        catch (Exception e)
        {
            log.error("Corrupted session data file, starting fresh", e);
            data = new SessionData();
        }
    }

    private static class SessionData
    {
        long totalProfit = 0;
        long totalTax = 0;
        Map<Integer, List<Integer>> buyHistory = new HashMap<>();
        List<FlipTransaction> completedFlips = new ArrayList<>();
    }

    public static class FlipTransaction
    {
        public int itemId;
        public int quantity;
        public long profit;
        public long timestamp;
    }
}