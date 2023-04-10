import jdk.jfr.Timespan;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.*;
import java.util.Map.Entry;

import static java.util.Map.entry;

public class FlanZombie {
    int base_dmg = 1;
    net.minecraft.world.entity.monster.Zombie nms_zombie;
    FlanEntityType type;
    int tick = 0;

//    List<Material> breakable_blocks = new ArrayList<>(Arrays.asList(Material.BLACK_TERRACOTTA, Material.BROWN_TERRACOTTA));

    Map<Material, Integer> block_to_base_hp = Map.ofEntries(
            entry(Material.BLACK_TERRACOTTA, 5) //TODO: Insert more blocks here
    );

    int dmg_multiplier = 1;

    Map<Location, Integer> block_location_to_remaining_hp = new HashMap<Location, Integer>();

    public FlanZombie(net.minecraft.world.entity.monster.Zombie nmsEntity, FlanEntityType type) {
        this.nms_zombie = nmsEntity;
        this.type = type;

        //TODO: Set stuff here
        this.nms_zombie.drops = new ArrayList<>(){};
    }

    public void onTick() {
        World world = nms_zombie.getBukkitEntity().getWorld();
        tick++;
        tick = tick % 10;

        //Once a second, damage block
        if (nms_zombie.getTarget() instanceof net.minecraft.world.entity.player.Player && tick == 0) {
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

        if (block_to_base_hp.containsKey(type)) {
            Location location = block.getLocation();

            if (!block_location_to_remaining_hp.containsKey(location)) block_location_to_remaining_hp.put(location, block_to_base_hp.get(type));
            int block_hp = block_location_to_remaining_hp.get(location);
            org.bukkit.entity.Entity entity = nms_zombie.getBukkitEntity();

            int dmg = base_dmg * dmg_multiplier;

            if (block_hp <= dmg) {
                EntityChangeBlockEvent event = new EntityChangeBlockEvent(entity, block, block.getBlockData());
                Bukkit.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.getWorld().playEffect(location, Effect.ZOMBIE_DESTROY_DOOR, 0);
                    block.setType(Material.AIR);
                    block_location_to_remaining_hp.remove(location);
                }
            } else {
                block_location_to_remaining_hp.replace(location, block_hp - dmg);
                entity.getWorld().playEffect(location, Effect.ZOMBIE_CHEW_WOODEN_DOOR, 0);
            }
        }
    }
}
