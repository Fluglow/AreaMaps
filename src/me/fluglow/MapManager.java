package me.fluglow;

import net.minecraft.server.v1_12_R1.WorldMap;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;


public class MapManager implements Listener {

	private File areaSaveFile;
	private FileConfiguration areaSaveConfig;
	private File rendererSaveFile;
	private FileConfiguration rendererSaveConfig;
	private File areaSettingsFile;
	private FileConfiguration areaSettingsConfig;

	private Map<Integer, MapArea> mapAreas;



	private final MapAreaRenderer mapAreaRenderer;
	private final WorldMap areaMapWorldMap;

	static MapArea UNKNOWN_AREA;

	private final boolean allowMapItemDestroy;

	public MapManager(JavaPlugin plugin, File areaSaveFile, File rendererSaveFile, File areaSettingsFile)
	{
		mapAreas = new LinkedHashMap<>();
		this.areaSaveFile = areaSaveFile;
		this.areaSaveConfig = createConfig(plugin, areaSaveFile);
		this.rendererSaveFile = rendererSaveFile;
		this.rendererSaveConfig = createConfig(plugin, rendererSaveFile);
		this.areaSettingsFile = areaSettingsFile;
		this.areaSettingsConfig = createConfig(plugin, areaSettingsFile);

		AreaMaps mainPlugin = JavaPlugin.getPlugin(AreaMaps.class);
		FileConfiguration config = JavaPlugin.getPlugin(AreaMaps.class).getConfig();

		allowMapItemDestroy = config.getBoolean("allow_map_item_deletion");

		int mapId = getWorldMapIdForAreas(mainPlugin);
		areaMapWorldMap = getWorldMap(mapId);

		loadMapAreas();
		mapAreaRenderer = loadMapRenderer(getRendererWorldMap(), mapAreas.values());
		prepareMapForRendering(areaMapWorldMap, mapAreaRenderer);
		UNKNOWN_AREA = new MapArea(null, new MapAreaSettings(true, "", null), config.getString("unknown_area_name"), -1);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private YamlConfiguration createConfig(JavaPlugin plugin, File file)
	{
		if(!file.exists())
		{
			file.getParentFile().mkdirs();
			plugin.saveResource(file.getName(), false);
		}
		YamlConfiguration config = new YamlConfiguration();

		try {
			config.load(file);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}

		return config;
	}



	public List<String> getAreaNames()
	{
		List<String> names = new ArrayList<>();
		for(MapArea area : mapAreas.values())
		{
			names.add(area.getId() + ". " + area.getName());
		}
		return names;
	}

	boolean isPluginMap(ItemStack item)
	{
		return item != null && item.getType() == Material.MAP && item.getDurability() == areaMapWorldMap.mapView.getId();
	}

	@EventHandler
	public void playerGrabMap(InventoryClickEvent event)
	{
		ItemStack item = event.getCurrentItem();
		if(item == null) return;
		if(isPluginMap(item))
		{
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void playerChangeHand(PlayerSwapHandItemsEvent event)
	{
		if(isPluginMap(event.getMainHandItem()) || isPluginMap(event.getOffHandItem()))
		{
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void playerDropMap(PlayerDropItemEvent event)
	{
		ItemStack item = event.getItemDrop().getItemStack();
		if(item == null) return;
		if(isPluginMap(item))
		{
			if(allowMapItemDestroy)
			{
				event.getItemDrop().remove();
			}
			else
			{
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void playerDeathMapDrop(PlayerDeathEvent event)
	{
		if(!event.getKeepInventory())
		{
			for(int i = 0; i < event.getDrops().size(); i++)
			{
				if(isPluginMap(event.getDrops().get(i)))
				{
					event.getDrops().remove(i);
					return;
				}
			}
		}
	}

	int getNextIdForMap()
	{
		int id = 0;
		while(mapAreas.containsKey(id))
		{
			id++;
		}
		return id;
	}

	public MapArea getArea(String name)
	{
		for(MapArea area : mapAreas.values())
		{
			if(area.getName().equalsIgnoreCase(name))
			{
				return area;
			}
		}
		return null;
	}

	MapArea getArea(int id)
	{
		return mapAreas.get(id);
	}

	void addArea(MapArea area)
	{
		mapAreas.put(area.getId(), area);
		getMapRenderer().addAreaToRenderer(area);
	}

	public MapAreaRenderer getMapRenderer()
	{
		return mapAreaRenderer;
	}

	MapArea getLocationArea(Location location)
	{
		for(MapArea area : mapAreas.values())
		{
			if(areaContains(area, location.clone())) //Ignores Y
			{
				return area;
			}
		}
		return UNKNOWN_AREA;
	}

	private boolean areaContains(MapArea area, Location location)
	{
		String regex = area.getSettings().getWorldRegex();
		World locWorld = location.getWorld();
		if(!regex.isEmpty())
		{
			if(!Pattern.compile(regex).matcher(locWorld.getName()).find())
			{
				return false;
			}
			location.setWorld(area.getSelection().getWorld()); //Regex is valid, set world to area's world to make selection.contains world check pass.
		}

		return area.getSelection().contains(location);
	}

	private WorldMap getWorldMap(int id)
	{
		WorldMap map = (WorldMap)((CraftServer)Bukkit.getServer()).getServer().worlds.get(0).a(WorldMap.class, "map_" + id);
		if(map == null)
		{
			map = createNextMap();
		}
		return map;
	}

	private WorldMap createNextMap()
	{
		//We could use Bukkit's method or ItemWorldMap's method for this, but this way it can be modified more. This is just a modified version of FILLED_MAP's getSavedMap()
		net.minecraft.server.v1_12_R1.World worldMain = ((CraftServer)Bukkit.getServer()).getServer().worlds.get(0);
		int id = worldMain.b("map");
		String s = "map_" + id;
		WorldMap worldmap = new WorldMap(s);
		worldMain.a(s, worldmap);
		worldmap.scale = 0;
		worldmap.a(0,0,0); //Middle X, Middle Z, Scale. Initializes values.
		worldmap.map = (byte)((WorldServer)worldMain).dimension;
		worldmap.track = false;
		worldmap.unlimitedTracking = false;
		worldmap.c();
		return worldmap;
	}

	private int getWorldMapIdForAreas(AreaMaps main)
	{
		int id;
		if((id = main.getConfig().getInt("WorldMapId", -1)) == -1)
		{
			id = 0;
			WorldServer nmsWorldServer = ((CraftServer)Bukkit.getServer()).getServer().worlds.get(0);
			WorldMap map = (WorldMap)nmsWorldServer.a(WorldMap.class, "map_" + id);
			while(map != null)
			{
				id++;
				map = (WorldMap)nmsWorldServer.a(WorldMap.class, "map_" + id);
			}
			main.getConfig().set("WorldMapId", id);
			main.saveConfig();

		}
		return id;
	}

	private void prepareMapForRendering(WorldMap map, MapAreaRenderer mapAreaRenderer)
	{
		for(MapRenderer renderer : map.mapView.getRenderers())
		{
			map.mapView.removeRenderer(renderer);
		}
		map.mapView.addRenderer(mapAreaRenderer);
	}

	WorldMap getRendererWorldMap()
	{
		return areaMapWorldMap;
	}

	public boolean deleteMap(String areaName)
	{
		MapArea area = getArea(areaName);
		if(area == null) return false;
		int id = area.getId();
		String idStr = Integer.toString(id);
		if(areaSaveConfig.contains(idStr))
		{
			rendererSaveConfig.set(idStr, null);
			areaSettingsConfig.set(idStr, null);
			areaSaveConfig.set(idStr, null);
			mapAreas.remove(id);
			mapAreaRenderer.removeAreaFromRenderer(id);
			saveMapAreas();
			saveRenderData();
			saveAreaSettings();
			return true;
		}
		return false;
	}

	public void resetAreaMapPixels(MapArea area)
	{
		mapAreaRenderer.resetAreaMap(area.getId());
		saveRenderData();
	}

	public boolean drawWholeMap(MapArea area, CommandSender sender)
	{
		int id = area.getId();
		return mapAreaRenderer.drawWholeMap(id, sender);
	}

	void saveMapAreas()
	{
		List<MapArea> sortedValues = new ArrayList<>(mapAreas.values());
		sortedValues.sort(Comparator.comparingInt(MapArea::getId));
		for(MapArea area : sortedValues)
		{
			areaSaveConfig.set(Integer.toString(area.getId()), area.serialize());
		}

		try {
			areaSaveConfig.save(areaSaveFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		saveAreaSettings();
	}

	private void loadMapAreas()
	{
		Set<String> keys = areaSaveConfig.getKeys(false);
		List<Integer> ids = new ArrayList<>();
		for(String key : keys)
		{
			try {
				ids.add(Integer.parseInt(key));
			} catch(NumberFormatException e) {
				Bukkit.getLogger().warning("[AreaMaps] Tried to load map area with id " + key + " but failed because \"" + key + "\" is not an integer!");
			}
		}

		ids.sort(Comparator.comparingInt(Integer::intValue));
		for(int id : ids)
		{
			MapAreaSettings settings = loadSettingsForArea(id);

			MapArea area = MapArea.deserialize(areaSaveConfig.getConfigurationSection(Integer.toString(id)).getValues(true), id, settings);
			if(area != null)
			{
				mapAreas.put(area.getId(), area);
			}
		}
	}

	private MapAreaRenderer loadMapRenderer(WorldMap worldMap, Collection<MapArea> mapAreas)
	{
		Map<Integer, MapAreaRenderData> renderData = new HashMap<>();
		Map<Integer, MapAreaRenderSettings> renderSettings = new HashMap<>();
		Set<String> keys = rendererSaveConfig.getKeys(false);
		for(String key : keys)
		{
			MapAreaRenderData renderer = MapAreaRenderData.deserialize(rendererSaveConfig.getConfigurationSection(key).getValues(true), key, this);
			if(renderer != null)
			{
				renderData.put(renderer.getAreaId(), renderer);
			}
		}

		for(MapArea area : mapAreas)
		{
			if(!renderData.containsKey(area.getId()))
			{
				renderData.put(area.getId(), MapAreaRenderData.fromMapArea(area));
			}
			renderSettings.put(area.getId(), area.getSettings().getRenderSettings());
		}
		return new MapAreaRenderer(worldMap, renderData, renderSettings);
	}

	void saveRenderData()
	{
		for(MapAreaRenderData renderData : mapAreaRenderer.getSavableRenderData())
		{
			if(getArea(renderData.getAreaId()).getSettings().getRenderSettings().resetsOnDisable()) //If this map should reset on disable, clear it.
			{
				renderData.resetPixels();
			}

			rendererSaveConfig.set(Integer.toString(renderData.getAreaId()), renderData.serialize());
			renderData.setShouldSave(false);
		}

		try {
			rendererSaveConfig.save(rendererSaveFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	MapAreaSettings loadSettingsForArea(int id)
	{
		ConfigurationSection section = areaSettingsConfig.getConfigurationSection(Integer.toString(id));
		if(section == null)
		{
			return createSettingsForArea(id);
		}
		return MapAreaSettings.deserialize(section.getValues(true));
	}

	private void saveAreaSettings()
	{
		for(MapArea area : mapAreas.values())
		{
			areaSettingsConfig.set(Integer.toString(area.getId()), area.getSettings().serialize());
		}

		try {
			areaSettingsConfig.save(areaSettingsFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private MapAreaSettings createSettingsForArea(int id)
	{
		MapAreaSettings settings = new MapAreaSettings(true, "", new MapAreaRenderSettings(MapCursor.Type.WHITE_POINTER));
		areaSettingsConfig.set(Integer.toString(id), settings.serialize());
		try {
			areaSettingsConfig.save(areaSettingsFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return settings;
	}
}
