package me.fluglow.areamapscommands;

import me.fluglow.MapArea;
import me.fluglow.MapManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DrawFullMapCommand implements CommandExecutor {

	private final MapManager mapManager;

	public DrawFullMapCommand(MapManager mapManager)
	{
		this.mapManager = mapManager;
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
			MapArea area;
			if((area = mapManager.getArea(areaName)) != null)
			{
				if(!mapManager.getMapRenderer().isFinished(area.getId()))
				{
					if(mapManager.drawWholeMap(area, sender))
					{
						sender.sendMessage(ChatColor.GREEN + "Started drawing pixels for area map " + area.getName() + ".");
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "That map is already being drawn!");
					}
				}
				else
				{
					sender.sendMessage(ChatColor.RED + "That map is already completely drawn. Please clear it first.");
				}
			}
			else
			{
				sender.sendMessage(ChatColor.RED + "Area not found.");
			}
		}
		else
		{
			sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.YELLOW + "/drawfullmap <Area name>");
		}
		return true;
	}
}
