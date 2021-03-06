package com.wimbli.WorldBorder;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;


public class Config
{
	// private stuff used within this class
	private static WorldBorder plugin;
	private static FileConfiguration cfg = null;
	private static PermissionHandler Permissions = null;
	private static final Logger mcLog = Logger.getLogger("Minecraft");
	public static DecimalFormat coord = new DecimalFormat("0.0");
	private static int borderTask = -1;
	public static WorldFillTask fillTask = null;
	public static WorldTrimTask trimTask = null;
	public static Set<String> movedPlayers = Collections.synchronizedSet(new HashSet<String>());
	private static Runtime rt = Runtime.getRuntime();
	
	// actual configuration values which can be changed
	private static boolean shapeRound = true;
	private static Map<String, BorderData> borders = Collections.synchronizedMap(new LinkedHashMap<String, BorderData>());
	private static String message;
	private static boolean DEBUG = false;
	private static double knockBack = 3.0;
	private static int timerTicks = 4;
	private static boolean whooshEffect = false;

	// for monitoring plugin efficiency
//	public static long timeUsed = 0;

	public static long Now()
	{
		return System.currentTimeMillis();
	}

	public static void setBorder(String world, BorderData border)
	{
		borders.put(world, border);
		Log("Border set. " + BorderDescription(world));
		save(true);
	}
	public static void setBorder(String world, int radius, double x, double z, Boolean shapeRound)
	{
		setBorder(world, new BorderData(x, z, radius, shapeRound));
	}
	public static void setBorder(String world, int radius, double x, double z)
	{
		BorderData old = Border(world);
		Boolean oldShape = (old == null) ? null : old.getShape();
		setBorder(world, new BorderData(x, z, radius, oldShape));
	}

	public static void removeBorder(String world)
	{
		borders.remove(world);
		Log("Removed border for world \"" + world + "\".");
		save(true);
	}

	public static void removeAllBorders()
	{
		borders.clear();
		Log("Removed all borders for all worlds.");
		save(true);
	}

	public static String BorderDescription(String world)
	{
		BorderData border = borders.get(world);
		if (border == null)
			return "No border was found for the world \"" + world + "\".";
		else
			return "World \"" + world + "\" has border " + border.toString();
	}

	public static Set<String> BorderDescriptions()
	{
		Set<String> output = new HashSet<String>();

		Iterator world = borders.keySet().iterator();
		while(world.hasNext())
		{
			output.add( BorderDescription((String)world.next()) );
		}

		return output;
	}

	public static BorderData Border(String world)
	{
		return borders.get(world);
	}

	public static void setMessage(String msg)
	{
		message = msg;
		Log("Border message is now set to: " + msg);
		save(true);
	}

	public static String Message()
	{
		return message;
	}

	public static void setShape(boolean round)
	{
		shapeRound = round;
		Log("Set default border shape to " + (round ? "round" : "square") + ".");
		save(true);
	}

	public static boolean ShapeRound()
	{
		return shapeRound;
	}

	public static void setDebug(boolean debugMode)
	{
		DEBUG = debugMode;
		Log("Debug mode " + (DEBUG ? "enabled" : "disabled") + ".");
		save(true);
	}

	public static boolean Debug()
	{
		return DEBUG;
	}

	public static void setWhooshEffect(boolean enable)
	{
		whooshEffect = enable;
		Log("\"Whoosh\" knockback effect " + (whooshEffect ? "enabled" : "disabled") + ".");
		save(true);
	}

	public static boolean whooshEffect()
	{
		return whooshEffect;
	}

	public static void setKnockBack(double numBlocks)
	{
		knockBack = numBlocks;
		Log("Knockback set to " + knockBack + " blocks inside the border.");
		save(true);
	}

	public static double KnockBack()
	{
		return knockBack;
	}

	public static void setTimerTicks(int ticks)
	{
		timerTicks = ticks;
		Log("Timer delay set to " + timerTicks + " tick(s). That is roughly " + (timerTicks * 50) + "ms / " + (((double)timerTicks * 50.0) / 1000.0) + " seconds.");
		StartBorderTimer();
		save(true);
	}

	public static int TimerTicks()
	{
		return timerTicks;
	}


	public static void StartBorderTimer()
	{
		StopBorderTimer();

		borderTask = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new BorderCheckTask(plugin.getServer()), timerTicks, timerTicks);

		if (borderTask == -1)
			LogWarn("Failed to start timed border-checking task! This will prevent the plugin from working. Try restarting Bukkit.");

		LogConfig("Border-checking timed task started.");
	}

	public static void StopBorderTimer()
	{
		if (borderTask == -1) return;

		plugin.getServer().getScheduler().cancelTask(borderTask);
		borderTask = -1;
		LogConfig("Border-checking timed task stopped.");
	}


	public static void StopFillTask()
	{
		if (fillTask != null && fillTask.valid())
			fillTask.cancel();
	}

	public static void StoreFillTask()
	{
		save(false, true);
	}
	public static void UnStoreFillTask()
	{
		save(false);
	}

	public static void RestoreFillTask(String world, int fillDistance, int chunksPerRun, int tickFrequency, int x, int z, int length, int total)
	{
		fillTask = new WorldFillTask(plugin.getServer(), null, world, fillDistance, chunksPerRun, tickFrequency);
		if (fillTask.valid())
		{
			fillTask.continueProgress(x, z, length, total);
			int task = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, fillTask, 20, tickFrequency);
			fillTask.setTaskID(task);
		}
	}


	public static void StopTrimTask()
	{
		if (trimTask != null && trimTask.valid())
			trimTask.cancel();
	}


	public static int AvailableMemory()
	{
		return (int)((rt.maxMemory() - rt.totalMemory() + rt.freeMemory()) / 1048576);  // 1024*1024 = 1048576 (bytes in 1 MB)
	}


	public static void loadPermissions(WorldBorder plugin)
	{
		if (Permissions != null || plugin == null)
			return;

		// Check for Permissions plugin
		Plugin test = plugin.getServer().getPluginManager().getPlugin("Permissions");

		if (test != null && test.getClass().getName().equals("com.nijikokun.bukkit.Permissions.Permissions"))
		{
			Permissions = ((Permissions)test).getHandler();
			LogConfig("Will use plugin for permissions: "+((Permissions)test).getDescription().getFullName());
		} else {
			LogConfig("Permissions plugin not found. Defaulting to Bukkit's built-in SuperPerms system.");
		}
	}

	public static boolean HasPermission(Player player, String request)
	{
		if (player == null)				// console, always permitted
			return true;
		else if (player.isOp())			// Op, always permitted
			return true;

		if (Permissions != null)	// Permissions plugin available
		{
			if (Permissions.permission(player, "worldborder." + request))
				return true;
		}
		if (player.hasPermission("worldborder." + request))	// built-in Bukkit superperms
			return true;

		player.sendMessage("You do not have sufficient permissions.");
		return false;
	}


	private static final String logName = "WorldBorder";
	public static void Log(Level lvl, String text)
	{
		mcLog.log(lvl, String.format("[%s] %s", logName, text));
	}
	public static void Log(String text)
	{
		Log(Level.INFO, text);
	}
	public static void LogWarn(String text)
	{
		Log(Level.WARNING, text);
	}
	public static void LogConfig(String text)
	{
		Log(Level.INFO, "[CONFIG] " + text);
	}


	private static final int currentCfgVersion = 4;

	public static void load(WorldBorder master, boolean logIt)
	{	// load config from file
		plugin = master;

		// do this first, in case config.yml has any old "�" compatibility characters in it which cause errors in Bukkit's new configuration handler
		compat_load();

		plugin.reloadConfig();
		cfg = plugin.getConfig();
//		cfg.options().pathSeparator('>');  // should work to get rid of hackish "world names with periods in them" workaround
		// ^ sadly, while Bukkit saves the data fine with periods intact using this, it does not load it correctly (bugged, still treats periods as separators in addition to chosen separator)

		int cfgVersion = cfg.getInt("cfg-version", currentCfgVersion);

		message = cfg.getString("message");
		shapeRound = cfg.getBoolean("round-border", true);
		DEBUG = cfg.getBoolean("debug-mode", false);
		whooshEffect = cfg.getBoolean("whoosh-effect", false);
		knockBack = cfg.getDouble("knock-back-dist", 3.0);
		timerTicks = cfg.getInt("timer-delay-ticks", 5);
		LogConfig("Using " + (shapeRound ? "round" : "square") + " border, knockback of " + knockBack + " blocks, and timer delay of " + timerTicks + ".");

		StartBorderTimer();

		borders.clear();

		if (message == null || message.isEmpty())
		{	// store defaults
			LogConfig("Configuration not present, creating new file.");
			message = "You have reached the edge of this world.";
			save(false);
			return;
		}

		ConfigurationSection worlds = cfg.getConfigurationSection("worlds");
		if (worlds != null)
		{
			Set<String> worldNames = worlds.getKeys(false);

			for(String worldName : worldNames)
			{
				ConfigurationSection bord = worlds.getConfigurationSection(worldName);

				// we're swapping "<" to "." at load since periods denote configuration nodes without a working way to change that, so world names with periods wreak havoc and are thus modified for storage
				if (cfgVersion > 3)
					worldName = worldName.replace("<", ".");

				Boolean overrideShape = (Boolean) bord.get("shape-round");
				BorderData border = new BorderData(bord.getDouble("x", 0), bord.getDouble("z", 0), bord.getInt("radius", 0), overrideShape);
				borders.put(worldName, border);
				LogConfig(BorderDescription(worldName));
			}
		}

		// if we have an unfinished fill task stored from a previous run, load it up
		ConfigurationSection storedFillTask = cfg.getConfigurationSection("fillTask");
		if (storedFillTask != null)
		{
			String worldName = storedFillTask.getString("world");
			int fillDistance = storedFillTask.getInt("fillDistance", 176);
			int chunksPerRun = storedFillTask.getInt("chunksPerRun", 5);
			int tickFrequency = storedFillTask.getInt("tickFrequency", 20);
			int fillX = storedFillTask.getInt("x", 0);
			int fillZ = storedFillTask.getInt("z", 0);
			int fillLength = storedFillTask.getInt("length", 0);
			int fillTotal = storedFillTask.getInt("total", 0);
			RestoreFillTask(worldName, fillDistance, chunksPerRun, tickFrequency, fillX, fillZ, fillLength, fillTotal);
			save(false);
		}

		if (logIt)
			LogConfig("Configuration loaded.");

		if (cfgVersion < currentCfgVersion)
			save(false);
	}

	public static void save(boolean logIt)
	{
		save(logIt, false);
	}
	public static void save(boolean logIt, boolean storeFillTask)
	{	// save config to file
		if (cfg == null) return;

		cfg.set("cfg-version", currentCfgVersion);
		cfg.set("message", message);
		cfg.set("round-border", shapeRound);
		cfg.set("debug-mode", DEBUG);
		cfg.set("whoosh-effect", whooshEffect);
		cfg.set("knock-back-dist", knockBack);
		cfg.set("timer-delay-ticks", timerTicks);

		cfg.set("worlds", null);
		Iterator world = borders.entrySet().iterator();
		while(world.hasNext())
		{
			Entry wdata = (Entry)world.next();
			String name = ((String)wdata.getKey()).replace(".", "<");
			BorderData bord = (BorderData)wdata.getValue();

			cfg.set("worlds." + name + ".x", bord.getX());
			cfg.set("worlds." + name + ".z", bord.getZ());
			cfg.set("worlds." + name + ".radius", bord.getRadius());
/*			cfg.set("worlds>" + name + ">x", bord.getX());
			cfg.set("worlds>" + name + ">z", bord.getZ());
			cfg.set("worlds>" + name + ">radius", bord.getRadius());
 */

			if (bord.getShape() != null)
				cfg.set("worlds." + name + ".shape-round", bord.getShape());
		}

		if (storeFillTask && fillTask != null && fillTask.valid())
		{
			cfg.set("fillTask.world", fillTask.refWorld());
			cfg.set("fillTask.fillDistance", fillTask.refFillDistance());
			cfg.set("fillTask.chunksPerRun", fillTask.refChunksPerRun());
			cfg.set("fillTask.tickFrequency", fillTask.refTickFrequency());
			cfg.set("fillTask.x", fillTask.refX());
			cfg.set("fillTask.z", fillTask.refZ());
			cfg.set("fillTask.length", fillTask.refLength());
			cfg.set("fillTask.total", fillTask.refTotal());
		}
		else
			cfg.set("fillTask", null);

		plugin.saveConfig();

		if (logIt)
			LogConfig("Configuration saved.");
	}

	// needed in case config.yml has any old "�" compatibility characters in it, since they cause parse errors in Bukkit's new configuration handler
	public static void compat_load()
	{ // load config from file
		Configuration old_cfg = plugin.getConfiguration();

		int cfgVersion = old_cfg.getInt("cfg-version", currentCfgVersion);

		// if the config version is newer than this, it's safe to load through the normal handler
		if (cfgVersion > 3)
			return;

		LogConfig("Upgrading older version config file.");

		message = old_cfg.getString("message");
		shapeRound = old_cfg.getBoolean("round-border", true);
		DEBUG = old_cfg.getBoolean("debug-mode", false);
		whooshEffect = old_cfg.getBoolean("whoosh-effect", false);
		knockBack = old_cfg.getDouble("knock-back-dist", 3.0);
		timerTicks = old_cfg.getInt("timer-delay-ticks", 5);

		borders.clear();

		Map<String, ConfigurationNode> worlds = old_cfg.getNodes("worlds");
		if (worlds != null)
		{
			Iterator world = worlds.entrySet().iterator();
			while(world.hasNext())
			{
				Entry wdata = (Entry)world.next();

				String name = null;
				// we're swapping "�" (from extended ASCII set) and "." back and forth at save and load since periods denote configuration nodes, and world names with periods otherwise wreak havoc
				if (cfgVersion > 1)
					name = ((String)wdata.getKey()).replace("�", ".");
				else // old v1 format, periods encoded as slashes, which had problems
					name = ((String)wdata.getKey()).replace("/", ".");

				ConfigurationNode bord = (ConfigurationNode)wdata.getValue();
				Boolean overrideShape = (Boolean) bord.getProperty("shape-round");
				BorderData border = new BorderData(bord.getDouble("x", 0), bord.getDouble("z", 0), bord.getInt("radius", 0), overrideShape);
				borders.put(name, border);
			}
		}

		// if we have an unfinished fill task stored from a previous run, load it up
		ConfigurationNode storedFillTask = old_cfg.getNode("fillTask");
		if (storedFillTask != null)
		{
			String worldName = storedFillTask.getString("world");
			int fillDistance = storedFillTask.getInt("fillDistance", 176);
			int chunksPerRun = storedFillTask.getInt("chunksPerRun", 5);
			int tickFrequency = storedFillTask.getInt("tickFrequency", 20);
			int fillX = storedFillTask.getInt("x", 0);
			int fillZ = storedFillTask.getInt("z", 0);
			int fillLength = storedFillTask.getInt("length", 0);
			int fillTotal = storedFillTask.getInt("total", 0);
			RestoreFillTask(worldName, fillDistance, chunksPerRun, tickFrequency, fillX, fillZ, fillLength, fillTotal);
		}

		old_cfg.removeProperty("worlds");
		old_cfg.save();
		cfg = plugin.getConfig();
		save(false, false);
	}
}
