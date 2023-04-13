import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;

public class EntityZombie extends net.minecraft.world.entity.monster.Zombie {
    FlanZombie flan_entity;

    public EntityZombie(World world) {
        super(((CraftWorld)world).getHandle());
        this.flan_entity = new FlanZombie(this, FlanEntityType.ZOMBIE);
    }

    @Override
    public void tick(){
        flan_entity.onTick();
        super.tick();
    }
}