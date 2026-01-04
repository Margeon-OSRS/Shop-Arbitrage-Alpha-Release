package com.margeon.shoparbitrage;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DevLauncher
{
    public static void main(String[] args) throws Exception
    {
        // 1. debug "Scream" - If you see this in the logs, we know Gradle is working
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.out.println("!!! DEV LAUNCHER IS RUNNING - FORCE LOADING !!!");
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        // 2. Force the client to load your specific plugin class
        // This bypasses the invisible META-INF files entirely.
        ExternalPluginManager.loadBuiltin(ShopArbitragePlugin.class);

        // 3. Start the actual RuneLite client
        RuneLite.main(args);
    }
}