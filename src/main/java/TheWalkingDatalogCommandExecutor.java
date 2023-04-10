import Utils.StringParsing;
import Utils.Triple;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
//import net.minecraft.core.RegistryMaterials;
//import net.minecraft.resources.MinecraftKey;
//import net.minecraft.world.entity.EntityTypes;
//import net.minecraft.world.entity.monster.EntityMonster;
//import net.minecraft.world.entity.monster.EntityZombie;
//import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.lang.reflect.Field;
import java.util.*;

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
    int wave = 0;
    int stage = 0;
    int mobs_on_map = 0;

    private World world;
    private ArrayList<PlayerStatsTWD> player_stats = new ArrayList<>();
    private final String config_prefix = "twd.";

    void onCommand(Player player, Command cmd, String[] args) {
        if(args.length == 0){
            player.sendMessage(ChatColor.RED + "Wrong use - Use better");
            return;
        }

        if(Objects.equals(args[0], "spawntest")) {
            if(Globals.Ongoing != Globals.Gamemode.TWD) {
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }
            FlanEntityType type = FlanEntityType.ZOMBIE;
            Location loc = new Location(world, -30, -42, 44);
            type.spawnEntity(loc);
        }

        if(Objects.equals(args[0], "start")) {
            wave = 0;
            stage = 0;
            if(Globals.Ongoing != Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }
            Globals.Ongoing = Globals.Gamemode.TWD;
            player_stats = new ArrayList<>();
            world = player.getWorld();

            String twd_spawn = FlanPluginConfig.get().getString(config_prefix + "spawn");
            Triple<Integer, Integer, Integer> twd_spawn_point = StringParsing.getCoordsFromConfigLocation(twd_spawn);

            for (Player p : Bukkit.getOnlinePlayers()){
                player_stats.add(new PlayerStatsTWD(p));

                p.getInventory().clear();
                p.setHealth(20.0);
                p.setSaturation(20);
                p.teleport(new Location(world, twd_spawn_point.first, twd_spawn_point.second, twd_spawn_point.third));

                p.sendMessage(ChatColor.YELLOW + "The Walking Datalog has begun!");
                p.sendMessage(ChatColor.YELLOW + "Defend Cassiopeia against waves of mobs.");
                p.sendMessage(ChatColor.YELLOW + "Kill mobs to obtain scorepoints and stregdollars.");
                p.sendMessage(ChatColor.YELLOW + "Stregdollars can be exchanged for equipment and daily products like Sport-Cola in Strandvejen.");
                p.sendMessage(ChatColor.YELLOW + "You have 3 lives! If the mobs manage to get their hands on Datalogijuice, the game will end.");
                p.sendMessage(ChatColor.YELLOW + "Player ranking is determined from the scorepoints.");

                Scoreboard sb = createPointsScoreboard();
                for (Player p2 : Bukkit.getOnlinePlayers()) { sb.getObjective("points").getScore(p2.getDisplayName()).setScore(0); }
                p.setScoreboard(sb);
            }

            spawnWave();
            //TODO
        }

        if(Objects.equals(args[0], "stop")) {
            if(Globals.Ongoing == Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "No fight is currently in progress!");
                return;
            }else if(Globals.Ongoing != Globals.Gamemode.TWD){
                player.sendMessage(ChatColor.RED + "Wrong gamemode to stop!");
                return;
            }
            stopGame();
            return;
        }
    }

    public void spawnWave() {
        int zombieAmount = (wave + 1) * 4;
//        for (int i = 0; i < zombieAmount; i++) {
//            Zombie z = (Zombie)world.spawnEntity(new Location(world, -30,-42,52), EntityType.ZOMBIE);
////            z.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
//
//            net.minecraft.world.entity.monster.Zombie nms_z = (net.minecraft.world.entity.monster.Zombie) ((CraftEntity) z).getHandle();
//
//        }
//        mobs_remaining_current_stage = zombieAmount;
    }

    public void spawnZombie() {

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
        if(player_stats.size() == 0) return;

        for (PlayerStatsTWD ps : player_stats) {
            updateScoreboardWithPlayerStats(ps);
        }
    }

    public void updateScoreboardWithPlayerStats(PlayerStatsTWD ps) {
        //Create new scoreboard each time because of terrible MC scoreboard design (cannot overwrite order comparitor)
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("points", "dummy", ChatColor.RED + "Player Info");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.getScore(ChatColor.GREEN + "Mobs killed: " + ps.mobs_killed).setScore(999);
        obj.getScore(ChatColor.GREEN + "Stregdollars: " + ps.stregdollars).setScore(998);
        obj.getScore(ChatColor.GREEN + "Deaths: " + ps.deaths).setScore(997);
        obj.getScore(" ").setScore(996);
        obj.getScore(ChatColor.BLUE + "=-=Leaderboard=-=").setScore(995);

        for (Player p2 : Bukkit.getOnlinePlayers()) { sb.getObjective("points").getScore(p2.getDisplayName()).setScore(ps.points); }
        ps.player.setScoreboard(sb);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        //TODO
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Monster)) return;

        Player killer = e.getEntity().getKiller();
        for (PlayerStatsTWD ps : player_stats) {
            if (ps.player == killer) {
                ps.mobs_killed++;
                //TODO: Points/Stregdollars
                //TODO: Leaderboard
                updateScoreboardWithPlayerStats(ps);
            }
        }

        mobs_on_map--;
//        if (mobs_remaining_current_stage == 0) {
//            wave++;
//            spawnWave();
//        }
    }

    public void stopGame() {
        if(Globals.Ongoing != Globals.Gamemode.TWD) return;

        for (Entity e : world.getEntities()) {
            if (e instanceof Creature) {
                e.remove();
            }
        }

        sortPlayerStatsByPoints();

        ArrayList<String> messagesToShow = new ArrayList<>();
        messagesToShow.add(ChatColor.YELLOW + "The Walking Datalog is now over!");

        if(player_stats.size() >= 1) {
            PlayerStatsTWD p = player_stats.get(0);
            messagesToShow.add(ChatColor.YELLOW + String.format("1st place (%d points): %s ", p.points, p.player.getDisplayName()));
        }
        if(player_stats.size() >= 2) {
            PlayerStatsTWD p = player_stats.get(1);
            messagesToShow.add(ChatColor.YELLOW + String.format("2nd place (%d points): %s ", p.points, p.player.getDisplayName()));
        }
        if(player_stats.size() >= 3) {
            PlayerStatsTWD p = player_stats.get(2);
            messagesToShow.add(ChatColor.YELLOW + String.format("3rd place (%d points): %s ", p.points, p.player.getDisplayName()));
        }

        for (PlayerStatsTWD ps : player_stats){
            for (String message : messagesToShow)
                ps.player.sendMessage(message);
        }

        String global_spawn = FlanPluginConfig.get().getString("global_spawn");
        Triple<Integer, Integer, Integer> t = StringParsing.getCoordsFromConfigLocation(global_spawn);
        for(PlayerStatsTWD ps : player_stats){
            ps.player.teleport(new Location(world, t.first, t.second, t.third));
            ps.player.getInventory().clear();
        }

        Globals.Ongoing = Globals.Gamemode.NONE;
    }

    public void giveStartEquipment(Player p) {
        //TODO:
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
    public int points = 0;
    public int stregdollars = 0;

    public PlayerStatsTWD(Player p) {player = p;}
}


//@SuppressWarnings("rawtypes")
//public class CustomEntityRegistry extends RegistryMaterials {
//
//    private static CustomEntityRegistry instance = null;
//
//    private final BiMap<MinecraftKey, Class<? extends Entity>> customEntities = HashBiMap.create();
//    private final BiMap<Class<? extends Entity>, MinecraftKey> customEntityClasses = this.customEntities.inverse();
//    private final Map<Class<? extends Entity>, Integer> customEntityIds = new HashMap<>();
//
//    private final RegistryMaterials wrapped;
//
//    private CustomEntityRegistry(RegistryMaterials original) {
//        this.wrapped = original;
//    }
//
//    public static CustomEntityRegistry getInstance() {
//        if (instance != null) {
//            return instance;
//        }
//
//        instance = new CustomEntityRegistry(EntityTypes.b);
//
//        try {
//            //TODO: Update name on version change (RegistryMaterials)
//            Field registryMaterialsField = EntityTypes.class.getDeclaredField("b");
//            registryMaterialsField.setAccessible(true);
//
//            Field modifiersField = Field.class.getDeclaredField("modifiers");
//            modifiersField.setAccessible(true);
//            modifiersField.setInt(registryMaterialsField, registryMaterialsField.getModifiers() & ~Modifier.FINAL);
//
//            registryMaterialsField.set(null, instance);
//        } catch (Exception e) {
//            instance = null;
//
//            throw new RuntimeException("Unable to override the old entity RegistryMaterials", e);
//        }
//
//        return instance;
//    }
//
//    public static void registerCustomEntity(int entityId, String entityName, Class<? extends Entity> entityClass) {
//        getInstance().putCustomEntity(entityId, entityName, entityClass);
//    }
//
//    public void putCustomEntity(int entityId, String entityName, Class<? extends Entity> entityClass) {
//        MinecraftKey minecraftKey = new MinecraftKey(entityName);
//
//        this.customEntities.put(minecraftKey, entityClass);
//        this.customEntityIds.put(entityClass, entityId);
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public Class<? extends Entity> get(Object key) {
//        if (this.customEntities.containsKey(key)) {
//            return this.customEntities.get(key);
//        }
//
//        return (Class<? extends Entity>) wrapped.get(key);
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public int a(Object key) { //TODO: Update name on version change (getId)
//        if (this.customEntityIds.containsKey(key)) {
//            return this.customEntityIds.get(key);
//        }
//
//        return this.wrapped.a(key);
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public MinecraftKey b(Object value) { //TODO: Update name on version change (getKey)
//        if (this.customEntityClasses.containsKey(value)) {
//            return this.customEntityClasses.get(value);
//        }
//
//        return (MinecraftKey) wrapped.b(value);
//    }
//}