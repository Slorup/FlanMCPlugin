import jdk.jfr.Timespan;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
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



public class FlanSkeleton {
    net.minecraft.world.entity.monster.Skeleton nms_entity;
    FlanEntityType type;
    int points_worth = 0;

    public FlanSkeleton(net.minecraft.world.entity.monster.Skeleton nmsEntity, FlanEntityType type) {
        this.nms_entity = nmsEntity;
        this.type = type;

        //TODO: Set stuff here
        this.nms_entity.drops = new ArrayList<>(){};
        this.nms_entity.expToDrop = 0;
        this.nms_entity.targetSelector.removeAllGoals();
        this.nms_entity.goalSelector.removeAllGoals();
        this.nms_entity.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(100.0D);

        this.nms_entity.goalSelector.addGoal(3, new AvoidEntityGoal(this.nms_entity, Wolf.class, 6.0F, 1.0D, 1.2D));
        this.nms_entity.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this.nms_entity, 1.0D));
        this.nms_entity.goalSelector.addGoal(6, new LookAtPlayerGoal(this.nms_entity, Player.class, 8.0F));
        this.nms_entity.goalSelector.addGoal(6, new RandomLookAroundGoal(this.nms_entity));
        this.nms_entity.targetSelector.addGoal(1, new HurtByTargetGoal(this.nms_entity, new Class[0]));
        this.nms_entity.targetSelector.addGoal(2, new NearestAttackableTargetLongGoal(this.nms_entity, Player.class, false));
    }

    public void onTick() {}
}
