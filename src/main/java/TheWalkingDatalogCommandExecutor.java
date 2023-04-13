import Utils.StringParsing;
import Utils.Triple;
//import net.minecraft.core.RegistryMaterials;
//import net.minecraft.resources.MinecraftKey;
//import net.minecraft.world.entity.EntityTypes;
//import net.minecraft.world.entity.monster.EntityMonster;
//import net.minecraft.world.entity.monster.EntityZombie;
//import net.minecraft.world.entity.player.EntityHuman;
import org.bukkit.*;
import org.bukkit.block.Chest;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;

enum TwdStage{
    NORMAL,
    MID,
    LATE
}

//TODO: More types of mobs                    -
//TODO: Mob Scaling + points for killing      -
//TODO: Block destruction over time (mobs)    -
//TODO: Fix food problem? (ticktask problem?) -
//TODO: Zombie vs playerzombie interaction    -

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
    Material breakable_block = Material.SMOOTH_STONE;
    int mobs_on_map = 0;
    int max_mobs_start = 20;
    int max_mobs_increase_per_min = 1;
    int zombie_start_points = 1;
    double zombie_point_scale_per_min = 0.1;
    TwdStage stage;

    Map<Material, Integer> shop_item_to_price = Map.ofEntries(
            entry(breakable_block, 1),
            entry(Material.WOODEN_SWORD, 10),
            entry(Material.IRON_SWORD, 20),
            entry(Material.DIAMOND_SWORD, 50)
    );

    private World world;
    private ArrayList<PlayerStatsTWD> player_stats = new ArrayList<>();
    ArrayList<Location> mob_spawn_locs = new ArrayList<>();
    ArrayList<Location> food_drop_locs = new ArrayList<>();
    Location player_spawn;
    Location zombie_spawn;
    Location zombie_portal;
    Location shop_location;
    Random rnd = new Random();
    private final String config_prefix = "twd.";
    long start_time;
    BukkitTask tick_task;
    long hunger_ticks;
    long next_hunger_time;
    long food_drop_ticks;
    long last_food_drop_time;

    List<Location> no_dark_locs;
    double no_dark_distance = 25.0;

    int num_teams = 2;//-1;
    List<Team> teams;

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

        if (Objects.equals(args[0], "add_no_dark")) {
            ArrayList<String> no_dark_areas = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "no_dark"));
            String str = String.format("(%d;0;%d)", player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            player.sendMessage("Added " + str + " to no_darks");
            no_dark_areas.add(str);
            FlanPluginConfig.get().set(config_prefix + "no_dark", StringParsing.listToConfigString(no_dark_areas));
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
        }

        if (Objects.equals(args[0], "set_num_teams")) {
            player.sendMessage("Setting number of teams to " + args[1]);
            num_teams = Integer.parseInt(args[1]);
        }

        if (Objects.equals(args[0], "set_alive")) {
            if (!Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getName().equals(args[1]))) {
                return;
            }
            player.sendMessage("Setting " + args[1] + " as alive");
            List<String> alive_players = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "alive_players"));
            alive_players.add(args[1]);
            FlanPluginConfig.get().set(config_prefix + "alive_players", StringParsing.listToConfigString(alive_players));
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
        }

        if (Objects.equals(args[0], "set_zombie")) {
            if (!Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getName().equals(args[1]))) {
                return;
            }
            player.sendMessage("Setting " + args[1] + " as zombie");
            ArrayList<String> alive_players = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "alive_players"));
            alive_players.remove(args[1]);
            FlanPluginConfig.get().set(config_prefix + "alive_players", StringParsing.listToConfigString(alive_players));
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
        }

        if (Objects.equals(args[0], "list_alive")) {
            player.sendMessage("Alive players: " + FlanPluginConfig.get().getString(config_prefix + "alive_players"));
        }

        if (Objects.equals(args[0], "reset")) {
            player.sendMessage("Resetting alive_players");
            FlanPluginConfig.get().set(config_prefix + "alive_players", "none");
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();
        }

        if (Objects.equals(args[0], "start")) {
            if (Globals.Ongoing != Globals.Gamemode.NONE) {
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }

            if (num_teams == -1) {
                player.sendMessage(ChatColor.RED + "ERROR: you need to set the number of teams!");
                return;
            }

            String alive_players_str = FlanPluginConfig.get().getString(config_prefix + "alive_players");
            if (Objects.equals(alive_players_str, "none")) {
                player.sendMessage("No saved alive players, adding everyone");
                List<String> s = Bukkit.getOnlinePlayers().stream().map(it -> it.getName()).toList();
                FlanPluginConfig.get().set(config_prefix + "alive_players", StringParsing.listToConfigString(s));
                FlanPluginConfig.get().options().copyDefaults(true);
                FlanPluginConfig.save();
            }
            ArrayList<String> alive_players = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "alive_players"));

            Globals.Ongoing = Globals.Gamemode.TWD;
            player_stats = new ArrayList<>();
            mob_spawn_locs = new ArrayList<>();
            food_drop_locs = new ArrayList<>();
            world = player.getWorld();
            hunger_ticks = 200;
            food_drop_ticks = 400;
            next_hunger_time = System.currentTimeMillis() + (hunger_ticks / 20) * 1000;

            last_food_drop_time = 0;

            stage = TwdStage.NORMAL;

            String twd_spawn = FlanPluginConfig.get().getString(config_prefix + "spawn");
            Triple<Integer, Integer, Integer> twd_spawn_point = StringParsing.getCoordsFromConfigLocation(twd_spawn);
            player_spawn = new Location(world, twd_spawn_point.first, twd_spawn_point.second, twd_spawn_point.third);

            String twd_zombie_spawn = FlanPluginConfig.get().getString(config_prefix + "zombie_spawn");
            Triple<Integer, Integer, Integer> twd_zombie_spawn_point = StringParsing.getCoordsFromConfigLocation(twd_zombie_spawn);
            zombie_spawn = new Location(world, twd_zombie_spawn_point.first, twd_zombie_spawn_point.second, twd_zombie_spawn_point.third);

            String twd_zombie_portal = FlanPluginConfig.get().getString(config_prefix + "zombie_portal");
            Triple<Integer, Integer, Integer> twd_zombie_portal_point = StringParsing.getCoordsFromConfigLocation(twd_zombie_portal);
            zombie_portal = new Location(world, twd_zombie_portal_point.first, twd_zombie_portal_point.second, twd_zombie_portal_point.third);

            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerStatsTWD ps = new PlayerStatsTWD(p);
                player_stats.add(ps);

                if (!alive_players.contains(p.getName())) {
                    ps.is_zombie = true;
                }

                p.getInventory().clear();
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.setSaturation(0);
                giveStartEquipmentPlayer(p);

                if (ps.is_zombie) {
                    p.teleport(zombie_spawn);
                } else {
                    p.teleport(player_spawn);
                }

                p.sendMessage(ChatColor.YELLOW + "The Walking Datalog has begun!");
                p.sendMessage(ChatColor.YELLOW + "Defend Cassiopeia against waves of mobs.");
                p.sendMessage(ChatColor.YELLOW + "Kill mobs to obtain stregdollars and buy items in Strandvejen.");
                p.sendMessage(ChatColor.YELLOW + "Food will be dropped around the map.");
                p.sendMessage(ChatColor.YELLOW + "If you die, you will become a zombie and can kill the other players.");
                p.sendMessage(ChatColor.YELLOW + "ALL players from the last team standing will advance to the next round!");
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

            String shop_loc = FlanPluginConfig.get().getString(config_prefix + "shoploc");
            Triple<Integer, Integer, Integer> shop_loc_point = StringParsing.getCoordsFromConfigLocation(shop_loc);
            shop_location = new Location(world, shop_loc_point.first, shop_loc_point.second, shop_loc_point.third);
            setupShop();

            no_dark_locs = StringParsing.configStringToList(FlanPluginConfig.get().getString(config_prefix + "no_dark"))
                    .stream().map(it -> StringParsing.getLocFromConfigLocation(it)).toList();

            createTeams();
            updateScoreboardAll();

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

                int players_alive = getAlivePlayerCount();
                // (num_food_locations * hunger_time * hunger_per_food) / (players_alive * additional_food_rate)
                long target_food_drop_time = (long)(((double)food_drop_locs.size() * hunger_ticks * 5) / ((double)players_alive * 1.25));
                if(System.currentTimeMillis() >= last_food_drop_time + target_food_drop_time) {
                    for (Location l : food_drop_locs) {
                        if (Bukkit.getWorlds().get(0).getNearbyEntities(l, 1, 2, 1).stream().noneMatch(it -> it.getName().equals("Bread"))) {
                            world.dropItemNaturally((new Location(Bukkit.getWorlds().get(0), 0.5, 0.5, 0.5)).add(l), new ItemStack(Material.BREAD, 1));
                        }
                    }
                    last_food_drop_time = System.currentTimeMillis();
                }

                if(System.currentTimeMillis() >= next_hunger_time) {
                    for (Player p : world.getPlayers()) {
                        p.setFoodLevel(p.getFoodLevel() - 1);
                    }
                    next_hunger_time = System.currentTimeMillis() + (hunger_ticks / 20) * 1000;
                }


                // Spawn zombies that are inside the portal
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getLocation().distance(zombie_portal) <= 2) {
                        Location l = getRandomMobSpawnLoc();
                        p.teleport(l);
                    }
                }

                // Check for win condition
                List<Team> alive_teams = player_stats.stream().filter(p -> !p.is_zombie).map(p -> p.team).toList();
                if (alive_teams.stream().allMatch(t -> t == alive_teams.get(0))) {
                    win(alive_teams.get(0));
                }


                // Debuff everyone who is not inside no_dark locations
                for (PlayerStatsTWD ps : player_stats) {
                    if (!ps.is_zombie) {
                        Location proj_player_loc = new Location(ps.player.getWorld(), ps.player.getLocation().getBlockX(), 0.0, ps.player.getLocation().getBlockZ());
                        boolean is_within = false;
                        for (Location no_dark_loc : no_dark_locs) {
//                            debugMessage(String.format("distance: %f", ps.player.getLocation().distance(no_dark_loc)));
                            if (proj_player_loc.distance(no_dark_loc) <= no_dark_distance) {
                                is_within = true;
                                break;
                            }
                        }
//                        debugMessage(String.format("Player %s is %b within", ps.player.getName(), is_within));
                        if (!is_within) {
                            ps.player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1));
                            ps.player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1));
                            ps.player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1));
                        }
                    }
                }
            }
        }.runTaskTimer(FlanPlugin.getInstance(), 1L, 1L);
    }

    public void win(Team t) {
        Bukkit.broadcastMessage(t.color + String.format("Team %d is the last team standing; they have won!", t.number));
        Bukkit.broadcastMessage(ChatColor.YELLOW + "These players will continue to the next round:");
        ArrayList<String> new_alive_players = new ArrayList<>();
        for (PlayerStatsTWD p : t.players) {
            Bukkit.broadcastMessage(p.player.getName() + (p.is_zombie? " (revived)": ""));
            new_alive_players.add(p.player.getName());
        }

        FlanPluginConfig.get().set(config_prefix + "alive_players", StringParsing.listToConfigString(new_alive_players));
        FlanPluginConfig.get().options().copyDefaults(true);
        FlanPluginConfig.save();

        stopGame();
        tick_task.cancel();
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

            if (e instanceof EntityZombie z) {
                z.fzombie.setBreakableBlocks(stage);
                z.fzombie.nms_zombie.setHealth(10);
                //TODO: Set scaling stats
            }

            mobs_on_map++;
        }
    }

    public long secondsSinceStart() {
        return (System.currentTimeMillis() / 1000) - start_time;
    }

    public void setupShop() {
        world.getBlockAt(shop_location).setType(Material.CHEST);
        Chest shop_chest = (Chest) world.getBlockAt(shop_location).getState();

        ItemStack block = setBlockShopMeta(new ItemStack(breakable_block));
        ItemStack wooden_sword = setBlockShopMeta(new ItemStack(Material.WOODEN_SWORD));
        ItemStack iron_sword = setBlockShopMeta(new ItemStack(Material.IRON_SWORD));
        ItemStack diamond_sword = setBlockShopMeta(new ItemStack(Material.DIAMOND_SWORD));

        ItemStack[] menu_items = {block, wooden_sword, iron_sword, diamond_sword};
        shop_chest.getInventory().setContents(menu_items);
    }

    public void createTeams() {
        String alive_players_string = FlanPluginConfig.get().getString(config_prefix + "alive_players");
        List<String> alive_player_names = StringParsing.configStringToList(alive_players_string);

        ArrayList<PlayerStatsTWD> zombie_players = new ArrayList<>();
        ArrayList<PlayerStatsTWD> alive_players = new ArrayList<>();
        for (PlayerStatsTWD ps : player_stats) {
            boolean added = false;
            for (String zp_name : alive_player_names) {
                if (ps.player.getName().equals(zp_name)) {
                    alive_players.add(ps);
                    added = true;
                }
            }
            if (!added) {
                zombie_players.add(ps);
            }
        }

        Collections.shuffle(alive_players);

        ScoreboardManager scman = Bukkit.getScoreboardManager();
        Scoreboard team_sc = scman.getNewScoreboard();

        List<ChatColor> colors = Arrays.stream(ChatColor.values()).skip(1).toList();

        teams = new ArrayList<Team>();
        int players_taken = 0;
        for (int i = 0; i < num_teams; ++i) {
            int players_per_team = (alive_players.size() - players_taken) / (num_teams - i);
            Team new_team = new Team(alive_players.subList(players_taken, players_taken + players_per_team));
            new_team.color = colors.get(i);
            new_team.number = i;
            teams.add(new_team);
            org.bukkit.scoreboard.Team team = team_sc.registerNewTeam(String.format("team_%d", i));
            Bukkit.broadcastMessage(new_team.color + String.format("The %d team is", i));
            for (PlayerStatsTWD p : new_team.players) {
                p.team = new_team;
                p.player.setDisplayName(new_team.color + p.player.getName());
                Bukkit.broadcastMessage(new_team.color + p.player.getName());
                team.addEntry(p.player.getName());
                team.setColor(new_team.color);
            }
            players_taken += players_per_team;
        }

        org.bukkit.scoreboard.Team zombie_team = team_sc.registerNewTeam("zombies");
        zombie_team.setColor(ChatColor.GREEN);

        for (PlayerStatsTWD ps : zombie_players) {
            zombie_team.addEntry(ps.player.getName());
        }
    }

    public ItemStack setBlockShopMeta(ItemStack item) {
        ItemMeta item_meta = item.getItemMeta();
        ArrayList<String> block_lore = new ArrayList<>();
        block_lore.add("Costs: " + shop_item_to_price.get(item.getType()) + " stregdollars!");
        item_meta.setLore(block_lore);
        item.setItemMeta(item_meta);

        return item;
    }

    public void updateScoreboardAll() {
        for (PlayerStatsTWD ps : player_stats) {
            updateScoreboard(ps);
        }
    }

    public void updateScoreboard(PlayerStatsTWD ps) {
        //Create new scoreboard each time because of terrible MC scoreboard design (cannot overwrite order comparitor)
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("points", "dummy", ChatColor.RED + "Player Info");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.getScore(ChatColor.GREEN + "Mobs killed: " + ps.mobs_killed).setScore(9999);
        obj.getScore(ChatColor.GREEN + "Stregdollars: " + ps.stregdollars).setScore(9998);
        obj.getScore(" ").setScore(9996);
        obj.getScore(ChatColor.BLUE + "=-=Players Alive=-=").setScore(9995);

        for (Team t : teams) {
            int alive_count = (int)t.players.stream().filter(p -> !p.is_zombie).count();
            obj.getScore(t.color + "Team " + t.number + ": ").setScore(alive_count);
        }
        ps.player.setScoreboard(sb);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(e.getClickedInventory().getType() == InventoryType.CHEST) {
            Player player = (Player) e.getWhoClicked();
            PlayerStatsTWD playerStats = getPlayerStatsFromPlayer(player);
            if (playerStats == null) return;
            if (playerStats.is_zombie) e.setCancelled(true);

            switch (e.getCurrentItem().getType()) {
                case SMOOTH_STONE -> tryBuy(playerStats, Material.SMOOTH_STONE);
                case WOODEN_SWORD -> tryBuy(playerStats, Material.WOODEN_SWORD);
                case IRON_SWORD -> tryBuy(playerStats, Material.IRON_SWORD);
                case DIAMOND_SWORD -> tryBuy(playerStats, Material.DIAMOND_SWORD);
            }
            e.setCancelled(true);
        }
    }

    public void tryBuy(PlayerStatsTWD playerStats, Material item){
        int price = shop_item_to_price.get(item);

        if(playerStats.stregdollars >= price){
            playerStats.player.getInventory().addItem(new ItemStack(item));
            playerStats.stregdollars -= price;
        }

        updateScoreboard(playerStats);
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent e){
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        PlayerStatsTWD playerStats = getPlayerStatsFromPlayer(e.getPlayer());

        if(playerStats == null) return;

        if(playerStats.is_zombie){
            e.setRespawnLocation(zombie_spawn);
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

            if(source.getInventory().getItemInMainHand().getType() == Material.DIAMOND_PICKAXE) {
                e.setDamage(0.5);
            }

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

        if (breakable_block != e.getBlock().getType()) {
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
        }

        if(e.getEntity().getKiller() != null) {
            PlayerStatsTWD killerStats = getPlayerStatsFromPlayer(e.getEntity().getKiller());
            if (killerStats != null) {
                killerStats.players_killed++;
            }
        }
        updateScoreboardAll();
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
                //TODO: Scaling points for harder mobs
                killerStats.stregdollars += zombie_start_points;
                updateScoreboard(killerStats);
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

//        if (player_stats.size() >= 1) {
//            PlayerStatsTWD p = player_stats.get(0);
//            messagesToShow.add(ChatColor.YELLOW + String.format("1st place (%d points): %s ", p.points, p.player.getDisplayName()));
//        }
//        if (player_stats.size() >= 2) {
//            PlayerStatsTWD p = player_stats.get(1);
//            messagesToShow.add(ChatColor.YELLOW + String.format("2nd place (%d points): %s ", p.points, p.player.getDisplayName()));
//        }
//        if (player_stats.size() >= 3) {
//            PlayerStatsTWD p = player_stats.get(2);
//            messagesToShow.add(ChatColor.YELLOW + String.format("3rd place (%d points): %s ", p.points, p.player.getDisplayName()));
//        }

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


class Team {
    List<PlayerStatsTWD> players;
    ChatColor color;
    int number;

    Team(List<PlayerStatsTWD> players) {
        this.players = players;
    }
}
