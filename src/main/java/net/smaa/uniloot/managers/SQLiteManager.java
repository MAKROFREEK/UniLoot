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
    private final String dbName = "uniloot.db";

    public SQLiteManager(UniLoot plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException, IOException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, dbName);
        if (!dbFile.exists()) {
            dbFile.createNewFile();
        }
        String url = "jdbc:sqlite:" + dbFile.getPath();
        connection = DriverManager.getConnection(url);
        plugin.getLogger().info("Successfully connected to the SQLite database.");
        initializeDatabase();
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from the SQLite database.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error while disconnecting from the SQLite database:");
            e.printStackTrace();
        }
    }

    private void initializeDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS player_loot_records (" +
                    "location_key TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "timestamp BIGINT NOT NULL," +
                    "inventory_contents TEXT," +
                    "PRIMARY KEY (location_key, player_uuid));");

            statement.execute("CREATE TABLE IF NOT EXISTS captured_loot_templates (" +
                    "location_key TEXT PRIMARY KEY," +
                    "inventory_contents TEXT NOT NULL);");

            statement.execute("CREATE TABLE IF NOT EXISTS player_placed_blocks (" +
                    "location_key TEXT PRIMARY KEY);");

            plugin.getLogger().info("Database tables verified and ready.");
        }
    }

    public PlayerLootRecord getPlayerRecord(Location location, UUID playerUUID) {
        String locationKey = LocationUtil.locationToString(location);
        String sql = "SELECT timestamp, inventory_contents FROM player_loot_records WHERE location_key = ? AND player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, locationKey);
            pstmt.setString(2, playerUUID.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                String encodedContents = rs.getString("inventory_contents");
                ItemStack[] contents = SerializationUtil.itemStackArrayFromBase64(encodedContents);
                return new PlayerLootRecord(timestamp, contents);
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("Failed to get player record for " + playerUUID + " at " + locationKey);
            e.printStackTrace();
        }
        return null;
    }

    public void setPlayerRecord(Location location, UUID playerUUID, PlayerLootRecord record) {
        String locationKey = LocationUtil.locationToString(location);
        String sql = "INSERT OR REPLACE INTO player_loot_records (location_key, player_uuid, timestamp, inventory_contents) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            String encodedContents = SerializationUtil.itemStackArrayToBase64(record.getContents());
            pstmt.setString(1, locationKey);
            pstmt.setString(2, playerUUID.toString());
            pstmt.setLong(3, record.getTimestamp());
            pstmt.setString(4, encodedContents);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set player record for " + playerUUID + " at " + locationKey);
            e.printStackTrace();
        }
    }

    public void addPlayerPlaced(Location location) {
        String sql = "INSERT OR IGNORE INTO player_placed_blocks (location_key) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocationUtil.locationToString(location));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removePlayerPlaced(Location location) {
        String sql = "DELETE FROM player_placed_blocks WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocationUtil.locationToString(location));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isPlayerPlaced(Location location) {
        String sql = "SELECT 1 FROM player_placed_blocks WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocationUtil.locationToString(location));
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void captureLoot(Location location, ItemStack[] contents) {
        String sql = "INSERT OR IGNORE INTO captured_loot_templates (location_key, inventory_contents) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocationUtil.locationToString(location));
            pstmt.setString(2, SerializationUtil.itemStackArrayToBase64(contents));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ItemStack[] getCapturedLoot(Location location) {
         String sql = "SELECT inventory_contents FROM captured_loot_templates WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocationUtil.locationToString(location));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return SerializationUtil.itemStackArrayFromBase64(rs.getString("inventory_contents"));
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean hasCapturedLoot(Location location) {
        String sql = "SELECT 1 FROM captured_loot_templates WHERE location_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocationUtil.locationToString(location));
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void clearAllDataForLocation(Location location) {
        String locationKey = LocationUtil.locationToString(location);
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement pstmt1 = connection.prepareStatement("DELETE FROM player_loot_records WHERE location_key = ?");
                 PreparedStatement pstmt2 = connection.prepareStatement("DELETE FROM captured_loot_templates WHERE location_key = ?");
                 PreparedStatement pstmt3 = connection.prepareStatement("DELETE FROM player_placed_blocks WHERE location_key = ?")) {

                pstmt1.setString(1, locationKey);
                pstmt1.executeUpdate();

                pstmt2.setString(1, locationKey);
                pstmt2.executeUpdate();

                pstmt3.setString(1, locationKey);
                pstmt3.executeUpdate();

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear all data for location: " + locationKey);
            e.printStackTrace();
        }
    }
}

