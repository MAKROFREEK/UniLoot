package net.smaa.uniloot;

import net.smaa.uniloot.commands.UniLootCommand;
import net.smaa.uniloot.managers.SQLiteManager;
import net.smaa.uniloot.managers.ConfigManager;
import net.smaa.uniloot.managers.DataManager;
import net.smaa.uniloot.managers.LootManager;
import net.smaa.uniloot.managers.ProtectionManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class UniLoot extends JavaPlugin {

    private ConfigManager configManager;
    private DataManager dataManager;
    private SQLiteManager sqliteManager;

    @Override
    public void onEnable() {
        try {
            getLogger().info("----------------------------------------------------");
            getLogger().info("   _    _ _   _ _ _   _  ");
            getLogger().info("  | |  | | | (_) | | | | ");
            getLogger().info("  | |  | | |_ _| | | | | ");
            getLogger().info("  | |  | | __| | | | | | ");
            getLogger().info("  | |__| | |_| | | | | |____");
            getLogger().info("   \\____/ \\__|_|_|_| |______|");
            getLogger().info(" ");

            getLogger().info("STEP 1: Initializing ConfigManager...");
            configManager = new ConfigManager(this);

            getLogger().info("STEP 2: Initializing SQLiteManager...");
            sqliteManager = new SQLiteManager(this);

            getLogger().info("STEP 3: Initializing DataManager...");
            dataManager = new DataManager(this, configManager, sqliteManager);

            getLogger().info("STEP 4: Loading configuration from config.yml...");
            configManager.loadConfig();

            getLogger().info("STEP 5: Connecting to the database...");
            sqliteManager.connect();

            getLogger().info("STEP 6: Registering LootManager listener...");
            getServer().getPluginManager().registerEvents(new LootManager(this, configManager, dataManager), this);

            getLogger().info("STEP 7: Registering ProtectionManager listener...");
            getServer().getPluginManager().registerEvents(new ProtectionManager(this, configManager, dataManager), this);

            getLogger().info("STEP 8: Getting '/uniloot' command from server...");
            PluginCommand pluginCommand = getCommand("uniloot");

            getLogger().info("STEP 9: Setting up command executor and tab completer...");
            if (pluginCommand != null) {
                UniLootCommand commandExecutor = new UniLootCommand(this);
                pluginCommand.setExecutor(commandExecutor);
                pluginCommand.setTabCompleter(commandExecutor);
            } else {
                getLogger().severe("Failed to register '/uniloot' command! Make sure it is defined in your plugin.yml.");
            }

            getLogger().info("            UniLoot has been enabled successfully!");
            getLogger().info("----------------------------------------------------");

        } catch (Throwable t) { // Use Throwable to catch all possible errors
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().severe("!! A CRITICAL ERROR OCCURRED DURING UNILOOT STARTUP !!");
            getLogger().severe("!! The plugin has been disabled to prevent world corruption. !!");
            getLogger().severe("!! Please check the stack trace below for the exact cause. !!");
            t.printStackTrace();
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (sqliteManager != null) {
            sqliteManager.disconnect();
        }
        getLogger().info("UniLoot has been disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}

