import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FlanWither {
    net.minecraft.world.entity.boss.wither.WitherBoss nms_entity;
    FlanEntityType type;
    int points_worth = 0;

    public FlanWither(net.minecraft.world.entity.boss.wither.WitherBoss nmsEntity, FlanEntityType type) {
        this.nms_entity = nmsEntity;
        this.type = type;

        //TODO: Set stuff here
        this.nms_entity.drops = new ArrayList<>(){};
        this.nms_entity.expToDrop = 0;
        this.nms_entity.targetSelector.removeAllGoals();
        this.nms_entity.goalSelector.removeAllGoals();
        this.nms_entity.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(100.0D);

        this.nms_entity.goalSelector.addGoal(2, new RangedAttackGoal(this.nms_entity, 1.0D, 40, 20.0F));
        this.nms_entity.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this.nms_entity, 1.0D));
        this.nms_entity.goalSelector.addGoal(6, new LookAtPlayerGoal(this.nms_entity, Player.class, 8.0F));
        this.nms_entity.goalSelector.addGoal(7, new RandomLookAroundGoal(this.nms_entity));
        this.nms_entity.targetSelector.addGoal(1, new HurtByTargetGoal(this.nms_entity, new Class[0]));
        this.nms_entity.targetSelector.addGoal(2, new NearestAttackableTargetLongGoal(this.nms_entity, Player.class, false));
    }

    public void onTick() {}
}
