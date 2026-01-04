package com.margeon.shoparbitrage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiDropFetcher {
    private static final String WIKI_API_URL = "https://oldschool.runescape.wiki/api.php?action=parse&prop=wikitext&format=json&page=";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // List of monsters you want to update/fetch
    private static final String[] MONSTERS_TO_FETCH = {
            "Abyssal Demon", "Dark Beast", "Cerberus", "Kraken", "Thermonuclear Smoke Devil"
    };

    public static void main(String[] args) {
        List<MonsterData> monsters = new ArrayList<>();

        for (String monsterName : MONSTERS_TO_FETCH) {
            System.out.println("Fetching: " + monsterName);
            MonsterData data = fetchMonsterFromWiki(monsterName);
            if (data != null) {
                monsters.add(data);
            }
        }

        System.out.println(gson.toJson(monsters));
    }

    private static MonsterData fetchMonsterFromWiki(String name) {
        String jsonResponse = makeRequest(WIKI_API_URL + name.replace(" ", "_"));
        if (jsonResponse == null) return null;

        MonsterData monster = new MonsterData();
        monster.name = name;
        monster.isSlayerTask = true; // Default assumption, edit manually if needed
        monster.killsPerHour = 0;    // Subjective data, cannot fetch from Wiki

        // 1. Extract Wikitext from JSON response
        JsonObject json = gson.fromJson(jsonResponse, JsonObject.class);
        if (!json.has("parse")) return null;
        String wikitext = json.getAsJsonObject("parse").getAsJsonObject("wikitext").get("*").getAsString();

        // 2. Parse Drop Table (Regex for {{DropsLine...}})
        // This regex looks for Name, Quantity, and Rarity templates
        Pattern dropPattern = Pattern.compile("\\{\\{DropsLine\\|Name=(.*?)\\|.*?Quantity=(.*?)\\|.*?Rarity=(.*?)\\|", Pattern.CASE_INSENSITIVE);
        Matcher matcher = dropPattern.matcher(wikitext);

        while (matcher.find()) {
            Drop drop = new Drop();
            drop.itemName = matcher.group(1).trim();
            String quantity = matcher.group(2).trim();
            String rarity = matcher.group(3).trim();

            // Simple parsing for quantity (e.g., "1-3" becomes min 1 max 3)
            if (quantity.contains("-")) {
                String[] parts = quantity.split("-");
                drop.minQuantity = tryParseInt(parts[0]);
                drop.maxQuantity = tryParseInt(parts[1]);
            } else {
                drop.minQuantity = tryParseInt(quantity);
                drop.maxQuantity = drop.minQuantity;
            }

            drop.dropRate = parseRarity(rarity);
            monster.drops.add(drop);
        }
        return monster;
    }

    private static double parseRarity(String rarity) {
        // Convert wiki rarity strings to double
        rarity = rarity.toLowerCase();
        if (rarity.contains("always")) return 1.0;
        if (rarity.contains("common")) return 1.0 / 20.0;
        if (rarity.contains("uncommon")) return 1.0 / 50.0;
        if (rarity.contains("rare")) return 1.0 / 128.0;
        if (rarity.contains("very rare")) return 1.0 / 512.0;

        // Handle explicit fractions (e.g. "1/128")
        if (rarity.contains("/")) {
            String[] parts = rarity.split("/");
            try {
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            } catch (Exception e) { return 0.0; }
        }
        return 0.0;
    }

    private static int tryParseInt(String val) {
        try { return Integer.parseInt(val.replaceAll("[^0-9]", "")); } catch (Exception e) { return 1; }
    }

    // Simple helper for HTTP requests
    private static String makeRequest(String url) {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            return response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // POJOs for GSON
    static class MonsterData {
        String name;
        int combatLevel; // You'd need a separate regex to fetch this from {{Infobox Monster}}
        int slayerLevel;
        int killsPerHour;
        boolean isSlayerTask;
        List<Drop> drops = new ArrayList<>();
    }
    static class Drop {
        int itemId; // Requires mapping Name -> ID (e.g., via ItemManager in RuneLite)
        String itemName;
        int minQuantity;
        int maxQuantity;
        double dropRate;
        boolean isAlchable;
        int alchValue;
    }
}