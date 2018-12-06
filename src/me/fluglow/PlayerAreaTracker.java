package me.fluglow;

import com.google.common.primitives.Ints;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerAreaTracker implements Listener {

	private MapManager mapManager;
	private Map<String, BossBar> areaBossBars = new HashMap<>();

	final boolean bossbarsEnabled;
	private final boolean titlesEnabled;
	private final int mapSlot;
	private final boolean dropItemIfFull;

	PlayerAreaTracker(MapManager mapManager)
	{
		this.mapManager = mapManager;
		FileConfiguration config = JavaPlugin.getPlugin(AreaMaps.class).getConfig();
		mapSlot = Ints.constrainToRange(JavaPlugin.getPlugin(AreaMaps.class).getConfig().getInt("map_item_slot") - 1, -1, 8); //-1 is used for offhand
		bossbarsEnabled = config.getBoolean("display_area_name_in_bossbar");
		titlesEnabled = config.getBoolean("display_area_name_as_title");
		dropItemIfFull = config.getBoolean("drop_item_if_inventory_full");
	}

	@EventHandler
	public void changedArea(PlayerMoveEvent event)
	{
		MapArea fromArea = mapManager.getLocationArea(event.getFrom());
		MapArea toArea = mapManager.getLocationArea(event.getTo());
		if(fromArea.getId() != toArea.getId())
		{
			changePlayerArea(fromArea, toArea, event.getPlayer());
		}
	}

	@EventHandler
	public void changedAreaTeleport(PlayerTeleportEvent event)
	{
		MapArea fromArea = mapManager.getLocationArea(event.getFrom());
		MapArea toArea = mapManager.getLocationArea(event.getTo());
		if(fromArea.getId() != toArea.getId())
		{
			changePlayerArea(fromArea, toArea, event.getPlayer());
		}
	}

	@EventHandler
	public void changedAreaRespawn(PlayerRespawnEvent event)
	{
		MapArea toArea = mapManager.getLocationArea(event.getRespawnLocation());
		changePlayerArea(MapManager.UNKNOWN_AREA, toArea, event.getPlayer());
	}

	@EventHandler
	public void areaOnJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		MapArea area = mapManager.getLocationArea(player.getLocation());
		changePlayerArea(MapManager.UNKNOWN_AREA, area, player);
	}

	@EventHandler
	public void areaOnExit(PlayerQuitEvent event)
	{
		removePlayerFromMap(event.getPlayer());

		//Bossbar handling
		if(!bossbarsEnabled) return;
		Player player = event.getPlayer();
		String area = mapManager.getLocationArea(player.getLocation()).getName();
		if(!area.equals(MapManager.UNKNOWN_AREA.getName()))
		{
			refreshPlayerBossbar(area, "", player); //Remove bossbar
		}
	}

	@EventHandler
	public void refreshBossbarOnDeath(PlayerDeathEvent event)
	{
		Player player = event.getEntity();
		MapArea area = mapManager.getLocationArea(player.getLocation());
		if(area.getId() != MapManager.UNKNOWN_AREA.getId())
		{
			changePlayerArea(area, MapManager.UNKNOWN_AREA, player);
		}
	}

	void changePlayerArea(MapArea oldArea, MapArea newArea, Player player)
	{
		if(!newArea.getSettings().isEnabled())
		{
			refreshPlayerMap(player, MapManager.UNKNOWN_AREA);
			return; //If area is disabled, just make sure that the player doesn't have an empty map (can happen on join if area was previously enabled)
		}
		if(titlesEnabled)
		{
			player.sendTitle(ChatColor.YELLOW + newArea.getName(), "", 40, 100, 40);
		}

		if(bossbarsEnabled)
		{
			refreshPlayerBossbar(oldArea.getName(), newArea.getName(), player);
		}

		if(oldArea.getId() != MapManager.UNKNOWN_AREA.getId())
		{
			removePlayerFromMap(player);
		}

		refreshPlayerMap(player, newArea);
		mapManager.saveRenderData(); //Save renderers that need to be saved.
	}

	private Collection<BossBar> getBossBars()
	{
		return areaBossBars.values();
	}

	public void removeBossbars(String area)
	{
		if(areaBossBars.containsKey(area))
		{
			areaBossBars.get(area).removeAll();
			areaBossBars.remove(area);
		}
	}

	public void refreshPlayerMaps()
	{
		for(UUID playerUUID : mapManager.getMapRenderer().getPlayerAreas().keySet()) //Loop for players that are being rendered on a map. We don't track active players elsewhere.
		{
			Player player = Bukkit.getPlayer(playerUUID);
			refreshPlayerMap(player, mapManager.getLocationArea(player.getLocation()));
		}
	}

	void clearBossbars()
	{
		if(!bossbarsEnabled) return;
		for(BossBar bar : getBossBars())
		{
			bar.removeAll();
		}
	}

	private void refreshPlayerBossbar(String fromArea, String toArea, Player player)
	{
		if(!fromArea.isEmpty())
		{
			if(!areaBossBars.containsKey(fromArea))
			{
				BossBar bossBarTitle = Bukkit.createBossBar(ChatColor.YELLOW + fromArea, BarColor.BLUE, BarStyle.SOLID);
				areaBossBars.put(fromArea, bossBarTitle);
			}
			else
			{
				areaBossBars.get(fromArea).removePlayer(player);
			}
		}
		if(!toArea.isEmpty())
		{
			if(!areaBossBars.containsKey(toArea))
			{
				BossBar bossBarTitle = Bukkit.createBossBar(ChatColor.YELLOW + toArea, BarColor.BLUE, BarStyle.SOLID);
				areaBossBars.put(toArea, bossBarTitle);
			}
			areaBossBars.get(toArea).addPlayer(player);
		}
	}

	@SuppressWarnings("deprecation")
	private void refreshPlayerMap(Player player, MapArea newArea)
	{
		if(newArea.getId() == MapManager.UNKNOWN_AREA.getId()) //If area doesn't exist, remove map item from player
		{
			if(mapManager.isPluginMap(getMapItem(player, mapSlot)))
			{
				setMapItem(player, null);
			}
			return;
		}
		addPlayerToArea(player, mapManager.getRendererWorldMap().mapView.getId(), newArea.getId());
	}

	private void addPlayerToArea(Player player, int mapId, int areaId)
	{
		mapManager.getMapRenderer().addPlayerToMap(player.getUniqueId(), areaId); //Order matters. We need to prepare the map for the player first.
		ItemStack itemInMapSlot = getMapItem(player, mapSlot);

		//1.12.2 Has an inconsistency with getItem(slot) and getItemInMainHand, so we need to check for null and for Material.AIR.
		if(itemInMapSlot != null && itemInMapSlot.getType() != Material.AIR && !mapManager.isPluginMap(itemInMapSlot)) //If there's something in the map slot.
		{
			int firstEmpty = player.getInventory().firstEmpty(); //Get first empty slot
			if(firstEmpty == -1) //If there are no empty slots
			{
				if(dropItemIfFull)
				{
					setMapItem(player, null);
					player.getWorld().dropItem(player.getLocation(), itemInMapSlot);
					player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, .7f);
				}
				else
				{
					return; //Don't give map, inventory is full.
				}
			}
			else
			{
				player.getInventory().setItem(firstEmpty, itemInMapSlot);
			}
		}
		setMapItem(player, new ItemStack(Material.MAP, 1, (short)mapId));
	}

	private void removePlayerFromMap(Player player)
	{
		mapManager.getMapRenderer().removePlayerFromMap(player.getUniqueId());
	}

	private ItemStack getMapItem(Player player, int slot)
	{
		return slot == -1 ? player.getInventory().getItemInOffHand() : player.getInventory().getItem(slot);
	}

	private void setMapItem(Player player, ItemStack itemStack)
	{
		if(mapSlot == -1)
		{
			player.getInventory().setItemInOffHand(itemStack);
		}
		else
		{
			player.getInventory().setItem(mapSlot, itemStack);
		}
	}
}
