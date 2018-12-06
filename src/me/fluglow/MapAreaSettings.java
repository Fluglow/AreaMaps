package me.fluglow;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapAreaSettings implements ConfigurationSerializable {
	private boolean enabled;
	private String worldNameRegex;
	private MapAreaRenderSettings renderSettings;

	MapAreaSettings(boolean enabled, String worldNameRegex, MapAreaRenderSettings renderSettings)
	{
		this.enabled = enabled;
		this.worldNameRegex = worldNameRegex;
		this.renderSettings = renderSettings;
	}

	@Override
	public Map<String, Object> serialize() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("enabled", enabled);
		values.put("worldnameregex", worldNameRegex);
		values.put("rendersettings", renderSettings.serialize());
		return values;
	}

	static MapAreaSettings deserialize(Map<String, Object> arg) //Deserialize from config
	{
		boolean enabled = (boolean)arg.get("enabled");
		String worldNameRegex = (String)arg.get("worldnameregex");
		ConfigurationSection section = (ConfigurationSection)arg.get("rendersettings");
		MapAreaRenderSettings renderSettings = MapAreaRenderSettings.deserialize(section.getValues(true));
		return new MapAreaSettings(enabled, worldNameRegex, renderSettings);
	}

	MapAreaRenderSettings getRenderSettings() {
		return renderSettings;
	}

	String getWorldRegex() {
		return worldNameRegex;
	}

	public void SetWorldRegex(String multipleWorlds) {
		this.worldNameRegex = multipleWorlds;
	}

	boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
