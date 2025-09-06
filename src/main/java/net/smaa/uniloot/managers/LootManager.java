package net.smaa.uniloot.managers;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.smaa.uniloot.UniLoot;
import net.smaa.uniloot.utils.FallbackLootItem;
import net.smaa.uniloot.utils.PlayerLootRecord;
import net.smaa.uniloot.utils.LocationUtil;
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
    // This map now only tracks which players have a UniLoot inventory open, for the close event.
    private final Map<UUID, Boolean> openLootInventories = new HashMap<>();

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

        Player player = event.getPlayer();
        Location primaryLocation = LocationUtil.getPrimaryLocation(block);

        if (data.isPlayerPlaced(primaryLocation)) {
            return; // This is a normal player chest, do nothing.
        }

        handleLootInteraction(player, block, primaryLocation, event);
    }

    private void handleLootInteraction(Player player, Block block, Location primaryLocation, PlayerInteractEvent event) {
        BlockState state = block.getState();
        if (!(state instanceof Container)) return;

        PlayerLootRecord record = data.getPlayerRecord(primaryLocation, player.getUniqueId());

        long now = System.currentTimeMillis();
        long cooldown = config.isRefreshEnabled() ? config.getRefreshIntervalMillis() : -1;
        boolean shouldGenerateNewLoot = true;

        if (record != null) {
            long timeSinceLooted = now - record.getTimestamp();
            if (cooldown == -1 || timeSinceLooted < cooldown) {
                shouldGenerateNewLoot = false;
            }
        }

        if (shouldGenerateNewLoot) {
            if (canGenerateLoot(state, primaryLocation)) {
                event.setCancelled(true);
                generateNewLootAndOpen(player, (Container) state, primaryLocation);
            }
        } else {
            event.setCancelled(true);
            openSavedLoot(player, record, (Container) state, primaryLocation);
        }
    }

    private boolean canGenerateLoot(BlockState state, Location location) {
        if (state instanceof Lootable lootable && lootable.getLootTable() != null) return true;
        if (state instanceof Container container && !isInventoryEmpty(container.getInventory())) return true;
        return data.hasCapturedLoot(location);
    }

    private void generateNewLootAndOpen(Player player, Container container, Location location) {
        ItemStack[] generatedContents = generateLootContents(container, location, player);
        if (generatedContents == null) return; // Should not happen if canGenerateLoot is true

        // --- NEW LOGIC: Create and save the record immediately ---
        PlayerLootRecord newRecord = new PlayerLootRecord(System.currentTimeMillis(), generatedContents);
        data.setPlayerRecord(location, player.getUniqueId(), newRecord);

        openPlayerInventory(player, container, newRecord.getContents());
        player.sendMessage(MiniMessage.miniMessage().deserialize(config.getFirstLootMessage()));
    }

    private ItemStack[] generateLootContents(Container container, Location location, Player player) {
        BlockState state = location.getBlock().getState();
        Inventory tempInventory = Bukkit.createInventory(null, container.getInventory().getSize());

        if (state instanceof Lootable lootable && lootable.getLootTable() != null) {
            LootContext.Builder lootContextBuilder = new LootContext.Builder(location).lootedEntity(player);
            lootable.getLootTable().fillInventory(tempInventory, random, lootContextBuilder.build());
            if (isInventoryEmpty(tempInventory) && config.isFallbackLootEnabled()) {
                populateWithFallbackLoot(tempInventory);
            }
            return tempInventory.getContents();
        }

        ItemStack[] capturedItems = data.getCapturedLoot(location);
        if (capturedItems != null && capturedItems.length > 0) {
            return capturedItems;
        }

        if (!isInventoryEmpty(container.getInventory())) {
            data.captureLoot(location, container.getInventory().getContents());
            if (config.isDebugMode()) {
                plugin.getLogger().info("Captured new pre-filled loot at: " + LocationUtil.locationToString(location));
            }
            return container.getInventory().getContents();
        }
        return null;
    }


    private void openSavedLoot(Player player, PlayerLootRecord record, Container container, Location location) {
        openPlayerInventory(player, container, record.getContents());

        if (config.isRefreshEnabled()) {
            long remainingCooldown = (record.getTimestamp() + config.getRefreshIntervalMillis()) - System.currentTimeMillis();
            if (remainingCooldown > 0) {
                String messageTemplate = config.getLootOnCooldownMessage();
                long hours = TimeUnit.MILLISECONDS.toHours(remainingCooldown);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingCooldown) % 60;
                long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingCooldown) % 60;

                player.sendMessage(MiniMessage.miniMessage().deserialize(messageTemplate,
                        Placeholder.unparsed("hours", String.valueOf(hours)),
                        Placeholder.unparsed("minutes", String.valueOf(minutes)),
                        Placeholder.unparsed("seconds", String.valueOf(seconds))
                ));
            }
        }
    }

    private void openPlayerInventory(Player player, Container container, ItemStack[] contents) {
        Inventory lootInventory = Bukkit.createInventory(null, container.getInventory().getSize(), config.getInventoryTitle());
        lootInventory.setContents(contents);
        player.openInventory(lootInventory);
        openLootInventories.put(player.getUniqueId(), true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (openLootInventories.containsKey(playerUUID)) {
            openLootInventories.remove(playerUUID);
            if (event.getView().title().equals(config.getInventoryTitle())) {
                // --- NEW LOGIC: Only update the contents, don't change the timestamp ---
                Location location = player.getTargetBlock(null, 5).getLocation(); // A reasonable guess
                data.updatePlayerRecordContents(LocationUtil.getPrimaryLocation(location.getBlock()), playerUUID, event.getInventory().getContents());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openLootInventories.remove(event.getPlayer().getUniqueId());
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

