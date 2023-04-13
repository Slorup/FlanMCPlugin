import jdk.jfr.Timespan;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.*;
import java.util.Map.Entry;

import static java.util.Map.entry;



public class FlanBlaze {
    net.minecraft.world.entity.monster.Blaze nms_entity;
    FlanEntityType type;
    int points_worth = 0;

    Map<Location, Integer> block_location_to_remaining_hp = new HashMap<Location, Integer>();

    public FlanBlaze(net.minecraft.world.entity.monster.Blaze nmsEntity, FlanEntityType type) {
        this.nms_entity = nmsEntity;
        this.type = type;

        //TODO: Set stuff here
        this.nms_entity.drops = new ArrayList<>(){};
        this.nms_entity.expToDrop = 0;
        this.nms_entity.targetSelector.removeAllGoals();
        this.nms_entity.goalSelector.removeAllGoals();
        this.nms_entity.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(100.0D);

        this.nms_entity.goalSelector.addGoal(4, new BlazeAttackGoal(this.nms_entity));
        this.nms_entity.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this.nms_entity, 1.0D));
        this.nms_entity.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this.nms_entity, 1.0D, 0.0F));
        this.nms_entity.goalSelector.addGoal(8, new LookAtPlayerGoal(this.nms_entity, Player.class, 8.0F));
        this.nms_entity.goalSelector.addGoal(8, new RandomLookAroundGoal(this.nms_entity));
        this.nms_entity.targetSelector.addGoal(1, (new HurtByTargetGoal(this.nms_entity, new Class[0])).setAlertOthers(new Class[0]));
        this.nms_entity.targetSelector.addGoal(2, new NearestAttackableTargetLongGoal(this.nms_entity, Player.class, false));

//        this.nms_skeleton.goalSelector.addGoal(3, new AvoidEntityGoal(this.nms_skeleton, Wolf.class, 6.0F, 1.0D, 1.2D));
//        this.nms_skeleton.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this.nms_skeleton, 1.0D));
//        this.nms_skeleton.goalSelector.addGoal(6, new LookAtPlayerGoal(this.nms_skeleton, Player.class, 8.0F));
//        this.nms_skeleton.goalSelector.addGoal(6, new RandomLookAroundGoal(this.nms_skeleton));
//        this.nms_skeleton.targetSelector.addGoal(1, new HurtByTargetGoal(this.nms_skeleton, new Class[0]));
//        this.nms_skeleton.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this.nms_skeleton, Player.class, true));
    }

    public void onTick() {}


    static class BlazeAttackGoal extends Goal {
        private final Blaze blaze;
        private int attackStep;
        private int attackTime;
        private int lastSeen;

        public BlazeAttackGoal(Blaze var0) {
            this.blaze = var0;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        public boolean canUse() {
            LivingEntity var0 = this.blaze.getTarget();
            return var0 != null && var0.isAlive() && this.blaze.canAttack(var0);
        }

        public void start() {
            this.attackStep = 0;
        }

        public void stop() {
            this.lastSeen = 0;
        }

        public boolean requiresUpdateEveryTick() {
            return true;
        }

        public void tick() {
            --this.attackTime;
            LivingEntity var0 = this.blaze.getTarget();
            if (var0 != null) {
                boolean var1 = this.blaze.getSensing().hasLineOfSight(var0);
                if (var1) {
                    this.lastSeen = 0;
                } else {
                    ++this.lastSeen;
                }

                double var2 = this.blaze.distanceToSqr(var0);
                if (var2 < 4.0D) {
                    if (!var1) {
                        return;
                    }

                    if (this.attackTime <= 0) {
                        this.attackTime = 20;
                        this.blaze.doHurtTarget(var0);
                    }

                    this.blaze.getMoveControl().setWantedPosition(var0.getX(), var0.getY(), var0.getZ(), 1.0D);
                } else if (var2 < this.getFollowDistance() * this.getFollowDistance() && var1) {
                    double var4 = var0.getX() - this.blaze.getX();
                    double var6 = var0.getY(0.5D) - this.blaze.getY(0.5D);
                    double var8 = var0.getZ() - this.blaze.getZ();
                    if (this.attackTime <= 0) {
                        ++this.attackStep;
                        if (this.attackStep == 1) {
                            this.attackTime = 60;
                        } else if (this.attackStep <= 4) {
                            this.attackTime = 6;
                        } else {
                            this.attackTime = 100;
                            this.attackStep = 0;
                        }

                        if (this.attackStep > 1) {
                            double var10 = Math.sqrt(Math.sqrt(var2)) * 0.5D;
                            if (!this.blaze.isSilent()) {
                                this.blaze.level.levelEvent((Player)null, 1018, this.blaze.blockPosition(), 0);
                            }

                            for(int var12 = 0; var12 < 1; ++var12) {
                                SmallFireball var13 = new SmallFireball(this.blaze.level, this.blaze, var4 + this.blaze.getRandom().nextGaussian() * var10, var6, var8 + this.blaze.getRandom().nextGaussian() * var10);
                                var13.setPos(var13.getX(), this.blaze.getY(0.5D) + 0.5D, var13.getZ());
                                this.blaze.level.addFreshEntity(var13);
                            }
                        }
                    }

                    this.blaze.getLookControl().setLookAt(var0, 10.0F, 10.0F);
                } else if (this.lastSeen < 5) {
                    this.blaze.getMoveControl().setWantedPosition(var0.getX(), var0.getY(), var0.getZ(), 1.0D);
                }

                super.tick();
            }
        }

        private double getFollowDistance() {
            return this.blaze.getAttributeValue(Attributes.FOLLOW_RANGE);
        }
    }
}
