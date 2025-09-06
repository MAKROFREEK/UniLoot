package net.smaa.uniloot.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.smaa.uniloot.UniLoot;
import net.smaa.uniloot.utils.FallbackLootItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {

    private final UniLoot plugin;
    private FileConfiguration config;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([hms])");


    // --- Cached Config Values ---
    private boolean debugMode;
    private Component inventoryTitle;
    private final Set<Material> enabledContainerTypes = new HashSet<>();
    private boolean protectionEnabled;
    private boolean creativeBreakAllowed;
    private int creativeBreakConfirmationSeconds;
    private boolean refreshEnabled;
    private long refreshIntervalMillis;
    private String firstLootMessage;
    private String alreadyLootedMessage;
    private String lootOnCooldownMessage;
    private String breakAttemptSurvivalMessage;
    private String breakWarningCreativeMessage;
    private String placeNextToLootChestMessage; // New message
    private boolean fallbackLootEnabled;
    private int fallbackItemsToGive;
    private final List<FallbackLootItem> fallbackItems = new ArrayList<>();
    private int totalFallbackWeight;

    public ConfigManager(UniLoot plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load general settings
        debugMode = config.getBoolean("general.debug", false);
        inventoryTitle = MiniMessage.miniMessage().deserialize(config.getString("general.inventory_title", "<gray>Loot</gray>"));

        enabledContainerTypes.clear();
        config.getStringList("general.enabled_containers").forEach(name -> {
            try {
                enabledContainerTypes.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid container type in config.yml: " + name);
            }
        });

        // Load protection settings
        protectionEnabled = config.getBoolean("protection.enabled", true);
        creativeBreakAllowed = config.getBoolean("protection.allow_creative_break", true);
        creativeBreakConfirmationSeconds = config.getInt("protection.creative_break_confirmation_seconds", 10);

        // Load loot refresh settings
        refreshEnabled = config.getBoolean("loot_refresh.enabled", true);
        refreshIntervalMillis = parseTime(config.getString("loot_refresh.refresh_interval", "24h"));


        // Load messages as raw strings
        firstLootMessage = config.getString("messages.first_loot", "<green>You found some loot!</green>");
        alreadyLootedMessage = config.getString("messages.already_looted", "<red>You have already looted this container.</red>");
        lootOnCooldownMessage = config.getString("messages.loot_on_cooldown", "<gray>You can loot this again in <hours>h <minutes>m <seconds>s. You can still access your items.</gray>");
        breakAttemptSurvivalMessage = config.getString("messages.break_attempt_survival", "<red>This special container is protected and cannot be broken.</red>");
        breakWarningCreativeMessage = config.getString("messages.break_warning_creative", "<yellow><bold>Warning!</bold> This is a protected loot container. Break it again within <time> seconds to permanently destroy it.</yellow>");
        placeNextToLootChestMessage = config.getString("messages.place_next_to_loot_chest", "<red>You cannot place a chest next to a special loot container.</red>");


        // Load fallback loot settings
        fallbackLootEnabled = config.getBoolean("fallback_loot.enabled", true);
        fallbackItemsToGive = config.getInt("fallback_loot.items_to_give", 3);

        fallbackItems.clear();
        totalFallbackWeight = 0;
        List<String> rawItems = config.getStringList("fallback_loot.items");
        for (String rawItem : rawItems) {
            try {
                String[] parts = rawItem.split(",");
                if (parts.length != 3) {
                    plugin.getLogger().warning("Invalid fallback item format: " + rawItem);
                    continue;
                }

                Material material = Material.matchMaterial(parts[0].trim());
                if (material == null) {
                    plugin.getLogger().warning("Invalid material in fallback item: " + parts[0].trim());
                    continue;
                }

                String[] amountParts = parts[1].trim().split("-");
                int minAmount = Integer.parseInt(amountParts[0]);
                int maxAmount = amountParts.length > 1 ? Integer.parseInt(amountParts[1]) : minAmount;

                int weight = Integer.parseInt(parts[2].trim());
                if (weight <= 0) {
                    plugin.getLogger().warning("Fallback item weight must be positive: " + rawItem);
                    continue;
                }

                fallbackItems.add(new FallbackLootItem(material, minAmount, maxAmount, weight));
                totalFallbackWeight += weight;

            } catch (Exception e) {
                plugin.getLogger().severe("Could not parse fallback item: " + rawItem);
                e.printStackTrace();
            }
        }
    }

    private long parseTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            plugin.getLogger().warning("Refresh interval is not set. Defaulting to 24h.");
            return TimeUnit.HOURS.toMillis(24);
        }
        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        if (matcher.matches()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "h":
                    return TimeUnit.HOURS.toMillis(value);
                case "m":
                    return TimeUnit.MINUTES.toMillis(value);
                case "s":
                    return TimeUnit.SECONDS.toMillis(value);
            }
        }
        plugin.getLogger().warning("Invalid time format '" + timeString + "'. Defaulting to 24h.");
        return TimeUnit.HOURS.toMillis(24);
    }


    // --- Getters for cached values ---
    public boolean isDebugMode() { return debugMode; }
    public Component getInventoryTitle() { return inventoryTitle; }
    public Set<Material> getEnabledContainerTypes() { return enabledContainerTypes; }
    public boolean isProtectionEnabled() { return protectionEnabled; }
    public boolean isCreativeBreakAllowed() { return creativeBreakAllowed; }
    public int getCreativeBreakConfirmationSeconds() { return creativeBreakConfirmationSeconds; }
    public boolean isRefreshEnabled() { return refreshEnabled; }
    public long getRefreshIntervalMillis() { return refreshIntervalMillis; }
    public String getFirstLootMessage() { return firstLootMessage; }
    public String getAlreadyLootedMessage() { return alreadyLootedMessage; }
    public String getLootOnCooldownMessage() { return lootOnCooldownMessage; }
    public String getBreakAttemptSurvivalMessage() { return breakAttemptSurvivalMessage; }
    public String getBreakWarningCreativeMessage() { return breakWarningCreativeMessage; }
    public String getPlaceNextToLootChestMessage() { return placeNextToLootChestMessage; }
    public boolean isFallbackLootEnabled() { return fallbackLootEnabled; }
    public int getFallbackItemsToGive() { return fallbackItemsToGive; }
    public List<FallbackLootItem> getFallbackItems() { return fallbackItems; }
    public int getTotalFallbackWeight() { return totalFallbackWeight; }
}

