import Utils.StringParsing;
import Utils.Triple;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

//TODO: List in config
//TODO: Better handling of player join/leave during gamemodes
//TODO: Global /spawn command - Only useable when no gamemodes are active

//TODO: Normal - Better tiebreaker!
//TODO: Normal - Change to timer instead of x kills?

//TODO: Parkour - Stats: time/deaths
//TODO: Parkour - Make sure effects apply on edge of blocks
//TODO: Parkour - Make sure no placement of Snowblocks!

//TODO: Spleef - Snowball damage?
//TODO: Spleef - Scoreboard

//TODO: Other gamemodes BR/3 lives - last man standing

class Globals{
    enum Gamemode{
        NONE,
        SNOWBALLFIGHT,
        SPLEEF,
        TWD
    }

    public static Gamemode Ongoing = Gamemode.NONE;
    public static String sbfCommandName = "sbf";
    public static String twdCommandName = "twd";
    public static String parkourCommandName = "parkour";
}

public class FlanPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getLogger().info("FlanPlugin enabled!");

        getConfig().options().copyDefaults();
        saveDefaultConfig();

        //SnowballFight
        FlanPluginConfig.setup();
        SnowballFightCommandExecutor sbfCmd = new SnowballFightCommandExecutor();
        getCommand(Globals.sbfCommandName).setExecutor(sbfCmd);
        NormalSnowballFight nsf = new NormalSnowballFight();
        Spleef spleef = new Spleef();
        sbfCmd.registerCommand("normal", nsf);
        sbfCmd.registerCommand("spleef", spleef);
        sbfCmd.registerCommand("reload", new ReloadCommand());

        //Parkour
        ParkourCommandExecutor parkourCmd = new ParkourCommandExecutor();
        getCommand(Globals.parkourCommandName).setExecutor(parkourCmd);
        parkourCmd.registerCommand("rules", new ParkourRulesCommand());
        parkourCmd.registerCommand("respawn", new ParkourRespawnCommand());
        parkourCmd.registerCommand("restart", new ParkourRestartCommand());

        //The Walking Dtaalog
        TheWalkingDatalogCommandExecutor twdCmd = new TheWalkingDatalogCommandExecutor();
        getCommand(Globals.twdCommandName).setExecutor(twdCmd);
        TheWalkingDatalog twd = new TheWalkingDatalog();
        twdCmd.registerCommand("normal", twd);
//        TheWalkingDatalog.FlanEntityType.registerEntities();

        //General
        getCommand("spawn").setExecutor(new SpawnCommand());

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(nsf, this);
        pm.registerEvents(spleef, this);
        pm.registerEvents(parkourCmd, this);
        pm.registerEvents(twd, this);

        FlanPluginConfig.get().options().copyDefaults(true);
        FlanPluginConfig.save();
    }

    @Override
    public void onDisable() {
        getLogger().info("FlanPlugin disabled!");
    }
}

class SpawnCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(sender instanceof Player p) {
            if (Globals.Ongoing != Globals.Gamemode.NONE){
                p.sendMessage(ChatColor.RED + "Can not teleport to spawn during gamemode!");
                return false;
            }

            Triple<Integer, Integer, Integer> global_spawn = StringParsing.getCoordsFromConfigLocation(FlanPluginConfig.get().getString("global_spawn"));
            p.teleport(new Location(p.getWorld(), global_spawn.first, global_spawn.second, global_spawn.third));
            p.getInventory().clear();
        }
        return true;
    }
}

abstract class SubCommand{
    abstract void onCommand(Player player, Command cmd, String[] args);
}

