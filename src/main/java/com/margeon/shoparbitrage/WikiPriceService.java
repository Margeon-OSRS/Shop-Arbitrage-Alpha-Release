package com.margeon.shoparbitrage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.gson.annotations.SerializedName;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject; // ENSURE THIS IS IMPORTED
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class WikiPriceService
{
    private final OkHttpClient okHttpClient;
    private final Gson gson;

    private final Map<Integer, WikiPrice> priceCache = new HashMap<>();
    private long lastFetchTime = 0;
    private static final long CACHE_DURATION_MS = 60 * 1000;

    // --- FIX: ADDED @Inject HERE ---
    @Inject
    public WikiPriceService(OkHttpClient okHttpClient, Gson gson)
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    public void fetchLivePrices(Runnable onSuccess)
    {
        long now = System.currentTimeMillis();

        if (now - lastFetchTime < CACHE_DURATION_MS && !priceCache.isEmpty()) {
            log.debug("Using cached Wiki prices (Data is {}s old)", (now - lastFetchTime) / 1000);
            if (onSuccess != null) onSuccess.run();
            return;
        }

        Request request = new Request.Builder()
                .url("https://prices.runescape.wiki/api/v1/osrs/24h")
                .header("User-Agent", "ShopArbitragePlugin - Discord: philly_9859")
                .build();

        log.debug("Fetching 24h prices (with volume) from Wiki API...");

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Wiki API connection failed: {}", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                if (!response.isSuccessful()) {
                    log.warn("Wiki API returned error: {}", response.code());
                    response.close();
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                    if (json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");
                        Type type = new TypeToken<Map<String, WikiPrice>>(){}.getType();
                        Map<String, WikiPrice> parsedData = gson.fromJson(data, type);

                        synchronized (priceCache) {
                            priceCache.clear();
                            for (Map.Entry<String, WikiPrice> entry : parsedData.entrySet()) {
                                try {
                                    priceCache.put(Integer.parseInt(entry.getKey()), entry.getValue());
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        lastFetchTime = System.currentTimeMillis();
                        if (onSuccess != null) onSuccess.run();
                    }
                } catch (Exception e) {
                    log.error("Error parsing Wiki API data", e);
                } finally {
                    response.close();
                }
            }
        });
    }

    public WikiPrice getPrice(int itemId)
    {
        synchronized (priceCache) {
            return priceCache.get(itemId);
        }
    }

    public Map<Integer, WikiPrice> getAllPrices()
    {
        synchronized (priceCache) {
            return new HashMap<>(priceCache);
        }
    }

    public static class WikiPrice {
        @SerializedName(value = "high", alternate = "avgHighPrice")
        public int high;

        @SerializedName(value = "low", alternate = "avgLowPrice")
        public int low;

        @SerializedName("highPriceVolume")
        public long highPriceVolume;

        @SerializedName("lowPriceVolume")
        public long lowPriceVolume;

        public int highTime;
        public int lowTime;

        public long getDailyVolume() {
            return highPriceVolume + lowPriceVolume;
        }
    }
}