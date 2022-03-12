import Utils.StringParsing;
import Utils.Triple;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

//TODO: Better tiebreaker!
//TODO: Change to timer instead of x kills?
//TODO: Snowblocks - Make sure not possible in parkour!
//TODO: List in config
//TODO: Other gamemodes Hexagon/tntrun/spleef
//TODO: Add rules to gamemodes/parkour
//TODO: Parkour - speedrun/cps/win/reward
//TODO: Better handling of player join/leave during gamemodes

class Globals{
    enum Gamemode{
        NONE,
        NORMAL
    }

    public static Gamemode Ongoing = Gamemode.NONE;
    public static String sbfCommandName = "sbf";
    public static String parkourCommandName = "parkour";
    public static ArrayList<Triple<Integer, Integer, Integer>> parkour_checkpoints = new ArrayList<>();
}

public class SnowballFight extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getLogger().info("SnowballFight plugin enabled!");

        getConfig().options().copyDefaults();
        saveDefaultConfig();
        SnowballConfig.setup();

        SnowballFightCommand cmd = new SnowballFightCommand();
        getCommand(Globals.sbfCommandName).setExecutor(cmd);
        NormalSnowballFight nsf = new NormalSnowballFight();
        cmd.registerCommand("normal", nsf);
        cmd.registerCommand("reload", new ReloadCommand());

        ParkourCommand parkourCmd = new ParkourCommand();
        getCommand(Globals.parkourCommandName).setExecutor(parkourCmd);
        parkourCmd.registerCommand("rules", new ParkourRulesCommand());
        parkourCmd.registerCommand("respawn", new ParkourRespawnCommand());
        parkourCmd.registerCommand("restart", new ParkourRestartCommand());

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(nsf, this);
        pm.registerEvents(parkourCmd, this);

        SnowballConfig.get().options().copyDefaults(true);
        SnowballConfig.save();
    }

    @Override
    public void onDisable() {
        getLogger().info("SnowballFight plugin disabled!");
    }
}

class ParkourCommand implements CommandExecutor, Listener {
    private Map<String, SubCommand> cmds = new HashMap<>();

    public void registerCommand(String cmd, SubCommand subCommand){
        cmds.put(cmd, subCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(sender instanceof Player p){
            if(args.length == 0){
                p.sendMessage(ChatColor.YELLOW + String.format("Existing subcommands are %s", cmds.keySet().toString()));
                return true;
            }

            if(args[0].equals("help") || args[0].equals("h")){
                p.sendMessage(ChatColor.YELLOW + String.format("Existing subcommands are %s", cmds.keySet().toString()));
                return true;
            }

            if(!cmds.containsKey(args[0].toLowerCase())){
                p.sendMessage(ChatColor.RED + "This subcommand does not exist!");
                p.sendMessage(ChatColor.YELLOW + String.format("Existing subcommands are %s", cmds.keySet().toString()));
                return true;
            }

            cmds.get(args[0]).onCommand(p, cmd, Arrays.copyOfRange(args, 1, args.length));
        }

        return true;
    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        Player player = e.getPlayer();

        Block b = player.getLocation().getBlock().getRelative(BlockFace.DOWN);

        List<String> l = (List<String>) SnowballConfig.get().getList("parkour." + "player_cps");
        List<String> newCps = new ArrayList<>();

        int playerCP = 0;
        for(String i : l){
            String p = i.split(";")[0];

            if(Objects.equals(p.toLowerCase(), player.getDisplayName().toLowerCase())){
                playerCP = Integer.parseInt(i.split(";")[1]);
            }else{
                newCps.add(i);
            }
        }
        Boolean update = false;

        if(b.getType() == Material.IRON_BLOCK){
            if(playerCP < 1){
                update = true;
                playerCP = 1;
            }
        }else if(b.getType() == Material.GOLD_BLOCK){
            if(playerCP < 2){
                update = true;
                playerCP = 2;
            }
        }else if(b.getType() == Material.DIAMOND_BLOCK){
            if(playerCP < 3){
                update = true;
                playerCP = 3;
            }
        }

        if(update){
            newCps.add(player.getDisplayName().toLowerCase() + ";" + playerCP);
            player.sendMessage(ChatColor.GREEN + String.format("Checkpoint %d reached!", playerCP));
            SnowballConfig.get().set("parkour." + "player_cps", newCps);
            SnowballConfig.get().options().copyDefaults(true);
            SnowballConfig.save();

            int goal = SnowballConfig.get().getInt("parkour." + "goal_cp");
            if(goal == playerCP){
                for(Player p : Bukkit.getOnlinePlayers()){
                    p.sendMessage(ChatColor.GREEN + String.format("%s just completed the parkour! Congratulations!", player.getDisplayName()));
                }
                Boolean completed = SnowballConfig.get().getBoolean("parkour." + "completed");

                if(!completed){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        p.sendMessage(ChatColor.GREEN + String.format("%s was the first to complete the parkour!", player.getDisplayName()));
                    }

                    SnowballConfig.get().set("parkour." + "completed", true);
                    SnowballConfig.get().options().copyDefaults(true);
                    SnowballConfig.save();
                }
            }
        }
    }
}

class ParkourRulesCommand extends SubCommand{

    void onCommand(Player player, Command cmd, String[] args) {
        player.sendMessage(ChatColor.RED + "Don't cheat.");
        player.sendMessage(ChatColor.YELLOW + "First person to complete the parkour gets a {placeholder}");
    }

}

class ParkourRespawnCommand extends SubCommand{
    private final String config_prefix = "parkour.";

    void onCommand(Player player, Command cmd, String[] args) {
        if(Globals.Ongoing != Globals.Gamemode.NONE){
            player.sendMessage(ChatColor.RED + "Cannot tp during gamemode!");
            return;
        }

        List<String> l = (List<String>) SnowballConfig.get().getList(config_prefix + "player_cps");

        int playerCP = 0;
        for(String i : l){
            String p = i.split(";")[0];
            if(!Objects.equals(p.toLowerCase(), player.getDisplayName().toLowerCase())) continue;

            playerCP = Integer.parseInt(i.split(";")[1]);
            break;
        }

        List<String> cpLocations = (List<String>) SnowballConfig.get().getList(config_prefix + "cp_locations");
        Triple<Integer, Integer, Integer> loc = StringParsing.getCoordsFromConfigLocation(cpLocations.get(playerCP));

        player.teleport(new Location(player.getWorld(), loc.first, loc.second, loc.third));
        player.sendMessage(ChatColor.YELLOW + String.format("Teleported to checkpoint %d", playerCP));
    }
}

class ParkourRestartCommand extends SubCommand{
    private final String config_prefix = "parkour.";

    void onCommand(Player player, Command cmd, String[] args) {
        if(Globals.Ongoing != Globals.Gamemode.NONE){
            player.sendMessage(ChatColor.RED + "Cannot tp during gamemode!");
            return;
        }

        List<String> cps = (List<String>) SnowballConfig.get().getList(config_prefix + "player_cps");
        List<String> newCps = new ArrayList<>();

        for(String i : cps){
            String p = i.split(";")[0];
            if(!Objects.equals(p.toLowerCase(), player.getDisplayName().toLowerCase())){
                newCps.add(i);
            }
        }
        newCps.add(player.getDisplayName().toLowerCase() + ";0");
        SnowballConfig.get().set(config_prefix + "player_cps", newCps);
        SnowballConfig.get().options().copyDefaults(true);
        SnowballConfig.save();

        String l = SnowballConfig.get().getString(config_prefix + "spawn");
        Triple<Integer, Integer, Integer> loc = StringParsing.getCoordsFromConfigLocation(l);

        player.teleport(new Location(player.getWorld(), loc.first, loc.second, loc.third));
        player.sendMessage(ChatColor.YELLOW + String.format("Parkour progress reset!"));
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
    private Map<String, SubCommand> cmds = new HashMap<>();

    public void registerCommand(String cmd, SubCommand subCommand){
        cmds.put(cmd, subCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(sender instanceof Player p && p.isOp()){
            if(args.length == 0){
                p.sendMessage(ChatColor.RED + "You need to enter some arguments.");
                p.sendMessage(ChatColor.YELLOW + String.format("To see all commands: /%s help", Globals.sbfCommandName));
                return false;
            }

            if(args[0].toLowerCase().equals("help") || args[0].toLowerCase().equals("h")){
                p.sendMessage(ChatColor.RED + "I was too lazy to implement a help command...");
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
    public ArrayList<PlayerStats> playersStats = new ArrayList<>();
    public World world;

    ArrayList<Triple<Integer, Integer, Integer>> spawnLocs = new ArrayList<>();
    Random rnd = new Random();

    public void createBoard(Player p){
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective("sbf_kills", "dummy", ChatColor.RED + "SBF Kills");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        Score score = obj.getScore(ChatColor.BLUE + "=-=-=-=-=-=-=-=");
        score.setScore(999);

        p.setScoreboard(board);
    }

    public void updateBoardForAllPlayers(){ //TODO: Super duper ineffecient but it works (maybe?)
        sortPlayerStatsByKills();
        if(playersStats.size() == 0) return;

        Scoreboard sb = playersStats.get(0).player.getScoreboard();

        for (PlayerStats ps : playersStats){
            sb.getObjective("sbf_kills").getScore(ps.player.getDisplayName()).setScore(ps.kills);
        }

        for (PlayerStats ps : playersStats){
            ps.player.setScoreboard(sb);
        }
    }

    private void sortPlayerStatsByKills() {
        playersStats.sort((o1, o2) -> {
            if (o1.kills == o2.kills) return 0;
            return o1.kills < o2.kills ? 1 : -1;
        });
    }

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

        if(Objects.equals(args[0], "del_spawn") || Objects.equals(args[0], "delete_spawn")){
            if(args.length < 2){
                player.sendMessage(ChatColor.RED + "Wrong use. Correct use (integers only as in the command \"spawns\"): del_spawn (X;Y;Z)");
                return;
            }

            ArrayList<String> spawns = StringParsing.configStringToList(SnowballConfig.get().getString(config_prefix + "spawns"));
            int sizeBefore = spawns.size();
            spawns.remove(args[1]);

            if(sizeBefore == spawns.size()){
                player.sendMessage(ChatColor.RED + "Wrong use somehow? Correct use (X, Y and Z should be integers as shown in command \"spawn\"): del_spawn (X;Y;Z)");
                return;
            }

            SnowballConfig.get().set(config_prefix + "spawns", StringParsing.listToConfigString(spawns));
            SnowballConfig.get().options().copyDefaults(true);
            SnowballConfig.save();
            player.sendMessage("Location deleted!");
        }

        if(Objects.equals(args[0], "start")) {
            if(Globals.Ongoing != Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }
            Globals.Ongoing = Globals.Gamemode.NORMAL;

            playersStats = new ArrayList<>();
            world = player.getWorld();
            spawnLocs = new ArrayList<>();

            ArrayList<String> spawns = StringParsing.configStringToList(SnowballConfig.get().getString(config_prefix + "spawns"));
            for (String s : spawns){
                spawnLocs.add(StringParsing.getCoordsFromConfigLocation(s));
            }

            for (Player p : Bukkit.getOnlinePlayers()){
                playersStats.add(new PlayerStats(p));
                Triple<Integer, Integer, Integer> spawnLoc = getRandomFightSpawn(p);
                p.getInventory().clear();
                givePlayerStartItems(p);
                p.setHealth(20.0);
                p.teleport(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));

                p.sendMessage(ChatColor.YELLOW + "Snowball fight has started!");
                p.sendMessage(ChatColor.YELLOW + String.format("First to %d kills wins!", SnowballConfig.get().getInt(config_prefix + "kills_to_win")));
                p.sendMessage(ChatColor.GREEN + "Snowblocks can be crafted and destroyed during the game!");
            }

            for(PlayerStats ps : playersStats)
                createBoard(ps.player);

            return;
        }

        if(Objects.equals(args[0], "stop")){
            if(Globals.Ongoing == Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "No fight is currently in progress!");
                return;
            }else if(Globals.Ongoing != Globals.Gamemode.NORMAL){
                player.sendMessage(ChatColor.RED + "Wrong gamemode to stop!");
                return;
            }
            stopFight();
            return;
        }

    }

    public Triple<Integer, Integer, Integer> getRandomFightSpawn(Player p){
        int num = rnd.nextInt(spawnLocs.size());
        return spawnLocs.get(num);
    }

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Snowball){
            e.setDamage(SnowballConfig.get().getDouble(config_prefix + "snowball_damage"));
            e.getEntity().getWorld().playEffect(e.getEntity().getLocation(), Effect.STEP_SOUND, 80, 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e){
        if(!e.getPlayer().isOp() && e.getBlock().getType() != Material.SNOW_BLOCK)
            e.setCancelled(true); //Let's just disable all block breaking on the server except snow blocks! :)
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        e.getDrops().clear();
        if(Globals.Ongoing != Globals.Gamemode.NORMAL) return;

        Player killed = e.getEntity();
        Player killer = killed.getKiller();
        Boolean skipKiller = killer == null || killer == killed;
        PlayerStats winner = null;

        for (PlayerStats ps : playersStats){
            if(ps.player.getDisplayName().equals(killed.getDisplayName())){
                ps.deaths++;
            }

            if(!skipKiller && ps.player.getDisplayName().equals(killer.getDisplayName())){
                ps.kills++;
                if(ps.kills == SnowballConfig.get().getInt(config_prefix + "kills_to_win")) winner = ps;
            }
        }

        updateBoardForAllPlayers();

        if(winner != null)
            stopFight();
    }

    public void stopFight(){
        if(Globals.Ongoing != Globals.Gamemode.NORMAL) return;

        sortPlayerStatsByKills();

        ArrayList<String> messagesToShow = new ArrayList<>();
        messagesToShow.add(ChatColor.YELLOW + "The gamemode is now over!");

        if(playersStats.size() >= 1) {
            PlayerStats p = playersStats.get(0);
            messagesToShow.add(ChatColor.YELLOW + String.format("1st place (%d kills): %s ", p.kills, p.player.getDisplayName()));
        }
        if(playersStats.size() >= 2) {
            PlayerStats p = playersStats.get(1);
            messagesToShow.add(ChatColor.YELLOW + String.format("2nd place (%d kills): %s ", p.kills, p.player.getDisplayName()));
        }
        if(playersStats.size() >= 3) {
            PlayerStats p = playersStats.get(2);
            messagesToShow.add(ChatColor.YELLOW + String.format("3rd place (%d kills): %s ", p.kills, p.player.getDisplayName()));
        }

        for (PlayerStats ps : playersStats){
            for (String message : messagesToShow)
                ps.player.sendMessage(message);
        }

        String global_spawn = SnowballConfig.get().getString("global_spawn");
        Triple<Integer, Integer, Integer> t = StringParsing.getCoordsFromConfigLocation(global_spawn);
        for(PlayerStats ps : playersStats){
            ps.player.teleport(new Location(world, t.first, t.second, t.third));
            ps.player.getInventory().clear();
        }

        Globals.Ongoing = Globals.Gamemode.NONE;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e){
        Player p = e.getPlayer();

        if(Globals.Ongoing == Globals.Gamemode.NORMAL){
            Triple<Integer, Integer, Integer> spawnLoc = getRandomFightSpawn(p);
            givePlayerStartItems(p);
            e.setRespawnLocation(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));
        }else if(Globals.Ongoing == Globals.Gamemode.NONE){
            String global_spawn = SnowballConfig.get().getString("global_spawn");
            Triple<Integer, Integer, Integer> spawnLoc = StringParsing.getCoordsFromConfigLocation(global_spawn);
            e.setRespawnLocation(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));
        }
    }

    public void givePlayerStartItems(Player p){
        p.getInventory().addItem(new ItemStack(Material.DIAMOND_SHOVEL, 1));
        p.getInventory().addItem(new ItemStack(Material.SNOWBALL, 500));
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

abstract class SubCommand{
    abstract void onCommand(Player player, Command cmd, String[] args);
}