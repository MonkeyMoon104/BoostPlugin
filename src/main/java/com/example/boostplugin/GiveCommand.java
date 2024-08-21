package com.example.boostplugin;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GiveCommand implements CommandExecutor, TabCompleter {

    private final BoostPlugin plugin;

    public GiveCommand(BoostPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("give")) {
                if (args.length >= 3) {
                    Player player = (Player) sender;
                    String itemName = args[1];
                    String targetPlayerName = args[2];
                    plugin.giveCustomItem(itemName, player, targetPlayerName);
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /magia give <item> <targetPlayer>");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("boostplugin.reload")) {
                    plugin.reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Plugin configuration reloaded.");
                } else {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload the plugin configuration.");
                }
                return true;
            }
        }

        sender.sendMessage(ChatColor.RED + "Usage: /magia <give|reload> [args...]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("give", "reload");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (plugin.getConfig().contains("items")) {
                List<String> itemNames = plugin.getConfig().getConfigurationSection("items").getKeys(false).stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                return StringUtil.copyPartialMatches(args[1], itemNames, new ArrayList<>());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> onlinePlayerNames = getOnlinePlayerNames();
            return StringUtil.copyPartialMatches(args[2], onlinePlayerNames, new ArrayList<>());
        }

        return completions;
    }
    private List<String> getOnlinePlayerNames() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }
}
