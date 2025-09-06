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

    // --- Player Placed Block Tracking ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        // --- NEW: Prevent creating double chests with loot containers ---
        if (blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST) {
            for (BlockFace face : ADJACENT_FACES) {
                Block adjacent = block.getRelative(face);
                if (adjacent.getType() == blockType && isProtectedContainer(adjacent)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(config.getPlaceNextToLootChestMessage()));
                    return;
                }
            }
        }

        if (config.getEnabledContainerTypes().contains(blockType)) {
            data.addPlayerPlaced(LocationUtil.getPrimaryLocation(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (config.getEnabledContainerTypes().contains(block.getType())) {
            // This now clears all data types for the location to prevent ghost data
            data.clearAllDataForLocation(LocationUtil.getPrimaryLocation(block));
        }
    }


    // --- Container Protection Logic ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreakAttempt(BlockBreakEvent event) {
        if (!config.isProtectionEnabled() || !isProtectedContainer(event.getBlock())) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && config.isCreativeBreakAllowed()) {
            handleCreativeBreak(player, event.getBlock(), event);
        } else {
            event.setCancelled(true);
            player.sendMessage(MiniMessage.miniMessage().deserialize(config.getBreakAttemptSurvivalMessage()));
        }
    }

    private void handleCreativeBreak(Player player, Block block, BlockBreakEvent event) {
        UUID playerUUID = player.getUniqueId();
        Location blockLocation = LocationUtil.getPrimaryLocation(block);

        if (creativeBreakConfirmations.containsKey(playerUUID) && creativeBreakConfirmations.get(playerUUID).equals(blockLocation)) {
            creativeBreakConfirmations.remove(playerUUID);
            // Allow the break to proceed
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
        event.blockList().removeIf(this::isProtectedContainer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!config.isProtectionEnabled()) return;
        event.blockList().removeIf(this::isProtectedContainer);
    }

    private boolean isProtectedContainer(Block block) {
        if (!config.getEnabledContainerTypes().contains(block.getType())) return false;

        BlockState state = block.getState();
        if (!(state instanceof Container)) return false;

        // A container is protected if it's not player-placed AND (it has a loot table OR has been captured)
        boolean isPlayerPlaced = data.isPlayerPlaced(LocationUtil.getPrimaryLocation(block));
        if (isPlayerPlaced) return false;

        boolean hasLootTable = state instanceof Lootable lootable && lootable.getLootTable() != null;
        boolean isCaptured = data.hasCapturedLoot(LocationUtil.getPrimaryLocation(block));

        return hasLootTable || isCaptured;
    }
}

