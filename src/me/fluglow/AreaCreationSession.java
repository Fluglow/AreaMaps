package me.fluglow;

import com.sk89q.worldedit.bukkit.selections.Polygonal2DSelection;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class AreaCreationSession
{
	private boolean editingName;
	private final World world;

	private List<Vector> positions;
	private String areaName;
	private Player player;

	private final MapManager mapManager;
	private final CreationSessionManager sessionManager;
	private final PlayerAreaTracker areaTracker;

	private final static String[] ACTIONS = {
			"Add your position to the corners of the area",
			"Remove the most recent position from the corners of the area",
			"Get the positions you have set",
			"Set the area's name",
			"Save the area",
			"Exit area creation without saving"
	};

	private final static String[] HELP = {
			"Please use your chat to select an option.",
			"You can not send any actual chat messages during this.",
			"To select an option, type the option's number to your chat and press enter."
	};

	public AreaCreationSession(Player player, MapManager mapManager, PlayerAreaTracker areaTracker, CreationSessionManager sessionManager)
	{
		this.areaTracker = areaTracker;
		positions = new ArrayList<>();
		areaName = "";
		this.world = player.getWorld();
		this.mapManager = mapManager;
		this.sessionManager = sessionManager;
		this.player = player;
		clearChat();
		sendHelpToPlayer();
		sendActionsToPlayer();
	}

	private void sendHelpToPlayer()
	{
		for (String helpStr : HELP) {
			player.sendMessage(ChatColor.YELLOW + helpStr);
		}
	}

	private void sendActionsToPlayer()
	{
		for(int i = 0; i < ACTIONS.length; i++)
		{
			player.sendMessage(ChatColor.YELLOW + Integer.toString(i+1) + ". " + ChatColor.DARK_GREEN + ACTIONS[i]);
		}
	}

	private void clearChat()
	{
		for(int i = 0; i < 10; i++)
		{
			player.sendMessage("");
		}
	}

	void handleSessionChat(String message, Player player)
	{
		if(editingName) //If player is setting the name of the area
		{
			if(mapManager.getArea(message) != null)
			{
				player.sendMessage(ChatColor.RED + "An area with that name already exists.");
			}
			else
			{
				areaName = message;
				player.sendMessage(ChatColor.GREEN + "Area name set!");
			}
			editingName = false;
			return;
		}

		if(isInteger(message))
		{
			int option = Integer.parseInt(message);
			switch (option)
			{
				case 1: {
					positions.add(player.getLocation().toVector()); //Set second position
					player.sendMessage(ChatColor.GREEN + "Position added!");
					break;
				}
				case 2: {
					Vector pos = positions.get(positions.size() - 1);
					Vector rounded = new Vector(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
					positions.remove(positions.size() - 1);
					player.sendMessage(ChatColor.GREEN + "Removed position: " + ChatColor.YELLOW + rounded);
					break;
				}
				case 3: {
					for(int i = 0; i < positions.size(); i++)
					{
						Vector pos = positions.get(i);
						Vector rounded = new Vector(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
						player.sendMessage(ChatColor.GREEN + Integer.toString(i+1) + ". " + ChatColor.YELLOW + rounded);
					}
					break;
				}
				case 4: {
					editingName = true;
					player.sendMessage(ChatColor.YELLOW + "Please enter the name for the area.");
					break;
				}
				case 5: {
					String error = getUnsetValueError();
					if(error.isEmpty())
					{
						MapArea playerOldArea = mapManager.getLocationArea(player.getLocation());
						MapArea area = toMapArea();
						mapManager.addArea(area);
						MapArea newArea = mapManager.getLocationArea(player.getLocation());
						player.sendMessage(ChatColor.GREEN + "Area finished!" + ChatColor.YELLOW + " You are now able to send chat messages again.");
						player.sendMessage(ChatColor.GREEN + "Area ID: " + ChatColor.YELLOW + area.getId());
						mapManager.saveMapAreas();
						if(newArea.getId() == area.getId())
						{
							areaTracker.changePlayerArea(playerOldArea, newArea, player);
						}
						sessionManager.removeSession(player.getUniqueId());
					}
					else
					{
						player.sendMessage(ChatColor.RED + error);
					}
					break;
				}
				case 6: {
					sessionManager.removeSession(player.getUniqueId());
					player.sendMessage(ChatColor.GREEN + "Session quit without saving.");
					break;
				}
				default: {
					clearChat();
					sendActionsToPlayer();
				}
			}
		}
		else
		{
			clearChat();
			sendHelpToPlayer();
			sendActionsToPlayer();
		}
	}

	private String getUnsetValueError()
	{
		if(areaName.isEmpty())
		{
			return "The area doesn't have a name.";
		}

		if(positions.size() < 3)
		{
			return "The area doesn't have enough positions.";
		}
		return "";
	}

	private MapArea toMapArea()
	{
		Polygonal2DSelection selections = MapArea.pointsToSelection(positions, world);
		int id = mapManager.getNextIdForMap();
		return new MapArea(selections, mapManager.loadSettingsForArea(id), areaName, id);
	}

	private static boolean isInteger(String s)
	{
		if(s.isEmpty()) return false;
		for(int i = 0; i < s.length(); i++) {
			if(i == 0 && s.charAt(i) == '-') {
				if(s.length() == 1) return false;
				else continue;
			}
			if(Character.digit(s.charAt(i),10) < 0) return false;
		}
		return true;
	}
}
