package me.fluglow;

import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.bukkit.selections.Polygonal2DSelection;
import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapArea implements ConfigurationSerializable {

	private Polygonal2DSelection selection;
	private String name;
	private int id; //Used to save config. Must be unique.
	private final MapAreaSettings settings;


	MapArea(Polygonal2DSelection selection, MapAreaSettings settings, String name, int id)
	{
		this.selection = selection;
		this.settings = settings;
		this.name = name;
		this.id = id;

	}

	Polygonal2DSelection getSelection() {
		return selection;
	}

	MapAreaSettings getSettings() {
		return settings;
	}

	@Override
	public Map<String, Object> serialize()
	{
		LinkedHashMap<String, Object> serialized = new LinkedHashMap<>(); //Linked so we can manage the configuration file value order

		String world = "";

		serialized.put("id", id);
		serialized.put("name", name);

		if(selection.getWorld() != null)
		{
			world = selection.getWorld().getName();
		}

		serialized.put("world", world);

		List<Vector> points = new ArrayList<>();

		for(BlockVector2D vector : selection.getNativePoints())
		{
			Vector asVector = new Vector(vector.getX(), 0, vector.getZ());
			points.add(asVector);
		}

		serialized.put("points", points);


		return serialized;
	}


	static MapArea deserialize(Map<String, Object> arg, int id, MapAreaSettings settings) //Deserialize from config
	{
		String areaName = (String) arg.get("name"); //Area name

		@SuppressWarnings("unchecked")

		String worldName = (String) arg.get("world"); //Area world
		World world = Bukkit.getWorld(worldName);
		if(world == null)
		{
			Bukkit.getLogger().warning("[AreaMaps] Could not load world with world name " + worldName + " for area " + areaName + "! Was the world deleted?");
			Bukkit.getLogger().warning("[AreaMaps] If you will never use this map again, please remove it from your configuration.");
			return null;
		}

		@SuppressWarnings("unchecked")
		List<Vector> savedPoints = (ArrayList<Vector>) arg.get("points"); //The area's boundaries
		if(savedPoints == null)
		{
			Bukkit.getLogger().warning("[AreaMaps] Could not load points for " + areaName + "! Please check the config for any errors.");
			return null;
		}

		return new MapArea(MapArea.pointsToSelection(savedPoints, world), settings, areaName, id);
	}

	static Polygonal2DSelection pointsToSelection(List<Vector> points, World world) //Turns a list of points to a selection
	{
		List<BlockVector2D> blockVectors = new ArrayList<>();
		for(Vector vector : points)
		{
			BlockVector2D blockVector = new BlockVector2D(vector.getX(), vector.getZ()); //Turn the vector to a BlockVector2D that the selection takes
			blockVectors.add(blockVector); //Add to this layer's points
		}
		return new Polygonal2DSelection(world, blockVectors, 0, world.getMaxHeight()); //Create a selection from the points
	}

	public void showBorderToPlayer(Player player)
	{
		List<BlockVector2D> vectors = selection.getNativePoints();
		World world = player.getWorld();

		int particleCount = 15;
		int yDown = 0;

		for(int i = 0; i < vectors.size(); i++) //Loop through vectors
		{
			Vector point1 = new Vector(vectors.get(i).getX(), 0, vectors.get(i).getZ());
			Vector point2;
			if(i+1 != vectors.size())
			{
				point2 = new Vector(vectors.get(i+1).getX(), 0, vectors.get(i+1).getZ());
			}
			else
			{
				point2 = new Vector(vectors.get(0).getX(), 0, vectors.get(0).getZ());
			}
			double distance = point1.distance(point2);
			Vector direction = point2.subtract(point1).normalize();
			for(int i2 = 0; i2 < distance; i2++)
			{
				Vector dir = direction.clone().multiply(i2);
				Location location = dir.add(point1).toLocation(world);
				location.setY(player.getLocation().getY() - yDown);
				location.add(location.getX() < 0 ? 0.5 : -0.5, 0.0, location.getZ() < 0 ? -0.5 : 0.5); //Get block center instead of block corner
				player.spawnParticle(Particle.SMOKE_LARGE, location, particleCount, 0, 0, 0, 0);
			}
		}
	}

	public void teleportTo(Player player)
	{
		player.getLocation().getYaw();
		World world = selection.getWorld();
		if(world == null)
		{
			return;
		}
		Vector[] corners = new Vector[] {
				selection.getMinimumPoint().toVector(), selection.getMaximumPoint().toVector()
		};
		Vector midPoint = corners[1].add(corners[0]).divide(new Vector(2, 0, 2));
		Location tpLoc = midPoint.toLocation(world);
		tpLoc.setYaw(player.getLocation().getYaw());
		tpLoc.setPitch(player.getLocation().getPitch());
		tpLoc.add(tpLoc.getX() < 0 ? 0.5 : -0.5, 0.0, tpLoc.getZ() < 0 ? -0.5 : 0.5); //Get block center instead of block corner
		tpLoc.setY(world.getHighestBlockYAt(tpLoc));
		player.teleport(tpLoc);
		player.sendMessage(ChatColor.GREEN + "Teleported to " + name + "! (" + tpLoc.toVector().toString() + ")");
	}

	public String getName() {
		return name;
	}

	public int getId() {
		return id;
	}
}
