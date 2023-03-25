import Utils.StringParsing;
import Utils.Triple;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

class ParkourCommandExecutor implements CommandExecutor, Listener {
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

        List<String> l = (List<String>) FlanPluginConfig.get().getList("parkour." + "player_cps");
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
            FlanPluginConfig.get().set("parkour." + "player_cps", newCps);
            FlanPluginConfig.get().options().copyDefaults(true);
            FlanPluginConfig.save();

            int goal = FlanPluginConfig.get().getInt("parkour." + "goal_cp");
            if(goal == playerCP){
                for(Player p : Bukkit.getOnlinePlayers()){
                    p.sendMessage(ChatColor.GREEN + String.format("%s just completed the parkour! Congratulations!", player.getDisplayName()));
                }
                Boolean completed = FlanPluginConfig.get().getBoolean("parkour." + "completed");

                if(!completed){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        p.sendMessage(ChatColor.GREEN + String.format("%s was the first to complete the parkour!", player.getDisplayName()));
                    }

                    FlanPluginConfig.get().set("parkour." + "completed", true);
                    FlanPluginConfig.get().options().copyDefaults(true);
                    FlanPluginConfig.save();
                }
            }
        }
    }
}

class ParkourRulesCommand extends SubCommand{

    void onCommand(Player player, Command cmd, String[] args) {
        player.sendMessage(ChatColor.RED + "Don't cheat.");
        player.sendMessage(ChatColor.YELLOW + "No rewards, only for fun!");
    }

}

class ParkourRespawnCommand extends SubCommand{
    private final String config_prefix = "parkour.";

    void onCommand(Player player, Command cmd, String[] args) {
        if(Globals.Ongoing != Globals.Gamemode.NONE){
            player.sendMessage(ChatColor.RED + "Cannot tp during gamemode!");
            return;
        }

        List<String> l = (List<String>) FlanPluginConfig.get().getList(config_prefix + "player_cps");

        int playerCP = 0;
        for(String i : l){
            String p = i.split(";")[0];
            if(!Objects.equals(p.toLowerCase(), player.getDisplayName().toLowerCase())) continue;

            playerCP = Integer.parseInt(i.split(";")[1]);
            break;
        }

        List<String> cpLocations = (List<String>) FlanPluginConfig.get().getList(config_prefix + "cp_locations");
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

        List<String> cps = (List<String>) FlanPluginConfig.get().getList(config_prefix + "player_cps");
        List<String> newCps = new ArrayList<>();

        for(String i : cps){
            String p = i.split(";")[0];
            if(!Objects.equals(p.toLowerCase(), player.getDisplayName().toLowerCase())){
                newCps.add(i);
            }
        }
        newCps.add(player.getDisplayName().toLowerCase() + ";0");
        FlanPluginConfig.get().set(config_prefix + "player_cps", newCps);
        FlanPluginConfig.get().options().copyDefaults(true);
        FlanPluginConfig.save();

        String l = FlanPluginConfig.get().getString(config_prefix + "spawn");
        Triple<Integer, Integer, Integer> loc = StringParsing.getCoordsFromConfigLocation(l);

        player.teleport(new Location(player.getWorld(), loc.first, loc.second, loc.third));
        player.sendMessage(ChatColor.YELLOW + String.format("Parkour progress reset!"));
    }
}