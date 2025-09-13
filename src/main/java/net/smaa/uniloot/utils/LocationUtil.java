package net.smaa.uniloot.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;

public class LocationUtil {

    /**
     * For all containers, returns its own location.
     * @param block The block that was interacted with.
     * @return The location for data storage.
     */
    public static Location getPrimaryLocation(Block block) {
        return block.getLocation();
    }

    /**
     * Converts a location into a standardized string format for use as a key in data files.
     * @param worldName The name of the world.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @return A string representation of the location.
     */
    public static String locationToString(String worldName, int x, int y, int z) {
        return worldName + ";" + x + ";" + y + ";" + z;
    }
}
