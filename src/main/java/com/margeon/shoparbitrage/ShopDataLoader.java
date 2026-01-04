package com.margeon.shoparbitrage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class ShopDataLoader
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShopDataLoader.class);

    public List<ShopData> loadShopData()
    {
        try
        {
            log.info("Attempting to load ShopData.json from resources");

            // Read the file from src/main/resources/ShopData.json
            InputStream inputStream = getClass().getResourceAsStream("/ShopData.json");

            if (inputStream == null)
            {
                log.error("ShopData.json not found in resources! Make sure it's in src/main/resources/");
                log.error("Classpath locations checked: /ShopData.json");
                return Collections.emptyList();
            }

            log.info("ShopData.json found, parsing JSON");

            // Convert JSON to Java Objects
            Gson gson = new Gson();
            Type listType = new TypeToken<List<ShopData>>(){}.getType();

            List<ShopData> shops = gson.fromJson(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                    listType
            );

            if (shops == null || shops.isEmpty())
            {
                log.warn("ShopData.json was parsed but contains no shops");
                return Collections.emptyList();
            }

            log.info("Successfully loaded {} shops from ShopData.json", shops.size());
            return shops;
        }
        catch (Exception e)
        {
            log.error("Error loading shop data", e);
            return Collections.emptyList();
        }
    }
}