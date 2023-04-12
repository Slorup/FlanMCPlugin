import jdk.jfr.Timespan;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
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
    int points_worth = 0;

    boolean all_blocks_breakable = false;
    List<Material> breakable_blocks = new ArrayList<>();

    Map<Material, Integer> block_to_base_hp = Map.ofEntries(
            entry(Material.SMOOTH_STONE, 5),
            entry(Material.GLASS_PANE, 2),
            entry(Material.WHITE_STAINED_GLASS_PANE, 2),
            entry(Material.WHITE_STAINED_GLASS, 3),
            entry(Material.GLASS, 3),
            entry(Material.IRON_DOOR, 20),
            entry(Material.OAK_DOOR, 7),
            entry(Material.WARPED_DOOR, 7),
            entry(Material.BLUE_TERRACOTTA, 3),
            entry(Material.BRICKS, 6),
            entry(Material.POLISHED_BASALT, 5),
            entry(Material.YELLOW_TERRACOTTA, 3),
            entry(Material.LIGHT_BLUE_TERRACOTTA, 3),
            entry(Material.OAK_PLANKS, 4),
            entry(Material.SMOOTH_QUARTZ, 5),
            entry(Material.BLUE_CONCRETE, 3),
            entry(Material.CYAN_CONCRETE, 3)
    );

    int dmg_multiplier = 1;

    Map<Location, Integer> block_location_to_remaining_hp = new HashMap<Location, Integer>();

    public void setBreakableBlocks(TwdStage stage) {
        switch (stage){
            case NORMAL -> setBreakableBlocksNormal();
            case MID -> setBreakableBlocksMid();
            case LATE -> setBreakableBlocksLate();
        }
    }

    void setBreakableBlocksNormal() {
        all_blocks_breakable = false;
        breakable_blocks = new ArrayList<>(Arrays.asList(Material.SMOOTH_STONE, Material.GLASS_PANE, Material.GLASS, Material.WHITE_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS, Material.IRON_DOOR, Material.OAK_DOOR, Material.WARPED_DOOR));
    }

    void setBreakableBlocksMid() {
        all_blocks_breakable = false;
        breakable_blocks = new ArrayList<>(Arrays.asList(Material.SMOOTH_STONE, Material.GLASS_PANE, Material.GLASS, Material.WHITE_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS, Material.IRON_DOOR, Material.OAK_DOOR, Material.WARPED_DOOR, Material.BRICKS, Material.BLUE_TERRACOTTA, Material.BLUE_CONCRETE, Material.CYAN_CONCRETE, Material.OAK_PLANKS, Material.SMOOTH_QUARTZ, Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.POLISHED_BASALT));
    }

    void setBreakableBlocksLate() {
        all_blocks_breakable = true;
    }

    public FlanZombie(net.minecraft.world.entity.monster.Zombie nmsEntity, FlanEntityType type) {
        this.nms_zombie = nmsEntity;
        this.type = type;

        //TODO: Set stuff here
        this.nms_zombie.drops = new ArrayList<>(){};
        this.nms_zombie.expToDrop = 0;
        this.nms_zombie.targetSelector.removeAllGoals();
        this.nms_zombie.goalSelector.removeAllGoals();
        this.nms_zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(100.0D);
        this.nms_zombie.goalSelector.addGoal(8, new LookAtPlayerGoal(this.nms_zombie, Player.class, 8.0F));
        this.nms_zombie.goalSelector.addGoal(8, new RandomLookAroundGoal(this.nms_zombie));
        this.nms_zombie.goalSelector.addGoal(2, new ZombieAttackGoal(this.nms_zombie, 1.0D, false));
        this.nms_zombie.goalSelector.addGoal(7, new CassStrollGoal(this.nms_zombie, 1.0D));
        this.nms_zombie.targetSelector.addGoal(1, (new HurtByTargetGoal(this.nms_zombie, new Class[0])).setAlertOthers(new Class[]{ZombifiedPiglin.class}));
        this.nms_zombie.targetSelector.addGoal(2, new NearestAttackableTargetLongGoal(this.nms_zombie, Player.class, false));
//        this.nms_zombie.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this.nms_zombie, Player.class, false));
    }

    public void onTick() {
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
        double dz = direction.getZ();

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

        if (all_blocks_breakable || breakable_blocks.contains(type)) {
            Location location = block.getLocation();

            int block_hp = 1;

            if(!all_blocks_breakable) {
                if (!block_location_to_remaining_hp.containsKey(location)) block_location_to_remaining_hp.put(location, block_to_base_hp.get(type));
                block_hp = block_location_to_remaining_hp.get(location);
            }

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
