package net.smaa.uniloot.managers;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class OpenInventoryData {
    private final ItemStack[] initialContents;
    private final Location location;

    public OpenInventoryData(ItemStack[] initialContents, Location location) {
        this.initialContents = initialContents;
        this.location = location;
    }

    public ItemStack[] getInitialContents() {
        return initialContents;
    }

    public Location getLocation() {
        return location;
    }
}