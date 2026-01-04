package com.margeon.shoparbitrage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads slayer monster data from JSON file
 *
 * Data sources:
 * - OSRS Wiki drop tables
 * - Kill rates from community averages
 * - High alch values from game data
 *
 * JSON format allows easy updates without recompiling!
 */
public class SlayerDataLoader
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SlayerDataLoader.class);
    private static final Gson gson = new Gson();

    // JSON file location (in plugin directory)
    private static final File MONSTERS_FILE = new File(RuneLite.RUNELITE_DIR, "slayer-monsters.json");

    /**
     * Load all slayer monsters from JSON file
     */
    public static List<SlayerMonster> loadMonsters()
    {
        List<SlayerMonster> monsters = new ArrayList<>();

        try
        {
            Reader reader;

            // Try to load from user's RuneLite directory first
            if (MONSTERS_FILE.exists())
            {
                log.info("Loading slayer monsters from: {}", MONSTERS_FILE.getAbsolutePath());
                reader = new FileReader(MONSTERS_FILE);
            }
            else
            {
                // Fall back to embedded resource
                log.info("Loading slayer monsters from embedded resource");
                InputStream is = SlayerDataLoader.class.getResourceAsStream("/slayer-monsters.json");
                if (is == null)
                {
                    log.error("Could not find slayer-monsters.json resource");
                    return loadDefaultMonsters();
                }
                reader = new InputStreamReader(is);
            }

            JsonObject root = gson.fromJson(reader, JsonObject.class);
            JsonArray monstersArray = root.getAsJsonArray("monsters");

            for (JsonElement element : monstersArray)
            {
                JsonObject monsterObj = element.getAsJsonObject();

                String name = monsterObj.get("name").getAsString();
                int combatLevel = monsterObj.get("combatLevel").getAsInt();
                int slayerLevel = monsterObj.get("slayerLevel").getAsInt();
                int killsPerHour = monsterObj.get("killsPerHour").getAsInt();
                boolean isSlayerTask = monsterObj.get("isSlayerTask").getAsBoolean();

                SlayerMonster monster = new SlayerMonster(name, combatLevel, slayerLevel, killsPerHour, isSlayerTask);

                // Load drops
                JsonArray drops = monsterObj.getAsJsonArray("drops");
                for (JsonElement dropElement : drops)
                {
                    JsonObject dropObj = dropElement.getAsJsonObject();

                    int itemId = dropObj.get("itemId").getAsInt();
                    String itemName = dropObj.get("itemName").getAsString();
                    int minQty = dropObj.get("minQuantity").getAsInt();
                    int maxQty = dropObj.get("maxQuantity").getAsInt();
                    double dropRate = dropObj.get("dropRate").getAsDouble();
                    boolean isAlchable = dropObj.get("isAlchable").getAsBoolean();
                    int alchValue = dropObj.get("alchValue").getAsInt();

                    monster.addDrop(new SlayerMonster.Drop(itemId, itemName, minQty, maxQty,
                            dropRate, isAlchable, alchValue));
                }

                monsters.add(monster);
            }

            reader.close();
            log.info("Successfully loaded {} slayer monsters from JSON", monsters.size());
        }
        catch (Exception e)
        {
            log.error("Error loading slayer monsters from JSON, using defaults", e);
            return loadDefaultMonsters();
        }

        return monsters;
    }

    /**
     * Fallback: Load a minimal set of monsters if JSON fails
     */
    private static List<SlayerMonster> loadDefaultMonsters()
    {
        List<SlayerMonster> monsters = new ArrayList<>();

        log.warn("Using minimal default monster set");

        // Just add a few basic monsters as fallback
        SlayerMonster gargoyles = new SlayerMonster("Gargoyle", 111, 75, 180, true);
        gargoyles.addDrop(new SlayerMonster.Drop(1185, "Rune med helm", 1, 1, 0.023, true, 11520));
        gargoyles.addDrop(new SlayerMonster.Drop(1201, "Rune kiteshield", 1, 1, 0.016, true, 32640));
        monsters.add(gargoyles);

        SlayerMonster abyssalDemons = new SlayerMonster("Abyssal Demon", 124, 85, 150, true);
        abyssalDemons.addDrop(new SlayerMonster.Drop(4151, "Abyssal whip", 1, 1, 0.002, false, 72000));
        abyssalDemons.addDrop(new SlayerMonster.Drop(1185, "Rune med helm", 1, 1, 0.016, true, 11520));
        monsters.add(abyssalDemons);

        return monsters;
    }
}