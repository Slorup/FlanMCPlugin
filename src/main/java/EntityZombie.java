import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;

public class EntityZombie extends net.minecraft.world.entity.monster.Zombie {
    FlanZombie fzombie;

    public EntityZombie(World world) {
        super(((CraftWorld)world).getHandle());
        this.fzombie = new FlanZombie(this, FlanEntityType.ZOMBIE);
    }

//        @Override
//        public boolean x() {
//            fzombie.onTick();
//            return super.d_();
//        }
//
//        @Override
//        public boolean d_() {
//            fzombie.onTick();
//            return super.d_();
//        }

    @Override
    public void tick(){
        fzombie.onTick();
        super.tick();
    }
}