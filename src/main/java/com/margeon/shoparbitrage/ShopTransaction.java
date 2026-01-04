package com.margeon.shoparbitrage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShopTransaction
{
    private final String itemName;
    private final int itemId;       // The ID used to look up GE price (e.g., 556 for Air Rune)
    private final int shopBuyPrice; // How much the shop charges you
    private final int quantity;     // How many you can buy
}