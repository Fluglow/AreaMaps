package me.fluglow;

import me.fluglow.areamapscommands.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class AreaMaps extends JavaPlugin {

	private MapManager mapManager;
	private CreationSessionManager sessionManager;
	private PlayerAreaTracker areaTracker;

	//Register configuration serializables
	static {
		ConfigurationSerialization.registerClass(MapArea.class, "MapArea");
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onEnable() {
		getLogger().info("Loading map data...");
		mapManager = createMapAreaManager();
		getLogger().info("Map data loaded!");
		areaTracker = new PlayerAreaTracker(mapManager);
		sessionManager = new CreationSessionManager();
		PluginManager manager = getServer().getPluginManager();
		manager.registerEvents(sessionManager, this);
		manager.registerEvents(areaTracker, this);
		manager.registerEvents(mapManager, this);
		registerCommands();
		saveDefaultConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();

		for(Player player : getServer().getOnlinePlayers()) //Refresh online players' maps so they don't get left without maps on reload
		{
			areaTracker.changePlayerArea(MapManager.UNKNOWN_AREA, mapManager.getLocationArea(player.getLocation()), player);
		}
	}

	@Override
	public void onDisable() {
		mapManager.getMapRenderer().stopAsyncRenderers();
		mapManager.saveRenderData();

		if(areaTracker == null) return; //AreaTracker failed to initialize, no need to do anything else.
		if(areaTracker.bossbarsEnabled)
		{
			areaTracker.clearBossbars();
		}

		for(Player player : getServer().getOnlinePlayers())
		{
			areaTracker.changePlayerArea(mapManager.getLocationArea(player.getLocation()), MapManager.UNKNOWN_AREA, player);
		}
	}

	private void registerCommands()
	{
		getCommand("areamaps").setExecutor(new HelpCommand());
		getCommand("newmaparea").setExecutor(new NewMapAreaCommand(sessionManager, areaTracker, mapManager));
		getCommand("deletemaparea").setExecutor(new DeleteMapAreaCommand(mapManager, areaTracker));
		getCommand("showareaborder").setExecutor(new ShowAreaBorderCommand(mapManager));
		getCommand("areateleport").setExecutor(new AreaTeleportCommand(mapManager));
		getCommand("resetmap").setExecutor(new ResetMapCommand(mapManager));
		getCommand("listareas").setExecutor(new ListAreasCommand(mapManager));
		getCommand("drawfullmap").setExecutor(new DrawFullMapCommand(mapManager));
		getCommand("canceldrawing").setExecutor(new CancelDrawingCommand(mapManager));

	}


	private MapManager createMapAreaManager()
	{
		File areaSaveFile = createConfigFile("Areas.yml");
		File rendererSaveFile = createConfigFile("AreaRenderers.yml");
		File areaSettingsFile = createConfigFile("AreaSettings.yml");

		return new MapManager(this, areaSaveFile, rendererSaveFile, areaSettingsFile);
	}


	@SuppressWarnings("ResultOfMethodCallIgnored")
	private File createConfigFile(String name)
	{
		File file = new File(getDataFolder(), name);
		if(!file.exists())
		{
			file.getParentFile().mkdirs();
			saveResource(name, false);
		}
		return file;
	}
}