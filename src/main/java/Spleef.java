import Utils.StringParsing;
import Utils.Triple;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class Spleef extends SubCommand implements Listener {
    private final String config_prefix = "spleef.";
    private ArrayList<PlayerSpleefStats> player_stats = new ArrayList<>();
    private long game_start_time;
    private World world;
    private Material death_block = Material.COAL_BLOCK;

    @Override
    void onCommand(Player player, Command cmd, String[] args) {
        if(args.length == 0){
            player.sendMessage(ChatColor.RED + "Wrong use - Use better");
            return;
        }

        if(Objects.equals(args[0], "start")){
            if(Globals.Ongoing != Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "Another gamemode is already in progress!");
                return;
            }
            Globals.Ongoing = Globals.Gamemode.SPLEEF;

            player_stats = new ArrayList<>();
            world = player.getWorld();
            game_start_time = System.currentTimeMillis();

            List<String> upper_coords = (List<String>) FlanPluginConfig.get().getList(config_prefix + "upper_coords");
            Triple<Integer, Integer, Integer> from = StringParsing.getCoordsFromConfigLocation(upper_coords.get(0));
            Triple<Integer, Integer, Integer> to = StringParsing.getCoordsFromConfigLocation(upper_coords.get(1)); //Assume square and x,z coords are bigger for 2nd upper_coord.
            Random rnd = new Random();

            int layers = FlanPluginConfig.get().getInt(config_prefix + "layers");
            int dist_layer = FlanPluginConfig.get().getInt(config_prefix + "dist_between_layers") + 1;
            int kill_layer = from.second - (layers * dist_layer);

            for (int x = from.first - 1; x <= to.first + 1; x++){
                for (int y = from.second + dist_layer; y >= kill_layer - dist_layer; y--){
                    world.getBlockAt(new Location(world, x, y, from.third - 1)).setType(Material.STONE_BRICKS);
                    world.getBlockAt(new Location(world, x, y, to.third + 1)).setType(Material.STONE_BRICKS);
                }
            }

            for (int z = from.third - 1; z <= to.third + 1; z++){
                for (int y = from.second + dist_layer; y >= kill_layer - dist_layer; y--) {
                    world.getBlockAt(new Location(world, from.first - 1, y, z)).setType(Material.STONE_BRICKS);
                    world.getBlockAt(new Location(world, to.first + 1, y, z)).setType(Material.STONE_BRICKS);
                }
            }

            for (int x = from.first; x <= to.first; x++){
                for (int z = from.third; z <= to.third; z++){
                    for (int y = from.second; y > kill_layer; y--){
                        if((from.second - y) % dist_layer == 0)
                            world.getBlockAt(new Location(world, x, y, z)).setType(Material.SNOW_BLOCK);
                        else
                            world.getBlockAt(new Location(world, x, y, z)).setType(Material.AIR);
                    }
                }
            }

            for (int x = from.first; x <= to.first; x++){
                for (int z = from.third; z <= to.third; z++) {
                    world.getBlockAt(new Location(world, x, kill_layer, z)).setType(death_block);
                }
            }

            for (Player p : Bukkit.getOnlinePlayers()){
                player_stats.add(new PlayerSpleefStats(p));
                int spawnX = rnd.nextInt(from.first, to.first);
                int spawnY = from.second;
                int spawnZ = rnd.nextInt(from.third, to.third);

                p.getInventory().clear();
                givePlayerStartItems(p);
                p.setHealth(20.0);
                p.teleport(new Location(world, spawnX, spawnY + 2, spawnZ));

                p.sendMessage(ChatColor.YELLOW + "Spleef has started!");
                p.sendMessage(ChatColor.YELLOW + "Survive as long as possible.");
                p.sendMessage(ChatColor.YELLOW + "Knock other players out by throwing snowballs at the blocks they are standing on!");
            }

            return;
        }

        if(Objects.equals(args[0], "stop")){
            if(Globals.Ongoing == Globals.Gamemode.NONE){
                player.sendMessage(ChatColor.RED + "No fight is currently in progress!");
                return;
            }else if(Globals.Ongoing != Globals.Gamemode.SPLEEF){
                player.sendMessage(ChatColor.RED + "Wrong gamemode to stop!");
                return;
            }
            stopGame();
            return;
        }
    }

    @EventHandler
    public void onPlayerFall(EntityDamageEvent e){
        if (Globals.Ongoing != Globals.Gamemode.SPLEEF) return;
        if(e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if(!(e.getEntity() instanceof Player)) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        if (Globals.Ongoing != Globals.Gamemode.SPLEEF)
            return;

        Player p = e.getPlayer();
        Block b = p.getLocation().getBlock().getRelative(BlockFace.DOWN);

        if(b.getType() == death_block)
            p.setHealth(0);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e){
        if (Globals.Ongoing != Globals.Gamemode.SPLEEF)
            return;

        if (e.getEntity().getType() != EntityType.SNOWBALL)
            return;

        if (e.getHitBlock() == null) return;

        if (Objects.requireNonNull(e.getHitBlock()).getType() == Material.SNOW_BLOCK){
            e.getHitBlock().setType(Material.AIR);
        }
    }

    @EventHandler
    public void onPlayerCraft(PrepareItemCraftEvent e){
        if (Globals.Ongoing != Globals.Gamemode.SPLEEF)
            return;

        e.getInventory().setResult(null);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        if(Globals.Ongoing != Globals.Gamemode.SPLEEF) return;

        for(PlayerSpleefStats ps : player_stats){
            if(e.getEntity().getDisplayName().equals(ps.player.getDisplayName())){
                ps.alive = false;
                ps.death_time = System.currentTimeMillis();
            }
        }

        int playersAliveCount = getAmountPlayersAlive();
        if(playersAliveCount <= 1)
            stopGame();
    }

    @EventHandler
    public void onPlayerLogOut(PlayerQuitEvent e){
        if(Globals.Ongoing != Globals.Gamemode.SPLEEF) return;

        for(PlayerSpleefStats ps : player_stats){
            if(e.getPlayer().getDisplayName().equals(ps.player.getDisplayName())){
                ps.alive = false;
                ps.death_time = System.currentTimeMillis();
            }
        }

        int playersAliveCount = getAmountPlayersAlive();
        if(playersAliveCount <= 1)
            stopGame();
    }

    public void stopGame(){
        if(Globals.Ongoing != Globals.Gamemode.SPLEEF) return;

        sortPlayerStatsByDeathTime();

        ArrayList<String> messagesToShow = new ArrayList<>();
        messagesToShow.add(ChatColor.YELLOW + "The gamemode is now over!");

        if(player_stats.size() >= 1) {
            PlayerSpleefStats p = player_stats.get(0);
            messagesToShow.add(ChatColor.YELLOW + String.format("1st place: %s ", p.player.getDisplayName()));
        }
        if(player_stats.size() >= 2) {
            PlayerSpleefStats p = player_stats.get(1);
            messagesToShow.add(ChatColor.YELLOW + String.format("2nd place: %s ", p.player.getDisplayName()));
        }
        if(player_stats.size() >= 3) {
            PlayerSpleefStats p = player_stats.get(2);
            messagesToShow.add(ChatColor.YELLOW + String.format("3rd place: %s ", p.player.getDisplayName()));
        }

        for (PlayerSpleefStats ps : player_stats){
            for (String message : messagesToShow)
                ps.player.sendMessage(message);
        }

        String global_spawn = FlanPluginConfig.get().getString("global_spawn");
        Triple<Integer, Integer, Integer> t = StringParsing.getCoordsFromConfigLocation(global_spawn);
        for(PlayerSpleefStats ps : player_stats){
            ps.player.teleport(new Location(world, t.first, t.second, t.third));
            ps.player.getInventory().clear();
        }

        Globals.Ongoing = Globals.Gamemode.NONE;
    }

    private void sortPlayerStatsByDeathTime() {
        player_stats.sort((o1, o2) -> {
            if(o1.death_time == 0) return -1;
            if(o2.death_time == 0) return 1;
            if (o1.death_time == o2.death_time) return 0;
            return o1.death_time < o2.death_time ? 1 : -1;
        });
    }

    public int getAmountPlayersAlive(){
        int counter = 0;

        for(PlayerSpleefStats ps : player_stats)
            if(ps.alive)
                counter++;

        return counter;
    }

    public void givePlayerStartItems(Player p){
        p.getInventory().addItem(new ItemStack(Material.SNOWBALL, 2000));
    }

}


class PlayerSpleefStats{
    public Player player;
    public long death_time;
    public Boolean alive = true;
    public PlayerSpleefStats(Player p) {player = p;}
}