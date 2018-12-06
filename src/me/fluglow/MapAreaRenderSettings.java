package me.fluglow;

import org.apache.commons.lang3.EnumUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.map.MapCursor;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapAreaRenderSettings implements ConfigurationSerializable {
	private MapCursor.Type playerCursor;

	private boolean neverFinished = false;
	private boolean resetOnDisable = false;
	private boolean darkenPixels = true;
	private boolean showOtherPlayers;
	private MapCursor.Type otherPlayerCursor;

	MapAreaRenderSettings(MapCursor.Type ownerCursorType)
	{
		this.playerCursor = ownerCursorType;
		otherPlayerCursor = ownerCursorType; //Not specified so set to default
	}

	private MapAreaRenderSettings(MapCursor.Type ownerCursorType, boolean neverFinished, boolean resetOnDisable, boolean darkenPixels, boolean showOtherPlayers, MapCursor.Type otherPlayerCursor)
	{
		this.playerCursor = ownerCursorType;
		this.neverFinished = neverFinished;
		this.darkenPixels = darkenPixels;
		this.resetOnDisable = resetOnDisable;
		this.showOtherPlayers = showOtherPlayers;
		this.otherPlayerCursor = otherPlayerCursor;
	}


	@Override
	public Map<String, Object> serialize()
	{
		LinkedHashMap<String, Object> serialized = new LinkedHashMap<>(); //Linked so we can manage the configuration file value order
		serialized.put("playercursor", playerCursor.toString());
		serialized.put("neverfinished", neverFinished);
		serialized.put("resetondisable", resetOnDisable);
		serialized.put("darkenpixels", darkenPixels);
		serialized.put("showotherplayers", showOtherPlayers);
		serialized.put("otherplayercursor", otherPlayerCursor.toString());
		return serialized;
	}


	static MapAreaRenderSettings deserialize(Map<String, Object> arg) //Deserialize from config
	{
		MapCursor.Type type = loadPlayerCursor(arg.get("playercursor"));
		boolean neverFinished = (boolean)arg.get("neverfinished");
		boolean resetOnDisable = (boolean)arg.get("resetondisable");
		boolean darkenPixels = (boolean)arg.get("darkenpixels");
		boolean showOtherPlayers = (boolean)arg.get("showotherplayers");
		MapCursor.Type otherPlayer = loadPlayerCursor(arg.get("otherplayercursor"));
		return new MapAreaRenderSettings(type, neverFinished, resetOnDisable, darkenPixels, showOtherPlayers, otherPlayer);
	}


	@SuppressWarnings("deprecation") //MapCursor constructor is deprecated
	private static MapCursor.Type loadPlayerCursor(Object cursor)
	{
		if(cursor instanceof Integer)
		{
			 byte b = ((Integer) cursor).byteValue();
			MapCursor.Type cursorType = MapCursor.Type.byValue(b);
			if(cursorType == null)
			{
				Bukkit.getLogger().warning("[AreaMaps] Could not load cursor with value " + b + " because it's invalid! Falling back to default cursor.");
				return MapCursor.Type.WHITE_POINTER;
			}
			return cursorType;
		}
		String cursorStr = (String)cursor;
		return EnumUtils.getEnum(MapCursor.Type.class, cursorStr.replace(" ", "_").toUpperCase());
	}

	 MapCursor.Type getPlayerCursor() {
		return playerCursor;
	}

	boolean isNeverFinished() {
		return neverFinished;
	}

	public void setNeverFinished(boolean neverFinished) {
		this.neverFinished = neverFinished;
	}

	boolean resetsOnDisable() {
		return resetOnDisable;
	}

	public void setResetOnDisable(boolean resetOnDisable) {
		this.resetOnDisable = resetOnDisable;
	}

	boolean shouldDarkenPixels()
	{
		return darkenPixels;
	}

	/* Removed until someone asks for it because it seems useless.
	public boolean showsTeammates()
	{
		return teammateCursor != null;
	}

	public void showTeammates(MapCursor.Type teammateCursor)
	{
		this.teammateCursor = teammateCursor;
	}

	public void hideTeammates()
	{
		this.teammateCursor = null;
	}
	*/

	boolean showsAllPlayers()
	{
		return showOtherPlayers;
	}

	MapCursor.Type getOtherPlayerCursor() {
		return otherPlayerCursor;
	}
}
