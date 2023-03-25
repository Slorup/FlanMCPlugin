import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class SnowballFightCommandExecutor implements CommandExecutor {
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

class FlanPluginConfig{
    private static File file;
    private static FileConfiguration customFile;

    public static void setup(){
        file = new File(Bukkit.getServer().getPluginManager().getPlugin("FlanPlugin").getDataFolder(),"flanplugin_config.yml");

        if(!file.exists()){
            try{
                file.createNewFile();
            }catch(IOException e){
                //TODO: Sucks
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
        FlanPluginConfig.reload();
        player.sendMessage(ChatColor.GREEN + "FlanPlugin reloaded!");
    }
}