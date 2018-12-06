package me.fluglow.areamapscommands;

import me.fluglow.MapArea;
import me.fluglow.MapManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShowAreaBorderCommand implements CommandExecutor {

	private final MapManager mapManager;

	public ShowAreaBorderCommand(MapManager mapManager)
	{
		this.mapManager = mapManager;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {

		if(args.length == 0)
		{
			if(sender instanceof Player)
			{
				sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.YELLOW + "/showareaborder <Area name>");
			}
			else
			{
				sender.sendMessage(ChatColor.RED + "Usage for console: " + ChatColor.YELLOW + "/showareaborder <Area name> <Player>");
			}
			return true;
		}

		String areaName;
		Player player;

		if(sender instanceof Player)
		{
			player = (Player)sender;
			StringBuilder areaNameBuilder = new StringBuilder();
			for(int i = 0; i < args.length; i++)
			{
				areaNameBuilder.append(args[i]);
				if(i != args.length - 1)
				{
					areaNameBuilder.append(" ");
				}
			}
			areaName = areaNameBuilder.toString();
		}
		else
		{
			if(args.length == 1)
			{
				sender.sendMessage(ChatColor.RED + "Usage for console: " + ChatColor.YELLOW + "/showareaborder <Area name> <Player>");
				return true;
			}

			player = sender.getServer().getPlayer(args[args.length - 1]);
			if(player == null)
			{
				sender.sendMessage(ChatColor.RED + "Player not found.");
				return true;
			}

			StringBuilder areaNameBuilder = new StringBuilder();
			for(int i = 0; i < args.length - 1; i++)
			{
				areaNameBuilder.append(args[i]);
				if(i != args.length - 2)
				{
					areaNameBuilder.append(" ");
				}
			}
			areaName = areaNameBuilder.toString();
		}

		MapArea area = mapManager.getArea(areaName);
		if(area != null)
		{
			area.showBorderToPlayer(player);
			if(!(sender instanceof Player))
			{
				sender.sendMessage(ChatColor.GREEN + "Showed border of area " + area.getName() + " to " + player.getName() + "!");
			}
		}
		else
		{
			sender.sendMessage(ChatColor.RED + "Area not found.");
		}
		return true;
	}
}
