import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.entity.EntityType;

public enum FlanEntityType {
    ZOMBIE("Zombie", 54, EntityType.ZOMBIE, net.minecraft.world.entity.monster.Zombie.class, EntityZombie.class),
    SKELETON("Skeleton", 51, EntityType.SKELETON, net.minecraft.world.entity.monster.Skeleton.class, EntitySkeleton.class),
    BLAZE("Blaze", 61, EntityType.BLAZE, net.minecraft.world.entity.monster.Blaze.class, EntityBlaze.class),
    WITHER("Wither", 64, EntityType.WITHER, net.minecraft.world.entity.boss.wither.WitherBoss.class, EntityWither.class);


    private final String name;
    private final int id;
    private final EntityType entityType;
    private final Class<? extends net.minecraft.world.entity.LivingEntity> nmsClass;
    private final Class<? extends net.minecraft.world.entity.LivingEntity> flanClass;

    private FlanEntityType(String name, int id, EntityType entityType, Class<? extends net.minecraft.world.entity.LivingEntity> nmsClass, Class<? extends net.minecraft.world.entity.LivingEntity> flanClass) {
        this.name = name;
        this.id = id;
        this.entityType = entityType;
        this.nmsClass = nmsClass;
        this.flanClass = flanClass;
    }

    private net.minecraft.world.entity.LivingEntity createEntity(World world) {
        try{
            return this.flanClass.getConstructor(World.class).newInstance(world);
        } catch (Exception e){
            e.printStackTrace();
        }

        return null;
    }

    public net.minecraft.world.entity.LivingEntity spawnEntity(Location location) {
        Level world = ((CraftWorld) location.getWorld()).getHandle();
        World w = world.getWorld();

        if (w == null) {
            System.out.println("w is null");
        }

        net.minecraft.world.entity.LivingEntity entity = this.createEntity(w);

        if(entity == null) {
            System.out.println("Entity is null");
        }

        //net.minecraft.world.entity.LivingEntity entity = new EntityZombie(world);
        entity.setPos(location.getX(), location.getY(), location.getZ());

        world.addFreshEntity(entity);

        return entity;
    }

//        public static void registerEntities() {
//            for (FlanEntityType flanEntityType : FlanEntityType.values()) {
//                net.minecraft.core.DefaultedRegistry r = new net.minecraft.core.DefaultedRegistry<>(Entiti);
//            }
//        }
}