
import java.util.EnumSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;

import static net.minecraft.world.item.Items.ZOMBIE_HEAD;

public class NearestAttackableTargetLongGoal<T extends LivingEntity> extends TargetGoal {
    private static final int DEFAULT_RANDOM_INTERVAL = 10;
    protected final Class<T> targetType;
    protected final int randomInterval;
    @Nullable
    protected LivingEntity target;
    protected TargetingConditions targetConditions;

    public NearestAttackableTargetLongGoal(Mob entityinsentient, Class<T> oclass, boolean flag) {
        this(entityinsentient, oclass, 10, flag, false, (Predicate)null);
    }

    public NearestAttackableTargetLongGoal(Mob entityinsentient, Class<T> oclass, boolean flag, Predicate<LivingEntity> predicate) {
        this(entityinsentient, oclass, 10, flag, false, predicate);
    }

    public NearestAttackableTargetLongGoal(Mob entityinsentient, Class<T> oclass, boolean flag, boolean flag1) {
        this(entityinsentient, oclass, 10, flag, flag1, (Predicate)null);
    }

    public NearestAttackableTargetLongGoal(Mob entityinsentient, Class<T> oclass, int i, boolean flag, boolean flag1, @Nullable Predicate<LivingEntity> predicate) {
        super(entityinsentient, flag, flag1);
        this.targetType = oclass;
        this.randomInterval = reducedTickDelay(i);
        this.setFlags(EnumSet.of(Flag.TARGET));
        this.targetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(predicate);
    }

    public boolean canUse() {
        if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
            return false;
        } else {
            this.findTarget();
            return this.target != null;
        }
    }

    protected AABB getTargetSearchArea(double d0) {
        return this.mob.getBoundingBox().inflate(d0, 50.0D, d0);
    }

    protected void findTarget() {
        if (this.mob.level.players().size() <= 0) return;

        Player closest = this.mob.level.players().get(0);
        double min_distance = 100000D;

        for (Player p : this.mob.level.players()){
            double dist = Math.sqrt(Math.pow(this.mob.getX() - p.getX(), 2) + Math.pow(this.mob.getY() - p.getY(), 2) + Math.pow(this.mob.getZ() - p.getZ(), 2));
            if (dist < min_distance) {
                min_distance = dist;
                closest = p;
            }
        }
        this.target = closest;

//        if (this.targetType != Player.class && this.targetType != ServerPlayer.class) {
//            this.target = this.mob.level.getNearestEntity(this.mob.level.getEntitiesOfClass(this.targetType, this.getTargetSearchArea(this.getFollowDistance()), (entityliving) -> {
//                return true;
//            }), this.targetConditions, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
//        } else {
//            this.target = this.mob.level.getNearestPlayer(this.targetConditions, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
//        }

    }

    public void start() {
        this.mob.setTarget(this.target, this.target instanceof ServerPlayer ? TargetReason.CLOSEST_PLAYER : TargetReason.CLOSEST_ENTITY, true);
        super.start();
    }

    public void setTarget(@Nullable LivingEntity entityliving) {
        this.target = entityliving;
    }
}