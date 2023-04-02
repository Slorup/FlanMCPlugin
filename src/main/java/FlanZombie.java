import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class FlanZombie {
    int dmg = 20;
    net.minecraft.world.entity.monster.Zombie nms_zombie;
    FlanEntityType type;
    List<Material> breakable_blocks = new ArrayList<>(Arrays.asList(Material.BLACK_TERRACOTTA, Material.BROWN_TERRACOTTA));
    HashMap<Location, Integer> block_health = new HashMap<Location, Integer>();

    public FlanZombie(net.minecraft.world.entity.monster.Zombie nmsEntity, FlanEntityType type) {
        this.nms_zombie = nmsEntity;
        this.type = type;

        //TODO: Set stuff here
        this.nms_zombie.drops = new ArrayList<>(){};
    }

    public void onTick() {
        World world = nms_zombie.getBukkitEntity().getWorld();

        if (nms_zombie.getTarget() instanceof net.minecraft.world.entity.player.Player && world.getTime() % 20 == 0) {
            attemptBreakBlock(getBreakableTargetBlock());
            attemptBreakBlock(getBreakableTargetBlock().getRelative(BlockFace.UP));
        }
    }

    public Block getBreakableTargetBlock() {
        Location direction = nms_zombie.getTarget().getBukkitEntity().getLocation().subtract(nms_zombie.getBukkitEntity().getLocation());

        double dx = direction.getX();
        double dz = direction.getY();

        int bdx = 0;
        int bdz = 0;

        if (Math.abs(dx) > Math.abs(dz)) {
            bdx = (dx > 0) ? 1 : -1;
        } else {
            bdz = (dx > 0) ? 1 : -1;
        }

        return nms_zombie.level.getWorld().getBlockAt((int) Math.floor(nms_zombie.getBlockX() + bdx), (int) Math.floor(nms_zombie.getBlockY()), (int) Math.floor(nms_zombie.getBlockZ() + bdz));
    }

    void attemptBreakBlock(Block block) {
        Material type = block.getType();

        if (breakable_blocks.contains(type)) {
            Location location = block.getLocation();

            if (!block_health.containsKey(location)) block_health.put(location, 100);
            int block_hp = block_health.get(location);
            org.bukkit.entity.Entity entity = nms_zombie.getBukkitEntity();

            if (block_hp <= dmg) {
                EntityChangeBlockEvent event = new EntityChangeBlockEvent(entity, block, block.getBlockData());
                Bukkit.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.getWorld().playEffect(location, Effect.ZOMBIE_DESTROY_DOOR, 0);
                    block.setType(Material.AIR);
                    block_health.remove(location);
                }
            } else {
                block_health.replace(location, block_hp - dmg);
                entity.getWorld().playEffect(location, Effect.ZOMBIE_CHEW_WOODEN_DOOR, 0);
            }
        }
    }
}
