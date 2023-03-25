import Utils.StringParsing;
import Utils.Triple;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

class NormalSnowballFight extends SubCommand implements Listener {
    private final String config_prefix = "normal.";
    public ArrayList<PlayerNormalSnowballStats> playersStats = new ArrayList<>();
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

        for (PlayerNormalSnowballStats ps : playersStats){
            sb.getObjective("sbf_kills").getScore(ps.player.getDisplayName()).setScore(ps.kills);
        }

        for (PlayerNormalSnowballStats ps : playersStats){
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
            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "spawns"));
            player.sendMessage(spawns.toString());
            return;
        }

        if(Objects.equals(args[0], "add_spawn")){
            Location loc = player.getLocation();
            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "spawns"));

            String newLoc = String.format("(%s;%s;%s)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            spawns.add(newLoc);
            FlanPluginConfig.get().set(config_prefix + "spawns", StringParsing.listToConfigString(spawns));
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
            player.sendMessage("Added new location: " + newLoc);
            return;
        }

        if(Objects.equals(args[0], "del_spawn") || Objects.equals(args[0], "delete_spawn")){
            if(args.length < 2){
                player.sendMessage(ChatColor.RED + "Wrong use. Correct use (integers only as in the command \"spawns\"): del_spawn (X;Y;Z)");
                return;
            }

            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "spawns"));
            int sizeBefore = spawns.size();
            spawns.remove(args[1]);

            if(sizeBefore == spawns.size()){
                player.sendMessage(ChatColor.RED + "Wrong use somehow? Correct use (X, Y and Z should be integers as shown in command \"spawn\"): del_spawn (X;Y;Z)");
                return;
            }

            FlanPluginConfig.get().set(config_prefix + "spawns", StringParsing.listToConfigString(spawns));
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
            player.sendMessage("Location deleted!");
        }

        if(Objects.equals(args[0], "start")) {
            if(Globals.Ongoing != Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }
            Globals.Ongoing = Globals.Gamemode.SNOWBALLFIGHT;

            playersStats = new ArrayList<>();
            world = player.getWorld();
            spawnLocs = new ArrayList<>();

            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "spawns"));
            for (String s : spawns){
                spawnLocs.add(StringParsing.getCoordsFromConfigLocation(s));
            }

            for (Player p : Bukkit.getOnlinePlayers()){
                playersStats.add(new PlayerNormalSnowballStats(p));
                Triple<Integer, Integer, Integer> spawnLoc = getRandomFightSpawn(p);
                p.getInventory().clear();
                givePlayerStartItems(p);
                p.setHealth(20.0);
                p.teleport(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));

                p.sendMessage(ChatColor.YELLOW + "Snowball fight has started!");
                p.sendMessage(ChatColor.YELLOW + String.format("First to %d kills wins!", FlanPluginConfig.get().getInt(config_prefix + "kills_to_win")));
                p.sendMessage(ChatColor.GREEN + "Snowblocks can be crafted and destroyed during the game!");
            }

            for(PlayerNormalSnowballStats ps : playersStats)
                createBoard(ps.player);

            return;
        }

        if(Objects.equals(args[0], "stop")){
            if(Globals.Ongoing == Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "No fight is currently in progress!");
                return;
            }else if(Globals.Ongoing != Globals.Gamemode.SNOWBALLFIGHT){
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
        if(Globals.Ongoing != Globals.Gamemode.SNOWBALLFIGHT)
            return;

        if(e.getDamager() instanceof Snowball){
            e.setDamage(FlanPluginConfig.get().getDouble(config_prefix + "snowball_damage"));
            e.getEntity().getWorld().playEffect(e.getEntity().getLocation(), Effect.STEP_SOUND, 80, 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e){
        if(Globals.Ongoing != Globals.Gamemode.SNOWBALLFIGHT)
            return;

        if(!e.getPlayer().isOp() && e.getBlock().getType() != Material.SNOW_BLOCK)
            e.setCancelled(true); //Let's just disable all block breaking on the server except snow blocks! :)
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        e.getDrops().clear();
        if(Globals.Ongoing != Globals.Gamemode.SNOWBALLFIGHT) return;

        Player killed = e.getEntity();
        Player killer = killed.getKiller();
        Boolean skipKiller = killer == null || killer == killed;
        PlayerNormalSnowballStats winner = null;

        for (PlayerNormalSnowballStats ps : playersStats){
            if(ps.player.getDisplayName().equals(killed.getDisplayName())){
                ps.deaths++;
            }

            if(!skipKiller && ps.player.getDisplayName().equals(killer.getDisplayName())){
                ps.kills++;
                if(ps.kills == FlanPluginConfig.get().getInt(config_prefix + "kills_to_win")) winner = ps;
            }
        }

        updateBoardForAllPlayers();

        if(winner != null)
            stopFight();
    }

    public void stopFight(){
        if(Globals.Ongoing != Globals.Gamemode.SNOWBALLFIGHT) return;

        sortPlayerStatsByKills();

        ArrayList<String> messagesToShow = new ArrayList<>();
        messagesToShow.add(ChatColor.YELLOW + "The gamemode is now over!");

        if(playersStats.size() >= 1) {
            PlayerNormalSnowballStats p = playersStats.get(0);
            messagesToShow.add(ChatColor.YELLOW + String.format("1st place (%d kills): %s ", p.kills, p.player.getDisplayName()));
        }
        if(playersStats.size() >= 2) {
            PlayerNormalSnowballStats p = playersStats.get(1);
            messagesToShow.add(ChatColor.YELLOW + String.format("2nd place (%d kills): %s ", p.kills, p.player.getDisplayName()));
        }
        if(playersStats.size() >= 3) {
            PlayerNormalSnowballStats p = playersStats.get(2);
            messagesToShow.add(ChatColor.YELLOW + String.format("3rd place (%d kills): %s ", p.kills, p.player.getDisplayName()));
        }

        for (PlayerNormalSnowballStats ps : playersStats){
            for (String message : messagesToShow)
                ps.player.sendMessage(message);
        }

        String global_spawn = FlanPluginConfig.get().getString("global_spawn");
        Triple<Integer, Integer, Integer> t = StringParsing.getCoordsFromConfigLocation(global_spawn);
        for(PlayerNormalSnowballStats ps : playersStats){
            ps.player.teleport(new Location(world, t.first, t.second, t.third));
            ps.player.getInventory().clear();
        }

        Globals.Ongoing = Globals.Gamemode.NONE;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e){
        Player p = e.getPlayer();

        if(Globals.Ongoing == Globals.Gamemode.SNOWBALLFIGHT){
            Triple<Integer, Integer, Integer> spawnLoc = getRandomFightSpawn(p);
            givePlayerStartItems(p);
            e.setRespawnLocation(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));
        }else if(Globals.Ongoing == Globals.Gamemode.NONE){
            String global_spawn = FlanPluginConfig.get().getString("global_spawn");
            Triple<Integer, Integer, Integer> spawnLoc = StringParsing.getCoordsFromConfigLocation(global_spawn);
            e.setRespawnLocation(new Location(p.getWorld(), spawnLoc.first, spawnLoc.second, spawnLoc.third));
        }
    }

    public void givePlayerStartItems(Player p){
        p.getInventory().addItem(new ItemStack(Material.DIAMOND_SHOVEL, 1));
        p.getInventory().addItem(new ItemStack(Material.SNOWBALL, 500));
    }

}


class PlayerNormalSnowballStats{
    public Player player;
    public double damage_done = 0;
    public double damage_received = 0;
    public int kills = 0;
    public int deaths = 0;

    public PlayerNormalSnowballStats(Player p){
        player = p;
    }
}