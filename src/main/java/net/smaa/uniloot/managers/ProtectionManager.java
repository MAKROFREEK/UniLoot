package net.smaa.uniloot.managers;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.smaa.uniloot.UniLoot;
import net.smaa.uniloot.utils.LocationUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.loot.Lootable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectionManager implements Listener {

    private final UniLoot plugin;
    private final ConfigManager config;
    private final DataManager data;
    private final Map<UUID, Location> creativeBreakConfirmations = new HashMap<>();
    private static final BlockFace[] ADJACENT_FACES = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };


    public ProtectionManager(UniLoot plugin, ConfigManager configManager, DataManager dataManager) {
        this.plugin = plugin;
        this.config = configManager;
        this.data = dataManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Prevent creating double chests with loot containers
        if (blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST) {
            for (BlockFace face : ADJACENT_FACES) {
                Block adjacent = block.getRelative(face);
                if (adjacent.getType() == blockType) {
                    if (isProtectedLootContainer(adjacent)) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(config.getPlaceNextToLootChestMessage()));
                        return;
                    }
                }
            }
        }

        // Track the placed container so the plugin knows to ignore it
        if (config.getEnabledContainerTypes().contains(blockType)) {
            data.addPlayerPlaced(LocationUtil.getPrimaryLocation(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (config.getEnabledContainerTypes().contains(block.getType())) {
            // Clear all data associated with this location to prevent "ghost" loot
            data.clearAllDataForLocation(LocationUtil.getPrimaryLocation(block));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreakAttempt(BlockBreakEvent event) {
        if (!config.isProtectionEnabled()) return;

        if (isProtectedLootContainer(event.getBlock())) {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && config.isCreativeBreakAllowed()) {
                handleCreativeBreak(player, event.getBlock(), event);
            } else {
                event.setCancelled(true);
                player.sendMessage(MiniMessage.miniMessage().deserialize(config.getBreakAttemptSurvivalMessage()));
            }
        }
    }

    private void handleCreativeBreak(Player player, Block block, BlockBreakEvent event) {
        UUID playerUUID = player.getUniqueId();
        Location blockLocation = LocationUtil.getPrimaryLocation(block);

        if (creativeBreakConfirmations.containsKey(playerUUID) && creativeBreakConfirmations.get(playerUUID).equals(blockLocation)) {
            creativeBreakConfirmations.remove(playerUUID);
        } else {
            event.setCancelled(true);
            creativeBreakConfirmations.put(playerUUID, blockLocation);
            String warningMessage = config.getBreakWarningCreativeMessage();
            player.sendMessage(MiniMessage.miniMessage().deserialize(warningMessage,
                    Placeholder.unparsed("time", String.valueOf(config.getCreativeBreakConfirmationSeconds()))
            ));

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> creativeBreakConfirmations.remove(playerUUID, blockLocation), config.getCreativeBreakConfirmationSeconds() * 20L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!config.isProtectionEnabled()) return;
        event.blockList().removeIf(this::isProtectedLootContainer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!config.isProtectionEnabled()) return;
        event.blockList().removeIf(this::isProtectedLootContainer);
    }

    private boolean isProtectedLootContainer(Block block) {
        if (!config.getEnabledContainerTypes().contains(block.getType())) {
            return false;
        }

        BlockState state = block.getState();
        if (!(state instanceof Container)) {
            return false;
        }

        Location primaryLocation = LocationUtil.getPrimaryLocation(block);

        // A container is NOT a loot container if the player placed it. This check is crucial.
        // --- THIS IS THE FIX --- Removed the .join() call
        if (data.isPlayerPlaced(primaryLocation)) {
            return false;
        }

        // It's a loot container if it has a loot table...
        boolean hasLootTable = state instanceof Lootable lootable && lootable.getLootTable() != null;
        if (hasLootTable) {
            return true;
        }

        // ...or if it's a pre-filled chest that the plugin has captured.
        // --- THIS IS THE FIX --- Removed the .join() call
        return data.hasCapturedLoot(primaryLocation);
    }
}

