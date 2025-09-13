package net.smaa.uniloot.managers;

import net.smaa.uniloot.UniLoot;
import net.smaa.uniloot.managers.SQLiteManager;
import net.smaa.uniloot.utils.PlayerLootRecord;
import org.bukkit.Location;
import net.smaa.uniloot.utils.LocationUtil;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DataManager {

    private final UniLoot plugin;
    private final ConfigManager configManager;
    private final SQLiteManager sqliteManager;

    public DataManager(UniLoot plugin, ConfigManager configManager, SQLiteManager sqliteManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.sqliteManager = sqliteManager;
    }

    // --- Player Loot Record Methods ---

    public PlayerLootRecord getPlayerRecord(Location location, UUID playerUUID) {
        return sqliteManager.getPlayerRecord(location, playerUUID);
    }

    public void setPlayerRecord(Location location, UUID playerUUID, PlayerLootRecord record) {
        sqliteManager.setPlayerRecord(location, playerUUID, record);
    }

    /**
     * Updates only the contents of an existing player loot record, preserving the original timestamp.
     * @param location The location of the container.
     * @param playerUUID The UUID of the player.
     * @param contents The new contents to save.
     */
    public void updatePlayerRecordContents(Location location, UUID playerUUID, ItemStack[] contents) {
        sqliteManager.updatePlayerRecordContents(location, playerUUID, contents);
    }


    // --- Captured Loot Template Methods ---

    public boolean hasCapturedLoot(Location location) {
        return sqliteManager.hasCapturedLoot(location);
    }

    public void captureLoot(Location location, ItemStack[] items) {
        sqliteManager.captureLoot(location, items);
    }

    public ItemStack[] getCapturedLoot(Location location) {
        return sqliteManager.getCapturedLoot(location);
    }

    // --- Player Placed Block Methods ---

    public void addPlayerPlaced(Location location) {
        sqliteManager.addPlayerPlaced(location);
    }

    public void removePlayerPlaced(Location location) {
        sqliteManager.removePlayerPlaced(location);
    }

    public boolean isPlayerPlaced(Location location) {
        return sqliteManager.isPlayerPlaced(location);
    }

    // --- Data Clearing ---

    public void clearAllDataForLocation(Location location) {
        sqliteManager.clearAllDataForLocation(location);
    }

    public boolean hasPlayerObtainedElytra(UUID playerUUID) {
        return sqliteManager.hasPlayerObtainedElytra(playerUUID);
    }

    public void setPlayerObtainedElytra(UUID playerUUID) {
        sqliteManager.setPlayerObtainedElytra(playerUUID);
    }

    public void resetPlayerElytraTimer(UUID playerUUID) {
        sqliteManager.resetPlayerElytraTimer(playerUUID);
    }
}
