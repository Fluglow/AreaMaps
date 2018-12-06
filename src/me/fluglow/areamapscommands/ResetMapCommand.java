package me.fluglow.areamapscommands;

import me.fluglow.MapArea;
import me.fluglow.MapManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ResetMapCommand implements CommandExecutor {

	private final MapManager mapManager;

	public ResetMapCommand(MapManager mapManager)
	{
		this.mapManager = mapManager;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
		if(args.length > 0)
		{
			MapArea area;

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

			if((area = mapManager.getArea(areaName)) != null)
			{
				if(!mapManager.getMapRenderer().hasAsyncRenderer(area.getId()))
				{
					mapManager.resetAreaMapPixels(area);
					sender.sendMessage(ChatColor.GREEN + "Pixels reset for area " + area.getName() + ".");
				}
				else
				{
					sender.sendMessage(ChatColor.RED + "That map is being drawn, please wait for it to finish.");
				}
			}
			else
			{
				sender.sendMessage(ChatColor.RED + "Area not found.");
			}
		}
		else
		{
			sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.YELLOW + "/resetmap <Area name>");
		}
		return true;
	}
}
