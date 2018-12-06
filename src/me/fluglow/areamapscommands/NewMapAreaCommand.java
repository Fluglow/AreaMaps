package me.fluglow.areamapscommands;

import me.fluglow.AreaCreationSession;
import me.fluglow.CreationSessionManager;
import me.fluglow.MapManager;
import me.fluglow.PlayerAreaTracker;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NewMapAreaCommand implements CommandExecutor {

	private final CreationSessionManager sessionManager;
	private final MapManager mapManager;
	private final PlayerAreaTracker areaTracker;

	public NewMapAreaCommand(CreationSessionManager sessionManager, PlayerAreaTracker areaTracker, MapManager mapManager)
	{
		this.sessionManager = sessionManager;
		this.mapManager = mapManager;
		this.areaTracker = areaTracker;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
		if(sender instanceof Player)
		{
			Player player = (Player)sender;
			if(!sessionManager.addSession(player.getUniqueId(), new AreaCreationSession(player, mapManager, areaTracker, sessionManager)))
			{
				player.sendMessage(ChatColor.RED + "You're already creating a new map area!");
			}
		}
		else
		{
			sender.sendMessage(ChatColor.RED + "Only a player can create a map area.");
		}
		return true;
	}
}
