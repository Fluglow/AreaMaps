package me.fluglow.areamapscommands;

import me.fluglow.MapManager;
import me.fluglow.PlayerAreaTracker;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DeleteMapAreaCommand implements CommandExecutor {

	private final MapManager mapManager;
	private final PlayerAreaTracker areaTracker;

	public DeleteMapAreaCommand(MapManager mapManager, PlayerAreaTracker areaTracker)
	{
		this.mapManager = mapManager;
		this.areaTracker = areaTracker;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
		if(args.length > 0)
		{
			StringBuilder areaNameBuilder = new StringBuilder();
			for(int i = 0; i < args.length; i++)
			{
				areaNameBuilder.append(args[i]);
				if(i != args.length - 1)
				{
					areaNameBuilder.append(" ");
				}
			}
			String areaName = areaNameBuilder.toString();

			if(mapManager.deleteMap(areaName))
			{
				areaTracker.removeBossbars(areaName);
				areaTracker.refreshPlayerMaps();
				sender.sendMessage(ChatColor.GREEN + "Deleted map area " + areaName + ".");
			}
			else
			{
				sender.sendMessage(ChatColor.RED + "Area not found.");
			}
		}
		else
		{
			sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.YELLOW + "/deletemaparea <Area name>");
		}
		return true;
	}
}
