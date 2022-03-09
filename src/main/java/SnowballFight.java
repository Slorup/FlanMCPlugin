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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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

        SnowballConfig.get().options().copyDefaults(true);
        SnowballConfig.save();
    }

    @Override
    public void onDisable() {
        getLogger().info("SnowballFight plugin disabled!");
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
    private Map<String, SubCommand> cmds = new HashMap<>();

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
    public int killsToWin = (SnowballConfig.get().getInt(config_prefix + "killsToWin"));

    ArrayList<Triple<Integer, Integer, Integer>> spawnLocs = new ArrayList<>();
    Random rnd = new Random();
    //TimerTask task;

    public void createBoard(Player p){
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        Objective obj = board.registerNewObjective("sbf_kills", "dummy", ChatColor.RED + "SBF Kills");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        Score score = obj.getScore(ChatColor.BLUE + "=-=-=-=-=-=-=-=");
        score.setScore(999);

        p.setScoreboard(board);
    }

    public void updateBoardForAllPlayers(){ //TODO: Super duper ineffecient but it works (maybe?)
        Collections.sort(playersStats, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats o1, PlayerStats o2) {
                if(o1.kills == o2.kills) return 0;
                return o1.kills < o2.kills ? 1 : -1;
            }
        });
        if(playersStats.size() == 0) return;

        Scoreboard sb = playersStats.get(0).player.getScoreboard();

        for (PlayerStats ps : playersStats){
            sb.getObjective("sbf_kills").getScore(ps.player.getDisplayName()).setScore(ps.kills);
        }

        for (PlayerStats ps : playersStats){
            ps.player.setScoreboard(sb);
        }
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
            if(isOngoing){
                player.sendMessage(ChatColor.RED + "SnowballFight is already in progress!");
                return;
            }
            isOngoing = true;

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
                p.getInventory().addItem(new ItemStack(Material.SNOWBALL, 2000));
                p.setHealth(20.0);
                p.teleport(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));

                p.sendMessage(ChatColor.YELLOW + "Snowball fight has started!");
            }

            for(PlayerStats ps : playersStats)
                createBoard(ps.player);

            return;
        }

        if(Objects.equals(args[0], "stop")){
            if(!isOngoing){
                player.sendMessage(ChatColor.RED + "No fight is currently in progress!");
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
        if(!e.getPlayer().isOp())
            e.setCancelled(true); //Let's just disable all block breaking on the server! :)
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        e.getDrops().clear();
        if(!isOngoing) return;

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
                if(ps.kills == killsToWin) winner = ps;
            }
        }

        updateBoardForAllPlayers();

        if(winner != null)
            stopFight();
    }

    public void stopFight(){
        if(!isOngoing) return;


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
                ps.player.sendMessage(ChatColor.YELLOW + "WINNER WINNER CHICKEN DINNER!");
                ps.player.sendMessage(ChatColor.YELLOW + String.format("1st place (%d kills): %s ", first.kills, first.player.getDisplayName()));
                ps.player.sendMessage(ChatColor.YELLOW + String.format("2nd place (%d kills): %s ", second.kills, second.player.getDisplayName()));
                ps.player.sendMessage(ChatColor.YELLOW + String.format("3rd place (%d kills): %s ", third.kills, third.player.getDisplayName()));
            }
        }else if(playersStats.size() >= 2){ //TODO: Quick spaghetti for debugging. Remove later
            PlayerStats first = playersStats.get(0);
            PlayerStats second = playersStats.get(1);

            for (PlayerStats ps : playersStats){
                ps.player.sendMessage(ChatColor.YELLOW + "WINNER WINNER CHICKEN DINNER!");
                ps.player.sendMessage(ChatColor.YELLOW + String.format("1st place (%d kills): %s ", first.kills, first.player.getDisplayName()));
                ps.player.sendMessage(ChatColor.YELLOW + String.format("2nd place (%d kills): %s ", second.kills, second.player.getDisplayName()));
            }
        }else if(playersStats.size() >= 1){
            PlayerStats first = playersStats.get(0);

            for (PlayerStats ps : playersStats){
                ps.player.sendMessage(ChatColor.YELLOW + "WINNER WINNER CHICKEN DINNER!");
                ps.player.sendMessage(ChatColor.YELLOW + String.format("1st place (%d kills): %s ", first.kills, first.player.getDisplayName()));
            }
        }

        String global_spawn = SnowballConfig.get().getString("global_spawn");
        Triple<Integer, Integer, Integer> t = StringParsing.getCoordsFromConfigLocation(global_spawn);
        for(PlayerStats ps : playersStats){
            ps.player.teleport(new Location(world, t.first, t.second, t.third));
        }

        isOngoing = false;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e){
        Player p = e.getPlayer();

        if(isOngoing){
            Triple<Integer, Integer, Integer> spawnLoc = getRandomFightSpawn(p);
            p.getInventory().addItem(new ItemStack(Material.SNOWBALL, 2000));
            e.setRespawnLocation(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));
        }else{
            String global_spawn = SnowballConfig.get().getString("global_spawn");
            Triple<Integer, Integer, Integer> spawnLoc = StringParsing.getCoordsFromConfigLocation(global_spawn);
            e.setRespawnLocation(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));
        }
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