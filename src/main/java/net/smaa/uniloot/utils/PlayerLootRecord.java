package net.smaa.uniloot.utils;

import org.bukkit.inventory.ItemStack;

public class PlayerLootRecord {
    private final long timestamp;
    private ItemStack[] contents;

    public PlayerLootRecord(long timestamp, ItemStack[] contents) {
        this.timestamp = timestamp;
        this.contents = contents;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ItemStack[] getContents() {
        // Return a copy to prevent external modification of the original array
        if (contents == null) return null;
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                copy[i] = contents[i].clone();
            }
        }
        return copy;
    }

    public void setContents(ItemStack[] contents) {
        // Save a copy to prevent external modification of the original array
        if (contents == null) {
            this.contents = null;
            return;
        }
        this.contents = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                this.contents[i] = contents[i].clone();
            }
        }
    }
}

