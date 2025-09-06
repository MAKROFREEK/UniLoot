package net.smaa.uniloot.utils;

import org.bukkit.Material;

public class FallbackLootItem {

    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final int weight;

    public FallbackLootItem(Material material, int minAmount, int maxAmount, int weight) {
        this.material = material;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.weight = weight;
    }

    public Material getMaterial() {
        return material;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public int getWeight() {
        return weight;
    }
}
