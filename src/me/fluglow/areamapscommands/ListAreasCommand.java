package me.fluglow.areamapscommands;

import me.fluglow.MapManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ListAreasCommand implements CommandExecutor {

	private final MapManager mapManager;

	public ListAreasCommand(MapManager mapManager)
	{
		this.mapManager = mapManager;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
		List<String> areas = mapManager.getAreaNames();
		if(areas.size() == 0)
		{
			sender.sendMessage(ChatColor.RED + "No areas found.");
			return true;
		}
		for(String line : mapManager.getAreaNames())
		{
			sender.sendMessage(ChatColor.YELLOW + line);
		}
		return true;
	}
}
