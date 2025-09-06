package net.smaa.uniloot.managers;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.smaa.uniloot.utils.FallbackLootItem;
import net.smaa.uniloot.utils.PlayerLootRecord;
import net.smaa.uniloot.utils.LocationUtil;
import net.smaa.uniloot.UniLoot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LootManager implements Listener {

    private final UniLoot plugin;
    private final ConfigManager config;
    private final DataManager data;
    private final Random random = new Random();

    // Tracks which player is currently viewing which loot container inventory
    // This is crucial for saving the correct inventory contents when it's closed.
    private final Map<UUID, Location> openLootInventories = new HashMap<>();

    public LootManager(UniLoot plugin, ConfigManager configManager, DataManager dataManager) {
        this.plugin = plugin;
        this.config = configManager;
        this.data = dataManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !config.getEnabledContainerTypes().contains(block.getType())) return;

        Location primaryLocation = LocationUtil.getPrimaryLocation(block);
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Immediately ignore any blocks marked as player-placed to protect storage.
        if (data.isPlayerPlaced(primaryLocation)) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof Container)) return;

        PlayerLootRecord record = data.getPlayerRecord(primaryLocation, playerUUID);
        long lastLootTime = (record != null) ? record.getTimestamp() : 0;
        long remainingCooldown = data.getRemainingCooldown(lastLootTime);

        // CASE 1: Player has a record and is on cooldown -> Show them their saved loot.
        if (config.isRefreshEnabled() && remainingCooldown > 0) {
            event.setCancelled(true);

            // Send the cooldown message before opening the inventory.
            String messageTemplate = config.getLootOnCooldownMessage();
            if (!messageTemplate.isEmpty()) {
                long hours = TimeUnit.MILLISECONDS.toHours(remainingCooldown);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingCooldown) % 60;
                long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingCooldown) % 60;

                player.sendMessage(MiniMessage.miniMessage().deserialize(messageTemplate,
                        Placeholder.unparsed("hours", String.valueOf(hours)),
                        Placeholder.unparsed("minutes", String.valueOf(minutes)),
                        Placeholder.unparsed("seconds", String.valueOf(seconds))
                ));
            }

            openSavedPlayerLoot(player, record, (Container) state, primaryLocation);
            return;
        }

        // CASE 2: Player has a permanent record (refresh disabled) -> Show them their saved loot.
        if (!config.isRefreshEnabled() && record != null) {
            event.setCancelled(true);
            openSavedPlayerLoot(player, record, (Container) state, primaryLocation);
            return;
        }

        // CASE 3: Cooldown expired or it's their first time looting -> Generate new loot.
        event.setCancelled(true);
        generateNewLoot(player, (Container) state, primaryLocation);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // If the player who closed the inventory was viewing one of our loot containers...
        if (openLootInventories.containsKey(playerUUID)) {
            Location lootedLocation = openLootInventories.get(playerUUID);
            PlayerLootRecord record = data.getPlayerRecord(lootedLocation, playerUUID);

            // This check is crucial. We only save if the inventory title matches.
            // It ensures we don't accidentally overwrite loot data when a player closes a different GUI.
            if (record != null && event.getView().title().equals(config.getInventoryTitle())) {
                // Save the player's modified inventory back to their record.
                record.setContents(event.getInventory().getContents());
                data.setPlayerRecord(lootedLocation, playerUUID, record);
            }
            openLootInventories.remove(playerUUID);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up the map if a player logs out while a loot inventory is open.
        openLootInventories.remove(event.getPlayer().getUniqueId());
    }

    private void openSavedPlayerLoot(Player player, PlayerLootRecord record, Container container, Location location) {
        Inventory lootInventory = Bukkit.createInventory(null, container.getInventory().getSize(), config.getInventoryTitle());
        if (record.getContents() != null) {
            lootInventory.setContents(record.getContents());
        }
        player.openInventory(lootInventory);
        // Track that this player is now viewing this specific loot inventory.
        openLootInventories.put(player.getUniqueId(), location);
    }

    private void generateNewLoot(Player player, Container container, Location location) {
        BlockState state = container.getBlock().getState();
        ItemStack[] newLootContents;

        // PATH 1: Standard Loot Table (Vanilla, most plugins)
        if (state instanceof Lootable lootable && lootable.getLootTable() != null) {
            Inventory tempInventory = Bukkit.createInventory(null, container.getInventory().getSize());
            LootContext.Builder contextBuilder = new LootContext.Builder(location).lootedEntity(player);
            lootable.getLootTable().fillInventory(tempInventory, new Random(), contextBuilder.build());

            if (isInventoryEmpty(tempInventory) && config.isFallbackLootEnabled()) {
                populateWithFallbackLoot(tempInventory);
            }
            newLootContents = tempInventory.getContents();
        }
        // PATH 2: Already Captured Pre-filled Loot
        else if (data.hasCapturedLoot(location)) {
            newLootContents = data.getCapturedLoot(location);
        }
        // PATH 3: New Pre-filled Loot (BetterStructures, Trial Chambers)
        else if (!isInventoryEmpty(container.getInventory())) {
            data.captureLoot(location, container.getInventory().getContents());
            newLootContents = data.getCapturedLoot(location);
             if (config.isDebugMode()) {
                plugin.getLogger().info("Captured new pre-filled loot at: " + LocationUtil.locationToString(location));
            }
        } else {
            return; // It's an empty, unknown container. Ignore it.
        }

        // Create a new record for the player with the generated loot.
        PlayerLootRecord newRecord = new PlayerLootRecord(System.currentTimeMillis(), newLootContents);
        data.setPlayerRecord(location, player.getUniqueId(), newRecord);

        // Open the virtual inventory for the player.
        Inventory lootInventory = Bukkit.createInventory(null, container.getInventory().getSize(), config.getInventoryTitle());
        lootInventory.setContents(newLootContents);
        player.openInventory(lootInventory);

        // Track that this player is viewing this inventory.
        openLootInventories.put(player.getUniqueId(), location);

        // Send the "first loot" message.
        if (!config.getFirstLootMessage().isEmpty()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(config.getFirstLootMessage()));
        }
    }

    private void populateWithFallbackLoot(Inventory inventory) {
        Set<Integer> usedSlots = new HashSet<>();
        for (int i = 0; i < config.getFallbackItemsToGive(); i++) {
            FallbackLootItem chosenItem = getWeightedRandomFallbackItem();
            if (chosenItem == null) continue;
            int amount = chosenItem.getMinAmount() + (chosenItem.getMaxAmount() > chosenItem.getMinAmount() ? random.nextInt(chosenItem.getMaxAmount() - chosenItem.getMinAmount() + 1) : 0);
            ItemStack itemStack = new ItemStack(chosenItem.getMaterial(), amount);

            if (usedSlots.size() >= inventory.getSize()) break;

            int slot;
            do {
                slot = random.nextInt(inventory.getSize());
            } while (inventory.getItem(slot) != null && inventory.getItem(slot).getType() != Material.AIR);

            usedSlots.add(slot);
            inventory.setItem(slot, itemStack);
        }
    }

    private FallbackLootItem getWeightedRandomFallbackItem() {
        if (config.getTotalFallbackWeight() <= 0 || config.getFallbackItems().isEmpty()) return null;
        int roll = random.nextInt(config.getTotalFallbackWeight());
        for (FallbackLootItem item : config.getFallbackItems()) {
            roll -= item.getWeight();
            if (roll < 0) return item;
        }
        return null;
    }

    private boolean isInventoryEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        return true;
    }
}

