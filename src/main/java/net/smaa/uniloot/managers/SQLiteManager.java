package net.smaa.uniloot.managers;

import net.smaa.uniloot.UniLoot;
import net.smaa.uniloot.utils.PlayerLootRecord;
import net.smaa.uniloot.utils.LocationUtil;
import net.smaa.uniloot.utils.SerializationUtil;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class SQLiteManager {

    private final UniLoot plugin;
    private Connection connection;

    public SQLiteManager(UniLoot plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "uniloot.db");
        try {
            if (!dbFile.exists()) {
                dbFile.createNewFile();
            }
            String url = "jdbc:sqlite:" + dbFile.getPath();
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Successfully connected to the SQLite database.");
            initializeDatabase();
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().severe("!! FAILED TO CONNECT TO THE SQLITE DATABASE !!");
            plugin.getLogger().severe("!! This is a critical error. The plugin will not function. !!");
            plugin.getLogger().severe("!! Error: " + e.getMessage());
            plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error while disconnecting from the SQLite database: " + e.getMessage());
        }
    }

    private void initializeDatabase() {
        String createPlayerDataTable = "CREATE TABLE IF NOT EXISTS player_data (" +
                "location_key TEXT NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "timestamp BIGINT NOT NULL," +
                "contents TEXT," +
                "PRIMARY KEY (location_key, player_uuid)" +
                ");";

        String createCapturedLootTable = "CREATE TABLE IF NOT EXISTS captured_loot (" +
                "location_key TEXT PRIMARY KEY," +
                "contents TEXT NOT NULL" +
                ");";

        String createPlayerPlacedTable = "CREATE TABLE IF NOT EXISTS player_placed_blocks (" +
                "location_key TEXT PRIMARY KEY" +
                ");";

        String createElytraDataTable = "CREATE TABLE IF NOT EXISTS elytra_data (" +
                "player_uuid TEXT PRIMARY KEY," +
                "timestamp BIGINT NOT NULL" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayerDataTable);
            stmt.execute(createCapturedLootTable);
            stmt.execute(createPlayerPlacedTable);
            stmt.execute(createElytraDataTable);
            plugin.getLogger().info("Database tables verified and ready.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create database tables: " + e.getMessage());
        }
    }

    public PlayerLootRecord getPlayerRecord(Location location, UUID playerUUID) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "SELECT timestamp, contents FROM player_data WHERE location_key = ? AND player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            pstmt.setString(2, playerUUID.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String contentsBase64 = rs.getString("contents");
                ItemStack[] contents = SerializationUtil.itemStackArrayFromBase64(contentsBase64);
                return new PlayerLootRecord(timestamp, contents);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("Database error getting player record: " + e.getMessage());
        }
        return null;
    }

    public void setPlayerRecord(Location location, UUID playerUUID, PlayerLootRecord record) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "INSERT OR REPLACE INTO player_data (location_key, player_uuid, timestamp, contents) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            pstmt.setString(2, playerUUID.toString());
            pstmt.setLong(3, record.getTimestamp());
            pstmt.setString(4, SerializationUtil.itemStackArrayToBase64(record.getContents()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error setting player record: " + e.getMessage());
        }
    }

    public void updatePlayerRecordContents(Location location, UUID playerUUID, ItemStack[] contents) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "UPDATE player_data SET contents = ? WHERE location_key = ? AND player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, SerializationUtil.itemStackArrayToBase64(contents));
            pstmt.setString(2, locationKey);
            pstmt.setString(3, playerUUID.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error updating player record contents: " + e.getMessage());
        }
    }

    public boolean hasCapturedLoot(Location location) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "SELECT 1 FROM captured_loot WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error checking for captured loot: " + e.getMessage());
            return false;
        }
    }

    public void captureLoot(Location location, ItemStack[] items) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "INSERT OR IGNORE INTO captured_loot (location_key, contents) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            pstmt.setString(2, SerializationUtil.itemStackArrayToBase64(items));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error capturing loot: " + e.getMessage());
        }
    }

    public ItemStack[] getCapturedLoot(Location location) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "SELECT contents FROM captured_loot WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return SerializationUtil.itemStackArrayFromBase64(rs.getString("contents"));
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("Database error getting captured loot: " + e.getMessage());
        }
        return null;
    }

    public void addPlayerPlaced(Location location) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "INSERT OR IGNORE INTO player_placed_blocks (location_key) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error adding player placed block: " + e.getMessage());
        }
    }

    public void removePlayerPlaced(Location location) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "DELETE FROM player_placed_blocks WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error removing player placed block: " + e.getMessage());
        }
    }

    public boolean isPlayerPlaced(Location location) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String sql = "SELECT 1 FROM player_placed_blocks WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error checking if block is player placed: " + e.getMessage());
            return false;
        }
    }

    public void clearAllDataForLocation(Location location) {
        String locationKey = LocationUtil.locationToString(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String deletePlayerDataSQL = "DELETE FROM player_data WHERE location_key = ?";
        String deleteCapturedLootSQL = "DELETE FROM captured_loot WHERE location_key = ?";
        String deletePlayerPlacedSQL = "DELETE FROM player_placed_blocks WHERE location_key = ?";

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement pstmt1 = connection.prepareStatement(deletePlayerDataSQL);
                 PreparedStatement pstmt2 = connection.prepareStatement(deleteCapturedLootSQL);
                 PreparedStatement pstmt3 = connection.prepareStatement(deletePlayerPlacedSQL)) {

                pstmt1.setString(1, locationKey);
                pstmt1.executeUpdate();

                pstmt2.setString(1, locationKey);
                pstmt2.executeUpdate();

                pstmt3.setString(1, locationKey);
                pstmt3.executeUpdate();

            }
            
            String sql = "DELETE FROM elytra_data WHERE player_uuid IN (SELECT player_uuid FROM player_data WHERE location_key = ?)";
            try (PreparedStatement pstmt4 = connection.prepareStatement(sql)) {
                pstmt4.setString(1, locationKey);
                pstmt4.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error clearing all data for location: " + e.getMessage());
            try {
                connection.rollback();
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to rollback transaction: " + ex.getMessage());
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to restore auto-commit: " + e.getMessage());
            }
        }
    }

    public boolean hasPlayerObtainedElytra(UUID playerUUID) {
        String sql = "SELECT 1 FROM elytra_data WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error checking if player has obtained elytra: " + e.getMessage());
            return false;
        }
    }

    public void setPlayerObtainedElytra(UUID playerUUID) {
        String sql = "INSERT OR IGNORE INTO elytra_data (player_uuid, timestamp) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error setting player obtained elytra: " + e.getMessage());
        }
    }

    public void resetPlayerElytraTimer(UUID playerUUID) {
        String sql = "UPDATE elytra_data SET timestamp = ? WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setString(2, playerUUID.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error resetting player elytra timer: " + e.getMessage());
        }
    }
}
