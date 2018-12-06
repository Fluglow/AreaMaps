package me.fluglow;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multisets;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class ASyncMapRenderer {
	private byte[] currentWork;
	private MapAreaRenderData renderData;
	private boolean isDone = false;
	private BukkitTask task;
	private final BukkitScheduler scheduler;
	private final Plugin mapsPlugin;
	private final CommandSender initiator;

	private final int maxRPT = 2048;

	private final BlockPosition firstBlock;

	private int heightsSizeLimit = 2048;

	private boolean shouldCancel = false;

	private BlockDataArea lastDataArea;

	ASyncMapRenderer(MapAreaRenderData renderData, CommandSender initiator)
	{
		this.renderData = renderData;
		currentWork = renderData.getRendererColors().clone();
		this.initiator = initiator;
		scheduler = Bukkit.getScheduler();
		mapsPlugin = JavaPlugin.getPlugin(AreaMaps.class);

		double firstBlockX = (renderData.getMiddle().getX() / renderData.getBlocksPerPixel() - 64) * renderData.getBlocksPerPixel();
		double firstBlockZ = (renderData.getMiddle().getZ() / renderData.getBlocksPerPixel() - 64) * renderData.getBlocksPerPixel();
		firstBlock = new BlockPosition(firstBlockX, 0, firstBlockZ);
		if(renderData.getCeilBPP() > heightsSizeLimit)
		{
			heightsSizeLimit = renderData.getCeilBPP();
		}
	}

	int getAreaId()
	{
		return renderData.getAreaId();
	}

	byte[] getCurrentWork() {
		return currentWork.clone();
	}

	boolean isDone() {
		return isDone;
	}

	CommandSender getInitiator() {
		return initiator;
	}

	void interruptTask(boolean force)
	{
		if(force)
		{
			task.cancel();
		}
		else
		{
			shouldCancel = true;
		}

	}

	@SuppressWarnings({"ConstantConditions", "unchecked"})
	void drawMap(MapAreaRenderData renderData) {
		org.bukkit.World world = renderData.getMiddle().getWorld();
		World nmsWorld = ((CraftWorld)world).getHandle();

		double mapCenterX = renderData.getMiddle().getX();
		double mapCenterZ = renderData.getMiddle().getZ();
		double blocksPerPixel = renderData.getBlocksPerPixel();
		int ceilBPP = renderData.getCeilBPP();

		Runnable updaterRunnable = () ->
		{
			int pixelProgress = 0;
			initiator.sendMessage("Loading block heightmap...");
			Integer[][] blockHeights = getSyncBlockHeights(world, firstBlock);
			initiator.sendMessage("Done! Loading pixels...");
			int heightMapOffsetX = 0;

			for (int drawablePixelX = 0; drawablePixelX < 128; drawablePixelX++) //Loop for pixels within the update radius
			{
				int heightMapOffsetZ = 0;
				double lastHeightPerPixel = 0.0D;
				for (int drawablePixelZ = 0; drawablePixelZ < 128; drawablePixelZ++)
				{
					if(shouldCancel)
					{
						String cancelMessage = "The drawing of the map area with id " + getAreaId() + " has been canceled.";
						if(Bukkit.getPlayer(initiator.getName()) == null)
						{
							Bukkit.getLogger().info("[AreaMaps]: " + cancelMessage);
						}
						else
						{
							initiator.sendMessage(ChatColor.YELLOW + cancelMessage);
						}
						task.cancel();
						return;
					}

					if (!(drawablePixelX >= 0 && drawablePixelZ >= 0 && drawablePixelX < 128 && drawablePixelZ < 128)) //If drawable pixel is within map bounds and color array bounds
					{
						continue;
					}
					int index = drawablePixelX + drawablePixelZ * 128;

					int nextIndex = drawablePixelX == 127 ? (drawablePixelZ+1) * 128 : drawablePixelX + 1 + drawablePixelZ*128;
					if(nextIndex < currentWork.length && currentWork[index] != 0 && currentWork[nextIndex] != 0) //If the next pixel is set too, we can skip this pixel because we don't need it's height for the next one.
					{
						continue;
					}

					double drawableBlockPosX = (mapCenterX / blocksPerPixel + drawablePixelX - 64) * blocksPerPixel;
					double drawableBlockPosZ = (mapCenterZ / blocksPerPixel + drawablePixelZ - 64) * blocksPerPixel;
					HashMultiset hashmultiset = HashMultiset.create();
					int waterDepth = 0;
					double heightPerPixel = 0.0D;

					BlockPosition blockPos = new BlockPosition(drawableBlockPosX, 0, drawableBlockPosZ);
					BlockDataArea blockDataArea = new BlockDataArea(ceilBPP, ceilBPP, blockPos.getX(), blockPos.getZ());

					int blockMatrixX = blockPos.getX() - firstBlock.getX() - heightMapOffsetX;
					int blockMatrixZ = blockPos.getZ() - firstBlock.getZ() - heightMapOffsetZ;

					if(blockMatrixX + (ceilBPP-1) > blockHeights.length-1)
					{
						blockHeights = getSyncBlockHeights(world, blockPos);
						heightMapOffsetX += blockMatrixX;
					}
					else if(blockMatrixZ + (ceilBPP-1) > blockHeights[blockMatrixX].length-1)
					{
						blockHeights = getSyncBlockHeights(world, blockPos);
						heightMapOffsetZ += blockMatrixZ;
					}


					if(lastDataArea != null && lastDataArea.getFirstPosX() == blockPos.getX() && lastDataArea.getFirstPosZ() == blockPos.getZ())
					{
						blockDataArea = lastDataArea;
					}
					else
					{
						blockDataArea.setBlockData(getSyncAllBlocksDown(nmsWorld, blockHeights,60, blockPos.getX(), blockPos.getZ(), heightMapOffsetX, heightMapOffsetZ));
						lastDataArea = blockDataArea;
					}
					for (int blockPosX = 0; blockPosX < ceilBPP; ++blockPosX) {
						for (int blockPosZ = 0; blockPosZ < ceilBPP; ++blockPosZ) {
							blockMatrixX = blockPos.getX() - firstBlock.getX() - heightMapOffsetX;
							blockMatrixZ = blockPos.getZ() - firstBlock.getZ() - heightMapOffsetZ;

							blockPos = new BlockPosition(blockPos.getX(), blockHeights[blockMatrixX][blockMatrixZ] + 1, blockPos.getZ());
							IBlockData blockData;
							if (blockPos.getY() <= 1)
							{
								blockData = Blocks.BEDROCK.getBlockData();
							}
							else
							{
								IBlockData[] blocksDown = blockDataArea.getBlockData(blockPosX, blockPosZ);

								blockData = blocksDown[0];
								int dataIndex = 0;
								while (blockPos.getY() > 0 && blockData.a((IBlockAccess)nmsWorld, blockPos) == MaterialMapColor.c) //While the color of the block is 0 and block Y is valid. We can get MaterialMapColor with nulls since they aren't used in server code.
								{
									if(dataIndex == blocksDown.length || blocksDown[dataIndex+1] == null)
									{
										blocksDown = getSyncBlocksDown(nmsWorld, blockPos, 10);
										dataIndex = 0;
									}
									blockPos = blockPos.a(0, -1, 0);
									blockData = blocksDown[dataIndex];
									dataIndex++;
								}


								IBlockData liquidBlockData = blocksDown[dataIndex];
								BlockPosition liquidBlockPos = blockPos.a(0, -1, 0);
								while(liquidBlockPos.getY() > 0 && liquidBlockData.getMaterial().isLiquid()) //If the selected block is liquid
								{
									if(dataIndex == blocksDown.length || blocksDown[dataIndex] == null) //If blocksDown limit is hit
									{
										blocksDown = getSyncBlocksDown(nmsWorld, liquidBlockPos, 10); //Get more blocks
										dataIndex = 0; //Start over with index
									}

									liquidBlockPos = liquidBlockPos.a(0, -1, 0);
									liquidBlockData = blocksDown[dataIndex];
									dataIndex++;
									waterDepth++;
								}
							}
							heightPerPixel += (double) blockPos.getY() / (blocksPerPixel * blocksPerPixel);
							hashmultiset.add(blockData.a((IBlockAccess) nmsWorld, blockPos)); //Add block material to drawable mats

							blockPos = blockPos.a(0, 0, 1);
						}
						blockPos = blockPos.a(1, 0, -ceilBPP);
					}

					if(currentWork[index] != 0) //If the pixel is already set. This can't be done before the depth loop because we'd get incorrect colors on already rendered parts of the map.
					{
						pixelProgress += 1;
						lastHeightPerPixel = heightPerPixel; //Save heights per pixel.
						continue;
					}

					waterDepth /= blocksPerPixel * blocksPerPixel;
					double d2 = (heightPerPixel - lastHeightPerPixel) * 4.0D / (blocksPerPixel + 4) + ((double) (drawablePixelX + drawablePixelZ & 1) - 0.5D) * 0.4D;
					byte b0 = 1;
					if (d2 > 0.6D) {
						b0 = 2;
					}

					if (d2 < -0.6D) {
						b0 = 0;
					}

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
					byte mapPixel = (byte) (materialmapcolor.ad * 4 + b0);
					currentWork[index] = mapPixel;
					pixelProgress += 1;
					if(pixelProgress % 512 == 0)
					{
						initiator.sendMessage(ChatColor.YELLOW + "Map generation progress for area " + renderData.getAreaId() + ": " + pixelProgress + "/" + (MapAreaRenderer.MAP_PIXEL_ARRAY_SIZE));
					}
					lastHeightPerPixel = heightPerPixel;
				}
			}
			isDone = true;
		};

		task = scheduler.runTaskAsynchronously(mapsPlugin, updaterRunnable);
	}

	private void waitForFuture(Future<?> future)
	{
		while(!future.isDone())
		{
			try {
				Thread.sleep(40);
			} catch (InterruptedException ignored) {

			}
		}
	}

	@SuppressWarnings("unchecked")
	private IBlockData[][][] getSyncAllBlocksDown(World nmsWorld, Integer[][] heights, int blocksDown, int blockPosX, int blockPosZ, int heightMapOffsetX, int heightMapOffsetZ)
	{
		int ceilBPP = renderData.getCeilBPP();

		Future[][][] futureBlockData = new Future[ceilBPP][ceilBPP][];
		int callCounter = 0;
		int highestBlock = 0;
		for(int x = 0; x < ceilBPP; x++)
		{
			for(int z = 0; z < ceilBPP; z++)
			{
				int heightMapX = blockPosX - firstBlock.getX() + x - heightMapOffsetX;
				int heightMapZ = blockPosZ - firstBlock.getZ() + z - heightMapOffsetZ;
				int height = heights[heightMapX][heightMapZ] + 1;

				BlockPosition blockPos = new BlockPosition(blockPosX + x, height, blockPosZ + z);
				futureBlockData[x][z] = getFutureBlocksDown(nmsWorld, blockPos, blocksDown);
				if(height > highestBlock) highestBlock = height;

				if(callCounter != 0 && callCounter % maxRPT == 0)
				{
					Future[] lastDataArray = futureBlockData[x > 0 ? x-1 : 0][z > 0 ? z-1 : 0];
					waitForFuture(lastDataArray[lastDataArray.length-1]);
				}

				callCounter++;
			}
		}
		Future[] lastDataArray = futureBlockData[ceilBPP - 1][ceilBPP - 1];
		Future lastData = lastDataArray[lastDataArray.length - 1];

		waitForFuture(lastData);

		IBlockData[][][] blockData = new IBlockData[ceilBPP][ceilBPP][highestBlock];
		for(int x = 0; x < ceilBPP; x++)
		{
			for(int z = 0; z < ceilBPP; z++)
			{
				Future<IBlockData>[] futureDataArray = (Future<IBlockData>[])futureBlockData[x][z];
				try {
					for(int i = 0; i < futureDataArray.length; i++)
					{
						blockData[x][z][i] = futureDataArray[i].get();
					}
				} catch (InterruptedException | ExecutionException | CancellationException ignored) { //Ignore because we already warn user about interrupting

				}
			}
		}
		return blockData;
	}

	@SuppressWarnings("unchecked")
	private Integer[][] getSyncBlockHeights(org.bukkit.World world, BlockPosition firstBlock)
	{
		int size = (int)Math.ceil((128 + renderData.getCeilBPP()) * renderData.getBlocksPerPixel());

		if(size > heightsSizeLimit) size = heightsSizeLimit;
		Integer[][] heights = new Integer[size][size];
		Object[][] heightFutures = new Object[size][size];
		int callCounter = 0;
		for(int x = 0; x < size; x++)
		{
			for(int z = 0; z < size; z++)
			{
				if(callCounter != 0 && callCounter % maxRPT == 0)
				{
					Future<Integer> lastHeight = (Future<Integer>)heightFutures[x != 0 ? x - 1 : 0][z != 0 ? z-1 : 0]; //Wait for previous height
					waitForFuture(lastHeight);
				}
				int blockX = firstBlock.getX() + x;
				int blockZ = firstBlock.getZ() + z;
				try {
					heightFutures[x][z] = scheduler.callSyncMethod(mapsPlugin, () -> world.getHighestBlockYAt(blockX, blockZ));
				}
				catch(IllegalPluginAccessException ignored) //Ignore because we already warn user about interrupting
				{}

				callCounter++;
			}
		}

		Future<Integer> lastHeight = (Future<Integer>)heightFutures[size - 1][size - 1];
		waitForFuture(lastHeight);
		for(int x = 0; x < size; x++)
		{
			for (int z = 0; z < size; z++)
			{
				try {
					heights[x][z] = ((Future<Integer>)heightFutures[x][z]).get();
				} catch (InterruptedException | ExecutionException | CancellationException e) {
					e.printStackTrace();
				}
			}
		}
		return heights;
	}

	@SuppressWarnings("unchecked")
	private IBlockData[] getSyncBlocksDown(World nmsWorld, BlockPosition bPosition, int blocksDown)
	{
		Object[] futureBlockData = new Object[blocksDown];
		for(int i = 0; i < blocksDown; i++)
		{
			final BlockPosition blockPosition = bPosition = bPosition.a(0, -1, 0);
			try {
				futureBlockData[i] = scheduler.callSyncMethod(mapsPlugin, () -> nmsWorld.getType(blockPosition));
			}
			catch(IllegalPluginAccessException ignored) //Ignore because we already warn user about interrupting
			{}

		}

		Future<IBlockData> lastData = (Future<IBlockData>)futureBlockData[blocksDown-1];
		while(!lastData.isDone())
		{
			try {
				Thread.sleep(40);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		IBlockData[] blockData = new IBlockData[blocksDown];
		for(int i = 0; i < blocksDown; i++)
		{
			try {
				Future<IBlockData> data = (Future<IBlockData>)futureBlockData[i];
				blockData[i] = data.get();
			} catch (InterruptedException | ExecutionException | CancellationException ignored) { //Ignore because we already warn user about interrupting

			}
		}
		return blockData;
	}

	@SuppressWarnings("unchecked")
	private Future<IBlockData>[] getFutureBlocksDown(World nmsWorld, BlockPosition bPosition, int blocksDown)
	{
		Future[] futureBlockData = new Future[blocksDown];
		for(int i = 0; i < blocksDown; i++)
		{
			final BlockPosition blockPosition = bPosition = bPosition.a(0, -1, 0);
			try {
				futureBlockData[i] = scheduler.callSyncMethod(mapsPlugin, () -> nmsWorld.getType(blockPosition));
			}
			catch(IllegalPluginAccessException ignored) //Ignore because we already warn user about interrupting
			{}
		}
		return futureBlockData;
	}

	class BlockDataArea {
		int sizeX, sizeZ;
		int posX, posZ;
		IBlockData[][][] blockData;

		BlockDataArea(int sizeX, int sizeZ, int firstPosX, int firstPosZ)
		{
			this.sizeX = sizeX;
			this.sizeZ = sizeZ;
			this.posX = firstPosX;
			this.posZ = firstPosZ;
			blockData = new IBlockData[sizeX][sizeZ][];
		}

		void setBlockData(IBlockData[][][] blockData) {
			this.blockData = blockData;
		}

		int getFirstPosX() {
			return posX;
		}

		int getFirstPosZ() {
			return posZ;
		}

		IBlockData[] getBlockData(int blockX, int blockZ)
		{
			if(sizeX > blockX && sizeZ > blockZ)
			{
				return blockData[blockX][blockZ];
			}

			return null;
		}
	}
}
