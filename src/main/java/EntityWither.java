import net.minecraft.world.entity.EntityType;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;

public class EntityWither extends net.minecraft.world.entity.boss.wither.WitherBoss {
    FlanWither flan_entity;

    public EntityWither(World world) {
        super(EntityType.WITHER, ((CraftWorld)world).getHandle());
        this.flan_entity = new FlanWither(this, FlanEntityType.WITHER);
    }

    @Override
    public void tick(){
        flan_entity.onTick();
        super.tick();
    }
}