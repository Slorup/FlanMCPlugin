import Utils.StringParsing;
import Utils.Triple;
//import net.minecraft.core.RegistryMaterials;
//import net.minecraft.resources.MinecraftKey;
//import net.minecraft.world.entity.EntityTypes;
//import net.minecraft.world.entity.monster.EntityMonster;
//import net.minecraft.world.entity.monster.EntityZombie;
//import net.minecraft.world.entity.player.EntityHuman;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

//TODO: Make Zombies target without vision - Done
//TODO: Die by hunger                      - Doneish (Make sure difficulty is set to hard)
//TODO: Food drops                         - Done
//TODO: Shop                               -
//TODO: Zombie mode after death            - Done
//TODO: Points balancing                   -
//TODO: Zombie equipment                   - Done
//TODO: Block destruction over time        -
//TODO: Player Block Destruction           -
//TODO: Update Rules                       -
//TODO: Winning condition                  -
//TODO: Teams                              -
//TODO: Set server difficulty

public class TheWalkingDatalogCommandExecutor implements CommandExecutor, Listener {
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

}

class TheWalkingDatalog extends SubCommand implements Listener {
    Set<Material> destoryableBlocks = new HashSet<>(Arrays.asList(Material.COBBLESTONE));
    int wave = 0;
    int stage = 0;
    int mobs_on_map = 0;
    int max_mobs_start = 20;
    int max_mobs_increase_per_min = 1;
    int zombie_start_points = 1;
    double zombie_point_scale_per_min = 0.1;

    private World world;
    private ArrayList<PlayerStatsTWD> player_stats = new ArrayList<>();
    ArrayList<Location> mob_spawn_locs = new ArrayList<>();
    ArrayList<Location> food_drop_locs = new ArrayList<>();
    Location player_spawn;
    Random rnd = new Random();
    private final String config_prefix = "twd.";
    long start_time;
    BukkitTask tick_task;
    long hunger_ticks;
    long next_hunger_time;
    long food_drop_ticks;
    long next_food_drop_time;

    void onCommand(Player player, Command cmd, String[] args) {
        if(!player.isOp()) return;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Wrong use - Use better");
            return;
        }

        if (Objects.equals(args[0], "spawntest")) {
            if (Globals.Ongoing != Globals.Gamemode.TWD) {
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }
            FlanEntityType type = FlanEntityType.ZOMBIE;
            Location loc = new Location(world, -30, -42, 44);
            type.spawnEntity(loc);
        }

        if (Objects.equals(args[0], "add_mob_spawn")) {
            Location loc = player.getLocation();
            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "mobspawns"));

            String newLoc = String.format("(%s;%s;%s)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            spawns.add(newLoc);
            FlanPluginConfig.get().set(config_prefix + "mobspawns", StringParsing.listToConfigString(spawns));
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
            player.sendMessage("Added new mob spawn location: " + newLoc);
            return;
        }

        if (Objects.equals(args[0], "add_food_spawn")) {
            Location loc = player.getLocation();
            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "foodlocs"));

            String newLoc = String.format("(%s;%s;%s)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            spawns.add(newLoc);
            FlanPluginConfig.get().set(config_prefix + "foodlocs", StringParsing.listToConfigString(spawns));
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
            player.sendMessage("Added new food drop location: " + newLoc);
            return;
        }

        if (Objects.equals(args[0], "mobspawns")) {
            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "mobspawns"));
            player.sendMessage(spawns.toString());
            return;
        }

        if (Objects.equals(args[0], "foodlocs")) {
            ArrayList<String> spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "foodlocs"));
            player.sendMessage(spawns.toString());
            return;
        }

        if (Objects.equals(args[0], "start")) {
            wave = 0;
            stage = 0;
            if (Globals.Ongoing != Globals.Gamemode.NONE) {
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }
            Globals.Ongoing = Globals.Gamemode.TWD;
            player_stats = new ArrayList<>();
            mob_spawn_locs = new ArrayList<>();
            food_drop_locs = new ArrayList<>();
            world = player.getWorld();
            hunger_ticks = 400;
            food_drop_ticks = 400;
            next_hunger_time = System.currentTimeMillis() + (hunger_ticks / 20) * 1000;
            next_food_drop_time = System.currentTimeMillis() + (food_drop_ticks / 20) * 1000;

            String twd_spawn = FlanPluginConfig.get().getString(config_prefix + "spawn");
            Triple<Integer, Integer, Integer> twd_spawn_point = StringParsing.getCoordsFromConfigLocation(twd_spawn);
            player_spawn = new Location(world, twd_spawn_point.first, twd_spawn_point.second, twd_spawn_point.third);

            for (Player p : Bukkit.getOnlinePlayers()) {
                player_stats.add(new PlayerStatsTWD(p));

                p.getInventory().clear();
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.setSaturation(0);
                giveStartEquipmentPlayer(p);

                p.teleport(player_spawn);

                p.sendMessage(ChatColor.YELLOW + "The Walking Datalog has begun!");
                p.sendMessage(ChatColor.YELLOW + "Defend Cassiopeia against waves of mobs.");
                p.sendMessage(ChatColor.YELLOW + "Kill mobs to obtain scorepoints and stregdollars.");
                p.sendMessage(ChatColor.YELLOW + "Stregdollars can be exchanged for equipment and daily products like Sport-Cola in Strandvejen.");
                p.sendMessage(ChatColor.YELLOW + "You have 3 lives! If the mobs manage to get their hands on Datalogijuice, the game will end.");
                p.sendMessage(ChatColor.YELLOW + "Player ranking is determined from the scorepoints.");

                Scoreboard sb = createPointsScoreboard();
                for (Player p2 : Bukkit.getOnlinePlayers()) {
                    sb.getObjective("points").getScore(p2.getDisplayName()).setScore(0);
                }
                p.setScoreboard(sb);
            }

            ArrayList<String> mob_spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "mobspawns"));
            for (String s : mob_spawns) {
                Triple<Integer, Integer, Integer> spawnLoc = StringParsing.getCoordsFromConfigLocation(s);
                mob_spawn_locs.add(new Location(world, spawnLoc.first, spawnLoc.second, spawnLoc.third));
            }

            ArrayList<String> food_spawns = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "foodlocs"));
            for (String s : food_spawns) {
                Triple<Integer, Integer, Integer> spawnLoc = StringParsing.getCoordsFromConfigLocation(s);
                food_drop_locs.add(new Location(world, spawnLoc.first, spawnLoc.second, spawnLoc.third));
            }

            start_time = System.currentTimeMillis() / 1000;
            spawnZombie();

            setupTickTimer();

            return;
        }

        if (Objects.equals(args[0], "stop")) {
            if (Globals.Ongoing == Globals.Gamemode.NONE) {
                player.sendMessage(ChatColor.RED + "No fight is currently in progress!");
                return;
            } else if (Globals.Ongoing != Globals.Gamemode.TWD) {
                player.sendMessage(ChatColor.RED + "Wrong gamemode to stop!");
                return;
            }
            stopGame();
            tick_task.cancel();
            return;
        }
    }

    public void debugMessage(String s) {
        for (Player p : world.getPlayers()) {
            p.sendMessage(s);
        }
    }

    public void setupTickTimer() {
        tick_task = new BukkitRunnable() {
            @Override
            public void run() {
                if (Globals.Ongoing != Globals.Gamemode.TWD) return;

                if(System.currentTimeMillis() >= next_food_drop_time) {
                    int players_alive = getAlivePlayerCount();
                    int num_food_drops = players_alive / 4 + 1;
                    int num_food_locs_total = food_drop_locs.size();
                    int food_drops_per_loc = num_food_drops / num_food_locs_total + 1;
                    for (int i = 0; i < food_drop_locs.size(); i++) {
                        Location l = food_drop_locs.get(i);
                        world.dropItemNaturally(l, new ItemStack(Material.BREAD, food_drops_per_loc));
                    }

                    next_food_drop_time = System.currentTimeMillis() + (food_drop_ticks / 20) * 1000;
                }

                if(System.currentTimeMillis() >= next_hunger_time) {
                    for (Player p : world.getPlayers()) {
                        p.setFoodLevel(p.getFoodLevel() - 1);
                    }
                    next_hunger_time = System.currentTimeMillis() + (hunger_ticks / 20) * 1000;
                }
            }
        }.runTaskTimer(FlanPlugin.getInstance(), 1L, 1L);
    }

    public int getAlivePlayerCount() {
        int i = 0;
        for (PlayerStatsTWD ps : player_stats) {
            if (!ps.is_zombie) i++;
        }
        return i;
    }

    public void spawnZombie() {
        long seconds_since_start = secondsSinceStart();
        long current_mob_cap = max_mobs_start + (max_mobs_increase_per_min * (seconds_since_start / 60));
//        debugMessage("trying to spawn " + (current_mob_cap - mobs_on_map) + " zombies");
        while (mobs_on_map < current_mob_cap) {
            Location mobSpawnLoc = getRandomMobSpawnLoc();
            FlanEntityType type = FlanEntityType.ZOMBIE;
            net.minecraft.world.entity.LivingEntity e = type.spawnEntity(mobSpawnLoc);

            if (e instanceof EntityZombie) {
                ((EntityZombie) e).fzombie.nms_zombie.setHealth(1);
                //TODO: Set scaling stats
            }

            mobs_on_map++;
        }
    }

    public long secondsSinceStart() {
        return (System.currentTimeMillis() / 1000) - start_time;
    }

    public Scoreboard createPointsScoreboard() {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective("points", "dummy", ChatColor.RED + "Player Info");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.getScore(ChatColor.GREEN + "Mobs killed: 0").setScore(9999);
        obj.getScore(ChatColor.GREEN + "Stregdollars: 0").setScore(9998);
        obj.getScore(ChatColor.GREEN + "Deaths: 0").setScore(9997);
        obj.getScore(" ").setScore(9996);
        obj.getScore(ChatColor.BLUE + "=-=Leaderboard=-=").setScore(9995);

        return board;
    }

    public void updateScoreboardForAllPlayers() {
        if (player_stats.size() == 0) return;

        for (PlayerStatsTWD ps : player_stats) {
            updateScoreboardWithPlayerStats(ps);
        }
    }

    public void updateScoreboardWithPlayerStats(PlayerStatsTWD ps) {
        //Create new scoreboard each time because of terrible MC scoreboard design (cannot overwrite order comparitor)
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("points", "dummy", ChatColor.RED + "Player Info");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.getScore(ChatColor.GREEN + "Mobs killed: " + ps.mobs_killed).setScore(9999);
        obj.getScore(ChatColor.GREEN + "Stregdollars: " + ps.stregdollars).setScore(9998);
        obj.getScore(ChatColor.GREEN + "Deaths: " + ps.deaths).setScore(9997);
        obj.getScore(" ").setScore(9996);
        obj.getScore(ChatColor.BLUE + "=-=Leaderboard=-=").setScore(9995);

        for (Player p2 : Bukkit.getOnlinePlayers()) {
            sb.getObjective("points").getScore(p2.getDisplayName()).setScore(ps.points);
        }
        ps.player.setScoreboard(sb);
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent e){
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        PlayerStatsTWD playerStats = getPlayerStatsFromPlayer(e.getPlayer());

        if(playerStats == null) return;

        if(playerStats.is_zombie){
            Location l = getRandomMobSpawnLoc();
            e.setRespawnLocation(l);
            giveStartEquipmentZombiePlayer(e.getPlayer());
        } else {
            e.setRespawnLocation(player_spawn);
            giveStartEquipmentPlayer(e.getPlayer());
        }
    }

    public void giveStartEquipmentPlayer(Player p) {
        ItemStack weapon = new ItemStack(Material.DIAMOND_PICKAXE);
        weapon.addEnchantment(Enchantment.DURABILITY, 3);
        p.getInventory().addItem(weapon);
    }

    public void giveStartEquipmentZombiePlayer(Player p){
        ItemStack zombie_head = new ItemStack(Material.ZOMBIE_HEAD);
        zombie_head.addEnchantment(Enchantment.BINDING_CURSE, 1);
        p.getInventory().setHelmet(zombie_head);

        ItemStack leather_armor = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack leather_leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack leather_boots = new ItemStack(Material.LEATHER_BOOTS);
        leather_armor.addEnchantment(Enchantment.BINDING_CURSE, 1);
        leather_leggings.addEnchantment(Enchantment.BINDING_CURSE, 1);
        leather_boots.addEnchantment(Enchantment.BINDING_CURSE, 1);
        p.getInventory().setChestplate(leather_armor);
        p.getInventory().setBoots(leather_boots);
        p.getInventory().setLeggings(leather_leggings);

    }

    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent e) {
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
            Player target = (Player)e.getEntity();
            Player source = (Player)e.getDamager();

            PlayerStatsTWD targetStats = getPlayerStatsFromPlayer(target);
            PlayerStatsTWD sourceStats = getPlayerStatsFromPlayer(source);

            if(targetStats == null || sourceStats == null) return;

            if (sourceStats.is_zombie && targetStats.is_zombie) {
                e.setCancelled(true);
            }
            if (!sourceStats.is_zombie && !targetStats.is_zombie){
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        e.setDropItems(false);

        if (!destoryableBlocks.contains(e.getBlock().getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        e.getPlayer().setHealth(0);
        //TODO: Fix player join/leave problems
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        PlayerStatsTWD playerStats = getPlayerStatsFromPlayer(e.getEntity());
        if (playerStats != null) {
            playerStats.is_zombie = true;
            playerStats.deaths++;
            updateScoreboardWithPlayerStats(playerStats);
        }

        if(e.getEntity().getKiller() != null) {
            PlayerStatsTWD killerStats = getPlayerStatsFromPlayer(e.getEntity().getKiller());
            if (killerStats != null) {
                killerStats.players_killed++;
                updateScoreboardWithPlayerStats(killerStats);
            }
        }

        //TODO: Check if game is over
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        e.setDroppedExp(0);
        e.getDrops().clear();

        if (!(e.getEntity() instanceof Monster)) return;

        if(e.getEntity().getKiller() != null) {
            PlayerStatsTWD killerStats = getPlayerStatsFromPlayer(e.getEntity().getKiller());
            if (killerStats != null) {
                killerStats.mobs_killed++;
                killerStats.points += zombie_start_points;
                killerStats.stregdollars += zombie_start_points;
                //TODO: Scaling points for harder mobs
                updateScoreboardWithPlayerStats(killerStats);
            }
        }

        mobs_on_map--;
        spawnZombie();
    }

    public Location getRandomMobSpawnLoc() {
        int num = rnd.nextInt(mob_spawn_locs.size());
        return mob_spawn_locs.get(num);
    }

    public void stopGame() {
        if (Globals.Ongoing != Globals.Gamemode.TWD) return;

        Globals.Ongoing = Globals.Gamemode.NONE;

        for (Entity e : world.getEntities()) {
            if (e instanceof Creature) {
                e.remove();
            }
        }
        mobs_on_map = 0;

        sortPlayerStatsByPoints();

        ArrayList<String> messagesToShow = new ArrayList<>();
        messagesToShow.add(ChatColor.YELLOW + "The Walking Datalog is now over!");

        if (player_stats.size() >= 1) {
            PlayerStatsTWD p = player_stats.get(0);
            messagesToShow.add(ChatColor.YELLOW + String.format("1st place (%d points): %s ", p.points, p.player.getDisplayName()));
        }
        if (player_stats.size() >= 2) {
            PlayerStatsTWD p = player_stats.get(1);
            messagesToShow.add(ChatColor.YELLOW + String.format("2nd place (%d points): %s ", p.points, p.player.getDisplayName()));
        }
        if (player_stats.size() >= 3) {
            PlayerStatsTWD p = player_stats.get(2);
            messagesToShow.add(ChatColor.YELLOW + String.format("3rd place (%d points): %s ", p.points, p.player.getDisplayName()));
        }

        for (PlayerStatsTWD ps : player_stats) {
            for (String message : messagesToShow)
                ps.player.sendMessage(message);
        }

        String global_spawn = FlanPluginConfig.get().getString("global_spawn");
        Triple<Integer, Integer, Integer> t = StringParsing.getCoordsFromConfigLocation(global_spawn);
        for (PlayerStatsTWD ps : player_stats) {
            ps.player.teleport(new Location(world, t.first, t.second, t.third));
            ps.player.getInventory().clear();
        }

    }

    public PlayerStatsTWD getPlayerStatsFromPlayer(Player p) {
        for (PlayerStatsTWD ps : player_stats) {
            if (ps.player == p) return ps;
        }
        return null;
    }

    private void sortPlayerStatsByPoints() {
        player_stats.sort((o1, o2) -> {
            if (o1.points == o2.points) return 0;
            return o1.points < o2.points ? 1 : -1;
        });
    }
}

class PlayerStatsTWD{
    public Player player;
    public int deaths = 0;
    public int mobs_killed = 0;
    public int players_killed = 0;
    public boolean is_zombie = false;
    public int points = 0;
    public int stregdollars = 0;

    public PlayerStatsTWD(Player p) {player = p;}
}
