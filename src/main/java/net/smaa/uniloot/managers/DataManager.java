package net.smaa.uniloot.managers;

import net.smaa.uniloot.UniLoot;
import net.smaa.uniloot.managers.SQLiteManager;
import net.smaa.uniloot.utils.PlayerLootRecord;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    public long getRemainingCooldown(long lastLootTime) {
        if (lastLootTime == 0) {
            return 0;
        }
        long cooldownMillis = configManager.getRefreshIntervalMillis();
        long elapsedTime = System.currentTimeMillis() - lastLootTime;
        return Math.max(0, cooldownMillis - elapsedTime);
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
}

