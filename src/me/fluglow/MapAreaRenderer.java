package me.fluglow;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multisets;
import com.google.common.primitives.Ints;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.map.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.util.*;
import java.util.List;

public class MapAreaRenderer extends MapRenderer {
	static int MAP_PIXEL_ARRAY_SIZE = 16384;
	private final WorldMap worldMap;

	private Map<Integer, MapAreaRenderData> rendererDataMap;
	private Map<UUID, Integer> playerAreas = new HashMap<>();
	private Map<UUID, MapCursor> allCursors = new HashMap<>();
	private Map<UUID, Boolean> canvasPixelsSet = new HashMap<>();
	private final boolean smoothCursors;

	private Map<Integer, ASyncMapRenderer> asyncRenderers = new HashMap<>();

	private final Map<Integer, MapAreaRenderSettings> areaRenderSettings;
	/*
	Fix issue?: Cursors freeze when another map is initialized.
	This issue seems to be client sided, since cursors freeze even when map initialization is completely blocked server-side.
	 */
	MapAreaRenderer(WorldMap worldMap, Map<Integer, MapAreaRenderData> rendererDataMap, Map<Integer, MapAreaRenderSettings> settings) {
		super(true);
		this.worldMap = worldMap;
		this.rendererDataMap = rendererDataMap;
		this.areaRenderSettings = settings;
		smoothCursors = JavaPlugin.getPlugin(AreaMaps.class).getConfig().getBoolean("smooth_map_cursors");
	}

	void addPlayerToMap(UUID playerUUID, int area)
	{
		playerAreas.put(playerUUID, area);
		canvasPixelsSet.put(playerUUID, false);
	}

	void removePlayerFromMap(UUID playerUUID)
	{
		canvasPixelsSet.remove(playerUUID);
		playerAreas.remove(playerUUID);
		allCursors.remove(playerUUID);
	}

	boolean drawWholeMap(int areaId, CommandSender sender)
	{
		MapAreaRenderData renderData = rendererDataMap.get(areaId);
		if(renderData != null && !asyncRenderers.containsKey(renderData.getAreaId()))
		{
			ASyncMapRenderer asyncRenderer = new ASyncMapRenderer(renderData, sender);
			asyncRenderer.drawMap(renderData);
			asyncRenderers.put(renderData.getAreaId(), asyncRenderer);
			sender.sendMessage(ChatColor.YELLOW + "Drawing all pixels for map area with id " + areaId + "... This will take a while.");
			return true;
		}
		return false;
	}

	public boolean hasAsyncRenderer(int areaId)
	{
		return asyncRenderers.containsKey(areaId);
	}

	void stopAsyncRenderers()
	{
		for(ASyncMapRenderer renderer : asyncRenderers.values())
		{
			if(!renderer.isDone())
			{
				Bukkit.getLogger().warning( "[AreaMaps] An area renderer's work was interrupted! The map was not fully rendered.");
			}
			renderer.interruptTask(true);
			removeAsyncRenderer(renderer.getAreaId(), false);
		}
	}

	public void stopAsyncRenderer(int id)
	{
		ASyncMapRenderer renderer = asyncRenderers.get(id);
		if(renderer == null) return;
		renderer.interruptTask(false);
		removeAsyncRenderer(id, false);
	}

	private void removeAsyncRenderer(int areaId, boolean notifyInitiator)
	{
		if(notifyInitiator) asyncRenderers.get(areaId).getInitiator().sendMessage(ChatColor.GREEN + "Done drawing pixels for area with id " + areaId + "!");
		asyncRenderers.remove(areaId);
		rendererDataMap.get(areaId).setShouldSave(true);
	}


	public boolean isFinished(int areaId) {
		MapAreaRenderData renderData = rendererDataMap.get(areaId);
		return renderData != null && renderData.arePixelsSet();
	}

	@Override
	public void render(MapView mapView, MapCanvas canvas, Player canvasOwner)
	{
		UUID playerUUID = canvasOwner.getUniqueId();
		Integer playerArea = playerAreas.get(playerUUID);
		if(playerArea == null) return;

		MapAreaRenderData areaRenderData = rendererDataMap.get(playerArea);
		if(areaRenderData == null) return; //areaRenderData can be null if the server reloads.
		int playerMapPosX = MathHelper.floor((canvasOwner.getLocation().getX() - areaRenderData.getMiddle().getX()) / areaRenderData.getBlocksPerPixel()) + 64;
		int playerMapPosZ = MathHelper.floor((canvasOwner.getLocation().getZ() - areaRenderData.getMiddle().getZ()) / areaRenderData.getBlocksPerPixel()) + 64;

		/*
		Tell WorldMap that there was a pixel change, making it send map packets.
		If there are no changes, map packets aren't sent every tick and cursors look laggy.
		 */
		if(smoothCursors) worldMap.flagDirty(64, 64); //Use sendMapPacket if any issues come up because of this.
		//sendMapPacket(canvasOwner, canvas.getCursors());

		int areaId = areaRenderData.getAreaId();
		MapAreaRenderSettings renderSettings = areaRenderSettings.get(areaId);
		/*
		updateCursor compares old cursor values to the new ones that we want to set.
		If there was no movement, the method returns false.
		If the last argument is false, the method doesn't check for changes and returns false.
		Since we don't want to update the map if map pixels are set, we pass the arePixelsSet value.
		This is leads to a noticeable (>20%) CPU usage change on big maps because updateMap will not be called :)
		Smaller maps may take a small performance hit, but it's not noticeable.
		 */
		boolean checkMovement = !areaRenderData.arePixelsSet() || renderSettings.isNeverFinished();
		boolean shouldUpdate;
		if(!renderSettings.showsAllPlayers())
		{
			shouldUpdate = updateCursor(canvasOwner.getUniqueId(), renderSettings, canvasOwner.getLocation().getYaw(), canvas, playerMapPosX, playerMapPosZ, checkMovement);
		}
		else
		{
			shouldUpdate = updateAllCursorsOnCanvas(canvasOwner.getUniqueId(), renderSettings, canvasOwner.getLocation().getYaw(), canvas, playerMapPosX, playerMapPosZ, checkMovement);
		}


		if(!asyncRenderers.containsKey(areaId))
		{
			if(!shouldUpdate) //shouldUpdate is false when no one moves or if the map is rendered.
			{
				if(canvasPixelsSet.get(playerUUID))
				{
					return;
				}
			}
			else
			{
				updateMap(areaRenderData, playerMapPosX, playerMapPosZ);
			}
		}
		else
		{
			ASyncMapRenderer asyncRenderer = asyncRenderers.get(areaId);
			areaRenderData.setRendererColors(asyncRenderer.getCurrentWork(), false);
			if(asyncRenderer.isDone())
			{
				removeAsyncRenderer(areaId, true);
			}
		}
		byte[] rendererColors = areaRenderData.getRendererColors();
		byte[] pixelsToDarken = areaRenderData.getPixelsToDarken();
		boolean allSet = true;

		for(int x = 0; x < 128; ++x)
		{
			for(int y = 0; y < 128; ++y)
			{
				int index = y * 128 + x;
				byte b = rendererColors[index];
				byte bc = canvas.getPixel(x, y);
				boolean shouldDarken = pixelsToDarken != null && pixelsToDarken[index] == -1 && b != 0 && renderSettings.shouldDarkenPixels();
				if(b == bc && !shouldDarken) continue; //If there would be no change, don't do anything
				allSet = false;
				if(!shouldDarken)
				{
					canvas.setPixel(x, y, b);
					continue;
				}

				byte darkened = getDarkenedByte(b);
				rendererColors[index] = darkened;
				canvas.setPixel(x, y, darkened);
				pixelsToDarken[index] = 1;
			}
		}
		canvasPixelsSet.put(playerUUID, allSet); //If nothing was changed, add player to canvasPixelsSet. If a change happens on the map, this will be set to false.
	}

	@SuppressWarnings("deprecation") //Cursor constructor in deprecated
	private MapCursor getCursor(UUID player)
	{
		return allCursors.computeIfAbsent(player, k -> new MapCursor((byte) 0, (byte) 0, (byte) 0, (byte) 0, true)); //Thanks IntelliJ :^)
	}

	@SuppressWarnings({"ConstantConditions", "unchecked"})
	private void updateMap(MapAreaRenderData renderData, int entityMapX, int entityMapZ) {
		org.bukkit.World world = renderData.getMiddle().getWorld();
		World nmsWorld = ((CraftWorld)world).getHandle();
		//Actual scale is the minimum size in blocks, width or height of the area.
		double mapCenterX = renderData.getMiddle().getX();
		double mapCenterZ = renderData.getMiddle().getZ();
		int pixelUpdateRadius = renderData.getPixelUpdateRadius();
		double blocksPerPixel = renderData.getBlocksPerPixel();
		int ceilBPP = renderData.getCeilBPP();
		for (int drawablePixelX = entityMapX - pixelUpdateRadius + 1; drawablePixelX < entityMapX + pixelUpdateRadius; ++drawablePixelX) //Loop for pixels within the update radius
		{
			double lastHeightPerPixel = 0.0D;
			for (int drawablePixelZ = entityMapZ - pixelUpdateRadius - 1; drawablePixelZ < entityMapZ + pixelUpdateRadius; ++drawablePixelZ)
			{
				if (!(drawablePixelX >= 0 && drawablePixelZ >= 0 && drawablePixelX < 128 && drawablePixelZ < 128)) //If drawable pixel is within map bounds and color array bounds
				{
					continue;
				}
				byte color = renderData.getRendererColors()[drawablePixelX + drawablePixelZ * 128];

				double drawableBlockPosX = (mapCenterX / blocksPerPixel + drawablePixelX - 64) * blocksPerPixel;
				double drawableBlockPosZ = (mapCenterZ / blocksPerPixel + drawablePixelZ - 64) * blocksPerPixel;

				//highestBlock.setY(world.getHighestBlockYAt(highestBlock));
				/*
				int drawableBlockX = highestBlock.getBlockX(); //Get BlockX and Z
				int drawableBlockZ = highestBlock.getBlockZ();

				if (!world.isChunkLoaded(drawableBlockX >> 4, drawableBlockZ >> 4))
				{
					continue;
				}
				*/

				int renderSquarePosX = drawablePixelX - entityMapX;
				int renderSquarePosZ = drawablePixelZ - entityMapZ;

				//For drawing the circle pattern
				double renderSquarePosXSquared = renderSquarePosX * renderSquarePosX;
				double renderSquarePosZSquared = renderSquarePosZ * renderSquarePosZ;
				boolean isNearCircleEdge = renderSquarePosXSquared + renderSquarePosZSquared > (pixelUpdateRadius - 2) * (pixelUpdateRadius - 2);
				boolean isInsideCircle = renderSquarePosXSquared + renderSquarePosZSquared < pixelUpdateRadius * pixelUpdateRadius;
				boolean skipForPattern = (isNearCircleEdge && (drawablePixelX + drawablePixelZ & 1) == 0);

				HashMultiset hashmultiset = HashMultiset.create();
				int waterDepth = 0;
				double heightPerPixel = 0.0D;

				//Get blocks in the area that we want to draw
				BlockPosition blockPos = new BlockPosition(drawableBlockPosX, 0, drawableBlockPosZ);
				for (int blockPosX = 0; blockPosX < ceilBPP; ++blockPosX) {
					for (int blockPosZ = 0; blockPosZ < ceilBPP; ++blockPosZ) {
						blockPos = new BlockPosition(blockPos.getX(), world.getHighestBlockYAt(blockPos.getX(), blockPos.getZ()) + 1, blockPos.getZ());
						IBlockData blockData;
						if (blockPos.getY() <= 1)
						{
							blockData = Blocks.BEDROCK.getBlockData();
						}
						else
						{
							blockData = nmsWorld.getType(blockPos);

							while (blockPos.getY() > 0 && blockData.a((IBlockAccess)nmsWorld, blockPos) == MaterialMapColor.c) //While block Y is valid and the color of the block is 0.
							{
								blockPos = blockPos.a(0, -1, 0);
								blockData = nmsWorld.getType(blockPos);
							}

							//Water depth effect
							IBlockData liquidBlockData = nmsWorld.getType(blockPos);
							int liquidBlockY = blockPos.getY() - 1;
							while(liquidBlockY > 0 && liquidBlockData.getMaterial().isLiquid()) //If the selected block is liquid
							{
								liquidBlockData = nmsWorld.getType(new BlockPosition(blockPos.getX(), liquidBlockY--, blockPos.getZ()));
								waterDepth++;
							}
						}
						heightPerPixel += (double) blockPos.getY() / (blocksPerPixel * blocksPerPixel);
						hashmultiset.add(blockData.a((IBlockAccess) nmsWorld, blockPos)); //Add block material to drawable mats

						blockPos = blockPos.a(0, 0, 1);
					}
					blockPos = blockPos.a(1, 0, -ceilBPP);
				}

				if(!isInsideCircle && skipForPattern) //If we shouldn't draw the pixel, don't process anything else. Just save heightPerPixel.
				{
					lastHeightPerPixel = heightPerPixel;
					continue;
				}
				//Draw the pixel, I haven't bothered deobfuscating this since it works

				waterDepth /= blocksPerPixel * blocksPerPixel;
				double d2 = (heightPerPixel - lastHeightPerPixel) * 4.0D / (blocksPerPixel + 4) + ((double) (drawablePixelX + drawablePixelZ & 1) - 0.5D) * 0.4D;
				byte b0 = 1;
				if (d2 > 0.6D) {
					b0 = 2;
				}

				if (d2 < -0.6D) {
					b0 = 0;
				}

				/* Example of preferring a block.

				if(hashmultiset.contains(Material.STONE.r()))
				{
					hashmultiset.clear();
					hashmultiset.add(Material.STONE.r());
				}

				*/
				MaterialMapColor materialmapcolor = Iterables.getFirst(Multisets.copyHighestCountFirst(hashmultiset), MaterialMapColor.c); //Get the material color from the Multiset that was built or default to nothing.
				if (materialmapcolor == MaterialMapColor.o) {
					d2 = (double) waterDepth * 0.1D + (double) (drawablePixelX + drawablePixelZ & 1) * 0.2D;
					b0 = 1;
					if (d2 < 0.5D) {
						b0 = 2;
					}

					if (d2 > 0.9D) {
						b0 = 0;
					}
				}
				if (isInsideCircle && !skipForPattern) {
					byte mapPixel = (byte) (materialmapcolor.ad * 4 + b0);
					int index = drawablePixelX + drawablePixelZ * 128;
					if (color != mapPixel && renderData.getPixelsToDarken()[index] != 1) { //If pixel color is changing and pixel isn't darkened
						renderData.getRendererColors()[index] = mapPixel;
						refreshCanvasPixelsSet(renderData.getAreaId());
						renderData.setShouldSave(true); //Map was drawn on, save it.
						renderData.checkAllPixelsSet();
					}
				}
				lastHeightPerPixel = heightPerPixel;
			}

		}
	}

	private boolean updateCursor(UUID canvasOwner, MapAreaRenderSettings settings, float playerYaw, MapCanvas canvas, int x, int y, boolean checkMovement)
	{
		boolean[][] oldPositions = checkMovement ? new boolean[256][256] : null;
		while(canvas.getCursors().size() > 0) {
			MapCursor cursor = canvas.getCursors().getCursor(0);
			if(checkMovement) oldPositions[cursor.getX()+128][cursor.getY()+128] = true;
			canvas.getCursors().removeCursor(cursor);
		}

		boolean shouldUpdateMap = false;
		MapCursorCollection newCursors = new MapCursorCollection();
		MapCursor cursor = getCursor(canvasOwner);
		x = Ints.constrainToRange(x*2-128, -127, 127);
		y = Ints.constrainToRange(y*2-128, -127, 127);
		cursor.setX((byte)x);
		cursor.setY((byte)y);

		/*
		Cursor notes:
		RED_MARKER Is reversed, flip rotation when using.
		 */
		cursor.setType(settings.getPlayerCursor());
		if(cursor.getType() == MapCursor.Type.RED_MARKER)
		{
			playerYaw = (playerYaw + 180)%360;
		}

		cursor.setDirection(getDirectionByte(playerYaw));

		allCursors.put(canvasOwner, cursor);

		newCursors.addCursor(cursor);
		if(checkMovement && !oldPositions[x+128][y+128])
		{
			shouldUpdateMap = true;
		}
		canvas.setCursors(newCursors);
		return shouldUpdateMap;
	}

	@SuppressWarnings("deprecation")
	private boolean updateAllCursorsOnCanvas(UUID canvasOwner, MapAreaRenderSettings settings, float playerYaw, MapCanvas canvas, int x, int y, boolean checkMovement)
	{
		if(!settings.showsAllPlayers()) return false; //Only called when we should show all players on map
		boolean[][] oldPositions = checkMovement ? new boolean[256][256] : null;
		while(canvas.getCursors().size() > 0) {
			MapCursor cursor = canvas.getCursors().getCursor(0);
			if(checkMovement) oldPositions[cursor.getX()+128][cursor.getY()+128] = true;
			canvas.getCursors().removeCursor(cursor);
		}
		boolean shouldUpdateMap = false;
		MapCursorCollection newCursors = new MapCursorCollection();
		int ownerArea = playerAreas.get(canvasOwner);

		MapCursor ownerCursor = getCursor(canvasOwner);
		x = Ints.constrainToRange(x*2-128, -127, 127);
		y = Ints.constrainToRange(y*2-128, -127, 127);
		if(checkMovement && !oldPositions[x+128][y+128])
		{
			shouldUpdateMap = true;
		}
		ownerCursor.setX((byte)x);
		ownerCursor.setY((byte)y);

		ownerCursor.setType(settings.getPlayerCursor());
		if(ownerCursor.getType() == MapCursor.Type.RED_MARKER)
		{
			playerYaw = (playerYaw + 180)%360;
		}
		ownerCursor.setDirection(getDirectionByte(playerYaw));

		newCursors.addCursor(ownerCursor);


		org.bukkit.World ownerWorld = Bukkit.getPlayer(canvasOwner).getWorld();

		for(Map.Entry<UUID, MapCursor> cursorEntry : allCursors.entrySet())
		{
			UUID cursorOwnerUUID = cursorEntry.getKey();
			if(cursorOwnerUUID == canvasOwner || playerAreas.get(cursorOwnerUUID) != ownerArea) continue; //Skip if player is owner or cursor is not on this map.

			org.bukkit.World cursorOwnerWorld = Bukkit.getPlayer(cursorOwnerUUID).getWorld();
			if(!cursorOwnerWorld.equals(ownerWorld)) continue; //Skip cursor if it's in a different world.
			MapCursor cursor = cursorEntry.getValue();

			MapCursor newCursor = new MapCursor(cursor.getX(), cursor.getY(), cursor.getDirection(), (byte)0, cursor.isVisible()); //type 0 since we set it later
			MapCursor.Type type = settings.getOtherPlayerCursor();

			//RED_MARKER check, this cursor is reversed.
			if(type != MapCursor.Type.RED_MARKER && cursor.getType() == MapCursor.Type.RED_MARKER)
			{
				byte newDir = (byte)((cursor.getDirection()+8)%16);
				newCursor.setDirection(newDir);
			}
			newCursor.setType(type);

			allCursors.put(cursorOwnerUUID, newCursor);

			newCursors.addCursor(newCursor);
			if(checkMovement && !oldPositions[newCursor.getX()+128][newCursor.getY()+128])
			{
				shouldUpdateMap = true;
			}
		}
		canvas.setCursors(newCursors);
		return shouldUpdateMap;
	}

	private void refreshCanvasPixelsSet(int areaId)
	{
		for(UUID key : canvasPixelsSet.keySet())
		{
			if(playerAreas.get(key) == areaId)
			{
				canvasPixelsSet.put(key, false);
			}
		}
	}

	@SuppressWarnings("deprecation") //MapPalette.matchColor is deprecated.
	private byte getDarkenedByte(byte b)
	{
		Color color = new Color(MapPalette.getColor(b).getRGB());
		float shadeFactor = 2.5f;

		int[] colors = new int[] {
				Math.round(color.getRed()/(shadeFactor)), Math.round(color.getGreen()/(shadeFactor)), Math.round(color.getBlue()/(shadeFactor))
		};

		return MapPalette.matchColor(colors[0], colors[1], colors[2]);
	}

	void resetAreaMap(int id)
	{
		rendererDataMap.get(id).resetPixels();
		refreshCanvasPixelsSet(id);
		rendererDataMap.get(id).setShouldSave(true);
	}

	List<MapAreaRenderData> getSavableRenderData()
	{
		List<MapAreaRenderData> renderData = new ArrayList<>();
		for(MapAreaRenderData data : rendererDataMap.values())
		{
			if(data.shouldSave())
			{
				renderData.add(data);
			}
		}
		return renderData;
	}

	void addAreaToRenderer(MapArea area)
	{
		rendererDataMap.put(area.getId(), MapAreaRenderData.fromMapArea(area));
		areaRenderSettings.put(area.getId(), new MapAreaRenderSettings(MapCursor.Type.WHITE_POINTER));
	}

	void removeAreaFromRenderer(int areaId)
	{
		rendererDataMap.remove(areaId);
		areaRenderSettings.remove(areaId);
	}

	Map<UUID, Integer> getPlayerAreas() {
		return playerAreas;
	}

	private byte getDirectionByte(float yaw) {
		double rot = (yaw - 90) % 360;
		if (rot < 0) {
			rot += 360.0;
		}
		return getDirection(rot);
	}

	@SuppressWarnings("ConstantConditions")
	private byte getDirection(double rot) { //Get cardinal direction id for cursor
		if (0 <= rot && rot < 11.25) {
			return 4;
		} else if (11.25 <= rot && rot < 33.75) {
			return 5;
		} else if (33.75 <= rot && rot < 56.25) {
			return 6;
		} else if (56.25 <= rot && rot < 78.75) {
			return 7;
		} else if (78.75 <= rot && rot < 101.25) {
			return 8;
		} else if (101.25 <= rot && rot < 123.75) {
			return 9;
		} else if (123.75 <= rot && rot < 146.25) {
			return 10;
		} else if (146.25 <= rot && rot < 168.75) {
			return 11;
		} else if (168.75 <= rot && rot < 191.25) {
			return 12;
		} else if (191.25 <= rot && rot < 213.75) {
			return 13;
		} else if (213.75 <= rot && rot < 236.25) {
			return 14;
		} else if (236.25 <= rot && rot < 258.75) {
			return 15;
		} else if (258.75 <= rot && rot < 281.25) {
			return 0;
		} else if (281.25 <= rot && rot < 303.75) {
			return 1;
		} else if (303.75 <= rot && rot < 326.25) {
			return 2;
		} else if (326.25 <= rot && rot < 348.75) {
			return 3;
		} else if (348.75 <= rot && rot < 361) {
			return 4;
		}
		return 0;
	}

	/*
	private void sendMapPacket(Player player, MapCursorCollection cursors)
	{
		Collection<MapIcon> icons = new ArrayList<>();
		for(int i = 0; i < cursors.size(); i++)
		{
			MapCursor cursor = cursors.getCursor(i);
			icons.add(new MapIcon(MapIcon.Type.PLAYER, cursor.getX(), cursor.getY(), cursor.getDirection()));
		}
		PacketPlayOutMap mapPacket = new PacketPlayOutMap(worldMap.mapView.getId(), (byte)0, false, icons, rendererDataMap.get(playerAreas.get(player.getUniqueId())).getRendererColors(), 0,0,0,0);
		((CraftPlayer)player).getHandle().playerConnection.sendPacket(mapPacket);
	}
*/
}