import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class HungerTask extends BukkitRunnable {

    private final JavaPlugin plugin;

    public HungerTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if(p.getFoodLevel() > 0){
                p.setFoodLevel(p.getFoodLevel() - 1);
            }
        }
    }
}
