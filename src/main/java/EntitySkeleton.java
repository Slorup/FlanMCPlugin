import net.minecraft.world.entity.EntityType;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;

public class EntitySkeleton extends net.minecraft.world.entity.monster.Skeleton {
    FlanSkeleton flan_entity;

    public EntitySkeleton(World world) {
        super(EntityType.SKELETON, ((CraftWorld)world).getHandle());
        this.flan_entity = new FlanSkeleton(this, FlanEntityType.SKELETON);
    }

    @Override
    public void tick(){
        flan_entity.onTick();
        super.tick();
    }
}