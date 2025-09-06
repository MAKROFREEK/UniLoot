package net.smaa.uniloot.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;

public class LocationUtil {

    /**
     * For double chests, gets the location of the primary (left) side.
     * For single containers, returns its own location.
     * This ensures both halves of a double chest share the same loot data.
     * @param block The block that was interacted with.
     * @return The primary location for data storage.
     */
    public static Location getPrimaryLocation(Block block) {
        if (block.getState() instanceof Chest) {
            InventoryHolder holder = ((Chest) block.getState()).getInventory().getHolder();
            if (holder instanceof DoubleChest) {
                return ((DoubleChest) holder).getLocation();
            }
        }
        return block.getLocation();
    }

    /**
     * Converts a location into a standardized string format for use as a key in data files.
     * @param location The location to convert.
     * @return A string representation of the location.
     */
    public static String locationToString(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }
}
