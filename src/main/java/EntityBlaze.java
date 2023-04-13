import net.minecraft.world.entity.EntityType;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;

public class EntityBlaze extends net.minecraft.world.entity.monster.Blaze {
    FlanBlaze flan_entity;

    public EntityBlaze(World world) {
        super(EntityType.BLAZE, ((CraftWorld)world).getHandle());
        this.flan_entity = new FlanBlaze(this, FlanEntityType.BLAZE);
    }

    @Override
    public void tick(){
        flan_entity.onTick();
        super.tick();
    }
}