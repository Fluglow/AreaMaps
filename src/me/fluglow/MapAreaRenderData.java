package me.fluglow;

import com.google.common.primitives.Ints;
import com.sk89q.worldedit.bukkit.selections.Polygonal2DSelection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.craftbukkit.libs.jline.internal.Nullable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapAreaRenderData implements ConfigurationSerializable {
	private final int areaId;
	private byte[] rendererColors;
	private byte[] pixelsToDarken;
	private Polygonal2DSelection area;

	private Location areaMiddle;

	private final double blocksPerPixel;
	private final int pixelUpdateRadius;
	private final int ceilBPP;

	private boolean shouldSave;
	private boolean allPixelsSet;
	private MapAreaRenderData(int areaId, @Nullable byte[] rendererColors, @Nullable byte[] pixelsToDarken, Polygonal2DSelection area, boolean allPixelsSet)
	{
		Vector[] corners = new Vector[] {
				area.getMinimumPoint().toVector(), area.getMaximumPoint().toVector()
		};
		Vector mid = corners[1].add(corners[0]).divide(new Vector(2, 0, 2));
		mid.setY(0);
		areaMiddle = mid.toLocation(area.getWorld());

		this.areaId = areaId;
		this.area = area;

		int blockUpdateRadius = getBlockUpdateRadius();
		int maxDistance = area.getLength() > area.getWidth() ? area.getLength() : area.getWidth(); //Take the highest value
		blocksPerPixel = (float) maxDistance / 128;
		ceilBPP = (int)Math.ceil(blocksPerPixel);
		pixelUpdateRadius = Ints.constrainToRange((int)Math.ceil(blockUpdateRadius/blocksPerPixel), 1, 128); //How many pixels from the player should be updated on the map. constrain to 1-128 since nothing else would make sense.
		shouldSave = rendererColors == null || pixelsToDarken == null;

		this.rendererColors = rendererColors != null ? rendererColors : new byte[MapAreaRenderer.MAP_PIXEL_ARRAY_SIZE];
		this.pixelsToDarken = pixelsToDarken != null ? pixelsToDarken : getBytesToDarken();

		this.allPixelsSet = allPixelsSet;

	}

	private int getBlockUpdateRadius()
	{
		int radius = JavaPlugin.getPlugin(AreaMaps.class).getConfig().getInt("block_update_radius");
		return radius < 0 ? 0 : radius; //Negative value check
	}

	double getBlocksPerPixel() {
		return blocksPerPixel;
	}

	Location getMiddle() {
		return areaMiddle;
	}

	boolean shouldSave()
	{
		return shouldSave;
	}

	void setShouldSave(boolean bool)
	{
		this.shouldSave = bool;
	}

	void resetPixels()
	{
		rendererColors = new byte[MapAreaRenderer.MAP_PIXEL_ARRAY_SIZE];
		allPixelsSet = false;
		pixelsToDarken = getBytesToDarken();
		shouldSave = true;
	}

	boolean arePixelsSet() {
		return allPixelsSet;
	}

	private void setPixelsSet(boolean allPixelsSet) {
		this.allPixelsSet = allPixelsSet;
	}

	void checkAllPixelsSet()
	{
		boolean set = true;
		for(byte b : rendererColors)
		{
			if(b == 0)
			{
				set = false;
				break;
			}
		}
		setPixelsSet(set);
	}

	int getCeilBPP() {
		return ceilBPP;
	}

	int getPixelUpdateRadius() {
		return pixelUpdateRadius;
	}

	byte[] getRendererColors() {
		return rendererColors;
	}

	void setRendererColors(byte[] rendererColors, boolean overwriteSet) {
		if(overwriteSet)
		{
			this.rendererColors = rendererColors;
			return;
		}
		for(int i = 0; i < this.rendererColors.length; i++)
		{
			if(this.rendererColors[i] == 0 && rendererColors[i] != 0)
			{
				this.rendererColors[i] = rendererColors[i];
			}
		}
	}

	byte[] getPixelsToDarken() {
		return pixelsToDarken;
	}
	
	int getAreaId() {
		return areaId;
	}

	private byte[] getBytesToDarken()
	{
		byte[] pixels = new byte[MapAreaRenderer.MAP_PIXEL_ARRAY_SIZE];
		for(int x = 0; x < 128; ++x) {
			for (int y = 0; y < 128; ++y) {
				int index = y * 128 + x;
				Location mid = areaMiddle.clone();
				Location pixelLocation = mid.add(x*blocksPerPixel-64*blocksPerPixel, 0, y*blocksPerPixel-64*blocksPerPixel);
				if(!area.contains(pixelLocation))
				{
					pixels[index] = -1;
				}
			}
		}
		return pixels;
	}

	static MapAreaRenderData fromMapArea(MapArea area)
	{
		return new MapAreaRenderData(area.getId(), null, null, area.getSelection(), false);
	}


	@Override
	public Map<String, Object> serialize()
	{
		LinkedHashMap<String, Object> serialized = new LinkedHashMap<>(); //Linked so we can manage the configuration file value order

		serialized.put("renderercolors", ByteArrayCompressor.compress(rendererColors));
		serialized.put("pixelstodarken", ByteArrayCompressor.compress(pixelsToDarken));
		serialized.put("allpixelsset", allPixelsSet);
		return serialized;
	}

	static MapAreaRenderData deserialize(Map<String, Object> data, String idKey, MapManager mapManager) //Deserialize from config
	{
		int areaId = Integer.parseInt(idKey);
		boolean allPixelsSet = (boolean)data.get("allpixelsset");
		byte[] rendererColors = ByteArrayCompressor.decompress((byte[])data.get("renderercolors"));
		byte[] pixelsToDarken = ByteArrayCompressor.decompress((byte[])data.get("pixelstodarken"));
		MapArea mapArea = mapManager.getArea(areaId);
		if(mapArea == null)
		{
			Bukkit.getLogger().warning("[AreaMaps] Tried to load map renderer for area id " + areaId + " but the map was not found!");
			Bukkit.getLogger().info("[AreaMaps] To avoid accidental data loss, the renderer will not be removed from the configuration file.");
			Bukkit.getLogger().info("[AreaMaps] If you will never use the map again, please remove map renderer with id " + areaId + " from your area renderer configuration file.");
			return null;
		}
		Polygonal2DSelection selection = mapArea.getSelection();
		return new MapAreaRenderData(areaId, rendererColors, pixelsToDarken, selection, allPixelsSet);
	}
}
