package me.fluglow.areamapscommands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender sender, Command command, String s, String[] strings) {
		sender.sendMessage(ChatColor.GREEN + "AreaMaps is installed! Use " + ChatColor.YELLOW + "/help AreaMaps " + ChatColor.GREEN + "to get a list of available commands!");
		return true;
	}
}
