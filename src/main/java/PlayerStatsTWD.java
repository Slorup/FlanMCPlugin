import org.bukkit.entity.Player;

public class PlayerStatsTWD {
    public Player player;
    public int deaths = 0;
    public int mobs_killed = 0;
    public int players_killed = 0;
    public boolean is_zombie = false;
    public int points = 0;
    public int stregdollars = 0;
    public Team team;

    public PlayerStatsTWD(Player p) {
        player = p;
    }
}
