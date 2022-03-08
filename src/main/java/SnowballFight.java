import Utils.StringParsing;
import Utils.Triple;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.http.WebSocket;
import java.util.*;

//I hate Java.
public class SnowballFight extends JavaPlugin implements Listener {
    String sbfCommandName = "sbf";

    @Override
    public void onEnable() {
        getLogger().info("SnowballFight plugin enabled!");

        getConfig().options().copyDefaults();
        saveDefaultConfig();
        SnowballConfig.setup();

        SnowballFightCommand cmd = new SnowballFightCommand(sbfCommandName);
        getCommand(sbfCommandName).setExecutor(cmd);
        NormalSnowballFight nsf = new NormalSnowballFight();
        cmd.registerCommand("normal", nsf);
        cmd.registerCommand("reload", new ReloadCommand());

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(nsf, this);

        SnowballConfig.get().addDefault("global_spawn", "(0;0;120)");
        SnowballConfig.get().options().copyDefaults(true);
        SnowballConfig.save();
    }

    @Override
    public void onDisable() {
        getLogger().info("SnowballFight plugin disabled!");
    }

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Snowball){
            e.setDamage(SnowballConfig.get().getDouble("snowball_damage"));
            e.getEntity().getWorld().playEffect(e.getEntity().getLocation(), Effect.STEP_SOUND, 80, 1);
        }
    }
}


class SnowballConfig{
    private static File file;
    private static FileConfiguration customFile;

    public static void setup(){
        file = new File(Bukkit.getServer().getPluginManager().getPlugin("FlanSnowballFight").getDataFolder(),"snowballfight_config.yml");

        if(!file.exists()){
            try{
                file.createNewFile();
            }catch(IOException e){
                //Sucks
            }
        }
        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get(){
        return customFile;
    }

    public static void save(){
        try{
            customFile.save(file);
        }catch(IOException e){
            System.out.println("Could not save file");
        }
    }

    public static void reload(){
        customFile = YamlConfiguration.loadConfiguration(file);
    }
}

class ReloadCommand extends SubCommand {
    @Override
    void onCommand(Player player, Command cmd, String[] args) {
        SnowballConfig.reload();
        player.sendMessage(ChatColor.GREEN + "FlanSnowballFight plugin reloaded!");
    }
}

class SnowballFightCommand implements CommandExecutor {
    String cmdName;
    private Map<String, SubCommand> cmds = new HashMap<>(); //Maps name of gamemode to gamemode

    public SnowballFightCommand(String sbfCommandName) {
        cmdName = sbfCommandName;
    }

    public void registerCommand(String cmd, SubCommand subCommand){
        cmds.put(cmd, subCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(sender instanceof Player p && p.isOp()){
            if(args.length == 0){
                p.sendMessage(ChatColor.RED + "You need to enter some arguments.");
                p.sendMessage(ChatColor.YELLOW + String.format("To see all commands: /%s help", cmdName));
                return false;
            }

            if(args[0].toLowerCase().equals("help") || args[0].toLowerCase().equals("h")){
                p.sendMessage(ChatColor.RED + "Sucks, I was too lazy to implement help command");
                return true;
            }

            if(!cmds.containsKey(args[0].toLowerCase())){
                p.sendMessage(ChatColor.RED + "This subcommand does not exist!");
                p.sendMessage(ChatColor.YELLOW + String.format("Existing subcommands are %s", cmds.keySet().toString()));
                return false;
            }

            cmds.get(args[0]).onCommand(p, command, Arrays.copyOfRange(args, 1, args.length));
        }

        return true;
    }
}

class NormalSnowballFight extends SubCommand implements Listener{
    private final String config_prefix = "normal.";
    public boolean isOngoing = false;
    public ArrayList<PlayerStats> playersStats = new ArrayList<>();
    public World world;
    TimerTask task;

    @Override
    void onCommand(Player player, Command cmd, String[] args) {
        if(args.length == 0){
            player.sendMessage(ChatColor.RED + "Wrong use - Use better");
            return;
        }

        if(Objects.equals(args[0], "spawns")) {
            ArrayList<String> spawns = StringParsing.configStringToList(SnowballConfig.get().getString(config_prefix + "spawns"));
            player.sendMessage(spawns.toString());

            return;
        }

        if(Objects.equals(args[0], "add_spawn")){
            Location loc = player.getLocation();
            ArrayList<String> spawns = StringParsing.configStringToList(SnowballConfig.get().getString(config_prefix + "spawns"));

            String newLoc = String.format("(%s;%s;%s)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            spawns.add(newLoc);
            SnowballConfig.get().set(config_prefix + "spawns", StringParsing.listToConfigString(spawns));
            SnowballConfig.get().options().copyDefaults(true);
            SnowballConfig.save();
            player.sendMessage("Added new location: " + newLoc);
            return;
        }

        if(Objects.equals(args[0], "start")) {
            if(isOngoing){
                player.sendMessage(ChatColor.RED + "SnowballFight is already in progress!");
                return;
            }

            playersStats = new ArrayList<>();
            world = player.getWorld();

            Random rnd = new Random();
            ArrayList<String> spawns = StringParsing.configStringToList(SnowballConfig.get().getString(config_prefix + "spawns"));
            ArrayList<Triple<Integer, Integer, Integer>> spawnLocs = new ArrayList<>();
            for (String s : spawns){
                spawnLocs.add(StringParsing.getCoordsFromConfigLocation(s));
            }
            int locs = spawnLocs.size();


            for (Player p : Bukkit.getOnlinePlayers()){
                playersStats.add(new PlayerStats(p));
                int num = rnd.nextInt(locs);
                Triple<Integer, Integer, Integer> tpLoc = spawnLocs.get(num);
                p.teleport(new Location(player.getWorld(), tpLoc.first, tpLoc.second, tpLoc.third));
                p.setHealth(20);
                p.setSaturation(10);
                p.getInventory().clear();
                p.getInventory().addItem(new ItemStack(Material.SNOWBALL, 1000));
                p.sendMessage(ChatColor.YELLOW + "Snowball fight has started!");
            }

            SnowballConfig.get().set("snowball_damage", 5); //TODO: HERE
            isOngoing = true;
            task = new TimerTask() {
                @Override
                public void run() {
                    System.out.println("SnowballFight performed on: " + new Date() + "n" +
                            "Thread's name: " + Thread.currentThread().getName());
                    stopFight();
                }
            };

            Timer timer = new Timer();

            int matchLength = 10; //Seconds

            if (args.length > 1){
                try{
                    matchLength = Integer.parseInt(args[1]);
                }catch(NumberFormatException e){

                }
            }
            timer.schedule(task, matchLength * 1000);
        }

        if(Objects.equals(args[0], "stop")){
            if(task != null) task.cancel();
            stopFight();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        Player killed = e.getEntity();
        Player killer = killed.getKiller();
        if(killer == null) return; //TODO: Add death

        if(killer == killed) return;

        for (PlayerStats ps : playersStats){
            if(ps.player.getDisplayName().equals(killed.getDisplayName())){
                ps.deaths++;
            }

            if(ps.player.getDisplayName().equals(killer.getDisplayName())){
                ps.kills++;
            }
        }
    }

    public void stopFight(){
        if(!isOngoing) return;
        //TODO: Spawn points!
        //TODO: Leaderboards!
        //TODO: Better tiebreaker!
        //TODO: Give snowballs on throw!
        //TODO: Block protection
        //TODO: TimerTask is broken - Replace with x points to win?
        Collections.sort(playersStats, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats o1, PlayerStats o2) {
                if(o1.kills == o2.kills) return 0;
                return o1.kills < o2.kills ? 1 : -1;
            }
        });

        if(playersStats.size() >= 3){
            PlayerStats first = playersStats.get(0);
            PlayerStats second = playersStats.get(1);
            PlayerStats third = playersStats.get(2);

            for (PlayerStats ps : playersStats){
                ps.player.sendMessage(ChatColor.YELLOW + "Time's up!");
                ps.player.sendMessage(ChatColor.YELLOW + String.format("1st place (%d kills): %s ", first.kills, first.player.getDisplayName()));
                ps.player.sendMessage(ChatColor.YELLOW + String.format("2nd place (%d kills): %s ", second.kills, second.player.getDisplayName()));
                ps.player.sendMessage(ChatColor.YELLOW + String.format("3rd place (%d kills): %s ", third.kills, third.player.getDisplayName()));
            }
        }

        String global_spawn = SnowballConfig.get().getString("global_spawn");
        Triple<Integer, Integer, Integer> t = StringParsing.getCoordsFromConfigLocation(global_spawn);
        for(PlayerStats ps : playersStats){
            ps.player.teleport(new Location(world, t.first, t.second, t.third));
        }

        isOngoing = false;
    }

}

class PlayerStats{
    public Player player;
    public double damage_done = 0;
    public double damage_received = 0;
    public int kills = 0;
    public int deaths = 0;

    public PlayerStats(Player p){
        player = p;
    }
}

abstract class SubCommand{
    abstract void onCommand(Player player, Command cmd, String[] args);
}