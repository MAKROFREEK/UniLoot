package net.smaa.uniloot.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.smaa.uniloot.UniLoot;
import net.smaa.uniloot.managers.ConfigManager;
import net.smaa.uniloot.managers.DataManager;
import net.smaa.uniloot.utils.LocationUtil;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UniLootCommand implements CommandExecutor, TabCompleter {

    private final UniLoot plugin;
    private final ConfigManager configManager;
    private final DataManager dataManager;

    public UniLootCommand(UniLoot plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataManager = plugin.getDataManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "scan":
                handleScan(sender, args);
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("uniloot.reload")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to use this command.</red>"));
            return;
        }
        configManager.loadConfig();
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>UniLoot configuration reloaded successfully.</green>"));
    }

    private void handleScan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player.</red>"));
            return;
        }
        if (!sender.hasPermission("uniloot.scan")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to use this command.</red>"));
            return;
        }

        Player player = (Player) sender;
        int radius = 0; // Default to 0, which is a 1x1 chunk area (the current chunk)
        if (args.length > 1) {
            try {
                radius = Integer.parseInt(args[1]);
                if (radius < 0) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Radius must be a positive number.</red>"));
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid radius specified. Please use a number.</red>"));
                return;
            }
        }

        player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Starting container scan in a " + (radius * 2 + 1) + "x" + (radius * 2 + 1) + " chunk radius... This may take a moment.</yellow>"));

        int chunksScanned = 0;
        int containersFound = 0;
        World world = player.getWorld();
        Chunk originChunk = player.getChunk();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Chunk currentChunk = world.getChunkAt(originChunk.getX() + x, originChunk.getZ() + z);
                chunksScanned++;
                for (int blockX = 0; blockX < 16; blockX++) {
                    for (int blockZ = 0; blockZ < 16; blockZ++) {
                        for (int blockY = world.getMinHeight(); blockY < world.getMaxHeight(); blockY++) {
                            Block block = currentChunk.getBlock(blockX, blockY, blockZ);
                            if (configManager.getEnabledContainerTypes().contains(block.getType())) {
                                dataManager.addPlayerPlaced(LocationUtil.getPrimaryLocation(block));
                                containersFound++;
                            }
                        }
                    }
                }
            }
        }

        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>Scan complete! Scanned <gold><chunks></gold> chunks and added <gold><containers></gold> containers to the player-placed list.</green>",
                Placeholder.unparsed("chunks", String.valueOf(chunksScanned)),
                Placeholder.unparsed("containers", String.valueOf(containersFound))
        ));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gold>--- UniLoot Commands ---</gold>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/uniloot reload</yellow> <gray>- Reloads the config file.</gray>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/uniloot scan [radius]</yellow> <gray>- Scans containers in a chunk radius around you and marks them as player-placed.</gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // Suggest subcommands based on permission
            List<String> subcommands = new ArrayList<>();
            if (sender.hasPermission("uniloot.reload")) {
                subcommands.add("reload");
            }
            if (sender.hasPermission("uniloot.scan")) {
                subcommands.add("scan");
            }
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("scan") && sender.hasPermission("uniloot.scan")) {
            // Suggest a placeholder for the radius
            completions.add("[radius]");
        }
        return completions;
    }
}

