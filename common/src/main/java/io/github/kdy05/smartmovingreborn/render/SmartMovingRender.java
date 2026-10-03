package io.github.kdy05.smartmovingreborn.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kdy05.smartmovingreborn.logic.MovingController;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Player rendering entry points (the original's {@code SmartMovingRender}). Render thread only. */
public final class SmartMovingRender {
    private static final PoseCalculator POSE = new PoseCalculator();
    /** Models whose parts carry a Smart Moving pose, which vanilla's setupAnim does not fully overwrite. */
    private static final Set<HumanoidModel<?>> POSED = Collections.newSetFromMap(new WeakHashMap<>());
    /** How far below a head jumping player the ground is looked for ({@code getOverGroundHeight(5)}). */
    private static final double OVER_GROUND_RANGE = 5;
    /** Moving less than this per tick, a ceiling climber or swimmer faces the view instead of its movement. */
    private static final double CEILING_STILL_DISTANCE = 0.015;
    /** Moving less than this per tick, a sneaking swimmer faces the view instead of its movement. */
    private static final double SWIM_SNEAK_STILL_DISTANCE = 0.005;
    /** Moving less than this per tick horizontally, a flyer faces the view instead of its movement. */
    private static final double FLY_STILL_DISTANCE = 0.05;
    /** Each player's whole-body rotation as last drawn. */
    private static final Map<Entity, OuterFade> OUTERS = new WeakHashMap<>();
    /** Each climber's limb settings as last drawn. */
    private static final Map<Entity, ClimbFade> CLIMB_FADES = new WeakHashMap<>();

    /** Set while the first person hand is drawn, which reuses the player model and must keep vanilla's pose. */
    private static boolean renderingHand;
    /** Set while the player is drawn in the inventory screen, which keeps vanilla's pose like the original's. */
    private static boolean renderingInventory;
    /** The player whose model was last given a Smart Moving pose, while its layers are drawn. */
    private static Entity posedEntity;
    /** {@link #posedEntity}'s breast transform ({@link PoseCalculator#breastTransform}). */
    private static Matrix4f posedBreast;
    /** {@link #posedEntity}'s tilt forward, in radians. */
    private static float posedTilt;
    /** The most the cape being drawn may swing back, in degrees. */
    private static float capeSwingLimit = Float.POSITIVE_INFINITY;

    private SmartMovingRender() {
    }

    public static void setRenderingHand(boolean renderingHand) {
        SmartMovingRender.renderingHand = renderingHand;
    }

    private static MovingState stateOf(Entity entity) {
        return entity instanceof AbstractClientPlayer player ? MovingController.stateOf(player) : null;
    }

    /**
     * Whether {@code entity} is a player in a small box for a Smart Moving move (crawling, sliding, head jumping,
     * crawl climbing or climb crawling), whose model is drawn a block below its position.
     */
    private static boolean isLying(Entity entity) {
        return entity instanceof Player player && MovingController.smallPose(player);
    }

    /** Climbing or crawl climbing, whose model faces the view ({@code isClimb || isCrawlClimb}). */
    private static boolean isClimbPose(MovingState state) {
        return state.climbing || state.crawlClimbing;
    }

    /** Swimming at the surface, with its own pose ({@code isSwim}); dipping keeps the standing one. */
    private static boolean isSwimPose(MovingState state) {
        return state.swimming && !state.dipping;
    }

    /**
     * Flying with its own pose ({@code isFlying}, from {@code doFlyingAnimation}), unless a move before it in the
     * original's order poses the player: climbing, swimming, diving, crawling or sliding.
     */
    private static boolean isFlyPose(MovingState state) {
        return state.flyingAnimation && !isClimbPose(state) && !state.ceilingClimbing && !isSwimPose(state)
                && !state.diving && !state.crawling && !state.sliding;
    }

    /**
     * Whether the body faces the view and vanilla turns it from there ({@code rotatePlayer}): sliding, head
     * jumping, side and back jumping, climbing, ceiling climbing, swimming, diving, and the own player flying
     * Smart Moving's way (the original did not send that).
     */
    private static boolean facesView(Entity entity, MovingState state) {
        return state.sliding || state.headJumping || isAngleJumping(state) || isClimbPose(state)
                || state.ceilingClimbing || state.swimming || state.diving
                || entity instanceof Player player && MovingController.smartFlying(player);
    }

    /** Whether the player is in a side or back jump ({@code SmartMoving.isAngleJumping}). */
    private static boolean isAngleJumping(MovingState state) {
        return state.angleJumpType > 1 && state.angleJumpType < 7;
    }

    /**
     * {@code PlayerRenderer#render} HEAD: the whole-body rotation (Smart Render's outer joint, kept in
     * {@link OuterFade} for every frame). By default the body turns towards vanilla's body yaw, easing like
     * the original's ({@code fadeRotateAngleY} is on for every pose). A sliding or head jumping body faces the
     * direction it moves in ({@code currentHorizontalAngle}), the slide without easing; the head jump also
     * tilts along its flight path, easing. A ceiling climber's body faces its movement too, easing, and so do a
     * swimmer's, a diver's and a flyer's, which lie down, easing. The faded
     * yaw replaces vanilla's only while drawing ({@link #afterRender}), since the original never stored it in the
     * entity.
     * <p>
     * What the original did store ({@code rotatePlayer}, see {@link #facesView}): those moves set the body yaw to
     * the view direction, from which vanilla turns the body on the next tick. A side or back jumping body so turns
     * towards the view, and the legs turn towards the jump from there.
     * <p>
     * Not in the inventory screen, which sets its own rotations (the original skipped it the same way).
     */
    public static void beforeRender(AbstractClientPlayer player, float partialTicks) {
        MovingState state = stateOf(player);
        renderingInventory = isInventory(partialTicks);
        if (state == null || renderingInventory) {
            return;
        }
        OuterFade outer = OUTERS.computeIfAbsent(player, p -> new OuterFade());
        float time = player.tickCount + partialTicks;
        float horizontalAngle = horizontalAngle(player, outer);
        if (isSwimPose(state)) {
            // Faces the way it swims, or the view while (nearly) still, tilting up while treading water.
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            double still = state.slow ? SWIM_SNEAK_STILL_DISTANCE : CEILING_STILL_DISTANCE;
            float facing = dx * dx + dz * dz < still * still
                    ? Mth.rotLerp(partialTicks, player.yRotO, player.getYRot()) * Mth.DEG_TO_RAD : horizontalAngle;
            outer.update(PoseCalculator.swimTilt(player.walkAnimation.speed(partialTicks)), true, facing, true,
                    time);
            outer.viewOffset = 0;
        } else if (state.diving) {
            // The original chose between the movement and the view by the whole distance ever moved, so in
            // practice always the movement.
            outer.update(PoseCalculator.diveTilt(state.levitating, state.jumping, verticalAngle(player)), true,
                    horizontalAngle, true, time);
            outer.viewOffset = 0;
        } else if (isFlyPose(state)) {
            // Faces the way it flies, or the view while hardly moving horizontally (unlike diving, whose choice
            // went by the whole distance ever moved).
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            float facing = dx * dx + dz * dz < FLY_STILL_DISTANCE * FLY_STILL_DISTANCE
                    ? Mth.rotLerp(partialTicks, player.yRotO, player.getYRot()) * Mth.DEG_TO_RAD : horizontalAngle;
            outer.update(PoseCalculator.flyTilt(state.jumping, MotionStatistics.of(player).speed(partialTicks),
                    verticalAngle(player)), true, facing, true, time);
            outer.viewOffset = 0;
        } else if (state.headJumping) {
            outer.update(Mth.PI / 2 - verticalAngle(player), true, horizontalAngle, true, time);
        } else if (state.sliding) {
            outer.update(Mth.PI / 2, false, horizontalAngle, false, time);
        } else if (isClimbPose(state)) {
            outer.update(0, false, Mth.rotLerp(partialTicks, player.yRotO, player.getYRot()) * Mth.DEG_TO_RAD, true,
                    time);
            outer.viewOffset = 0;
        } else if (state.ceilingClimbing) {
            // Faces the way it moves, or the view while (nearly) still, twisting with the hand over hand moves.
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            float facing = dx * dx + dz * dz < CEILING_STILL_DISTANCE * CEILING_STILL_DISTANCE
                    ? Mth.rotLerp(partialTicks, player.yRotO, player.getYRot()) * Mth.DEG_TO_RAD : horizontalAngle;
            float sway = PoseCalculator.ceilingSway(player.walkAnimation.position(partialTicks),
                    player.walkAnimation.speed(partialTicks));
            outer.update(0, false, facing + sway, true, time);
            outer.viewOffset = 0;
        } else {
            float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
            outer.update(0, false, bodyYaw * Mth.DEG_TO_RAD, true, time);
            outer.viewOffset = Mth.wrapDegrees(player.getYRot() - bodyYaw) * Mth.DEG_TO_RAD;
            outer.headOffset = Mth.wrapDegrees(Mth.rotLerp(partialTicks, player.yHeadRotO, player.yHeadRot) - bodyYaw)
                    * Mth.DEG_TO_RAD;
        }

        outer.bodyRot = facesView(player, state)
                ? Mth.rotLerp(partialTicks, player.yRotO, player.getYRot()) : player.yBodyRot;
        outer.bodyRotO = player.yBodyRotO;
        player.yBodyRot = outer.yaw * Mth.RAD_TO_DEG;
        player.yBodyRotO = player.yBodyRot;
    }

    /** The own player wall jumped towards {@code yaw} (degrees): the body faces it at once ({@code onStartWallJump}). */
    public static void startWallJump(Entity player, float yaw) {
        OuterFade outer = OUTERS.get(player);
        if (outer != null) {
            outer.yaw = yaw * Mth.DEG_TO_RAD;
        }
    }

    /**
     * The own player jumped back off a climbing hold ({@code onStartClimbBackJump}): the body starts a quarter
     * turn round, or a half turn for a head jump, and eases on from there.
     */
    public static void startClimbBackJump(Entity player, boolean head) {
        OuterFade outer = OUTERS.get(player);
        if (outer != null) {
            outer.yaw += head ? Mth.PI : Mth.PI / 2;
        }
    }

    /** {@code PlayerRenderer#render} TAIL: puts back the body yaw {@link #beforeRender} replaced for drawing. */
    public static void afterRender(AbstractClientPlayer player) {
        renderingInventory = false;
        posedEntity = null;
        OuterFade outer = OUTERS.get(player);
        if (outer == null || Float.isNaN(outer.bodyRot)) {
            return;
        }
        player.yBodyRot = outer.bodyRot;
        player.yBodyRotO = outer.bodyRotO;
        outer.bodyRot = Float.NaN;
    }

    /** Whether the player is drawn in the inventory screen, which renders at a partial tick of exactly 1. */
    private static boolean isInventory(float partialTicks) {
        return partialTicks == 1 && Minecraft.getInstance().screen instanceof EffectRenderingInventoryScreen;
    }

    /**
     * The direction of the last tick's horizontal movement as a yaw in radians, or the last one while not
     * moving horizontally, or the view direction before any ({@code currentHorizontalAngle}).
     */
    private static float horizontalAngle(AbstractClientPlayer player, OuterFade outer) {
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        if (dx != 0 || dz != 0) {
            outer.horizontalAngle = (float) Mth.atan2(-dx, dz);
        } else if (Float.isNaN(outer.horizontalAngle)) {
            return player.getYRot() * Mth.DEG_TO_RAD;
        }
        return outer.horizontalAngle;
    }

    /**
     * {@code HumanoidModel#setupAnim} HEAD: restores the rest pose of a model posed last time. Vanilla never
     * resets part scales, head roll or some positions, so they would otherwise stay after standing up. A side or
     * back jumping body does not crouch ({@code animateSneaking} skipped it).
     */
    public static void beforeSetupAnim(HumanoidModel<?> model, Entity entity) {
        if (POSED.remove(model)) {
            POSE.resetParts(model);
        }
        MovingState state = stateOf(entity);
        if (!renderingHand && !renderingInventory && state != null && !state.lying() && isAngleJumping(state)) {
            model.crouching = false;
        }
    }

    /**
     * {@code HumanoidModel#setupAnim}, right after the walking swing: a side or back jump's arms replace it
     * ({@code animateArmSwinging}), and the rest of vanilla's arm animation applies on top.
     */
    public static void afterArmSwing(HumanoidModel<?> model, Entity entity) {
        MovingState state = stateOf(entity);
        if (!renderingHand && !renderingInventory && state != null && !state.lying() && isAngleJumping(state)) {
            PoseCalculator.angleJumpArms(model, state.angleJumpType);
        }
    }

    /** {@code HumanoidModel#setupAnim} TAIL: replaces vanilla's pose with the Smart Moving one. */
    public static void setupAnim(HumanoidModel<?> model, Entity entity, float limbSwing, float limbSwingAmount,
                                 float ageInTicks, float netHeadYaw, float headPitch) {
        MovingState state = stateOf(entity);
        posedEntity = null;
        if (renderingHand || renderingInventory || state == null) {
            return;
        }
        boolean crawlClimb = state.crawlClimbing || state.climbing && state.crawling;
        boolean climbJump = state.climbJumping && entity instanceof Player jumper
                && MotionStatistics.of(jumper).showsClimbJump();
        if (state.climbing && !state.crawling && !state.crawlClimbing && !climbJump || crawlClimb) {
            Player player = (Player) entity;
            float partialTicks = ageInTicks - player.tickCount;
            MotionStatistics statistics = MotionStatistics.of(player);
            int handsType = state.handsVineClimbing && state.handsClimbType == 2 ? 1 : state.handsClimbType;
            float[] limbs = CLIMB_FADES.computeIfAbsent(entity, e -> new ClimbFade())
                    .update(ClimbFade.targets(handsType, state.feetClimbType), ageInTicks);
            POSE.reset(model);
            POSE.climb(headPitch, handsType, state.feetClimbType, limbs, state.handsVineClimbing,
                    state.feetVineClimbing, statistics.verticalSpeed(partialTicks),
                    statistics.verticalDistance(partialTicks), limbSwingAmount, limbSwing,
                    statistics.distance(partialTicks), crawlClimb ? overGroundHeight(player) : Float.NaN);
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (climbJump) {
            POSE.reset(model);
            POSE.climbJump();
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (state.ceilingClimbing) {
            POSE.reset(model);
            POSE.ceilingClimb(limbSwing, limbSwingAmount);
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (isSwimPose(state)) {
            OuterFade outer = OUTERS.get(entity);
            POSE.reset(model);
            POSE.swim(limbSwing, limbSwingAmount, ageInTicks,
                    outer == null ? PoseCalculator.swimTilt(limbSwingAmount) : outer.xRot);
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (state.diving) {
            Player player = (Player) entity;
            float partialTicks = ageInTicks - player.tickCount;
            MotionStatistics statistics = MotionStatistics.of(player);
            OuterFade outer = OUTERS.get(entity);
            POSE.reset(model);
            POSE.dive(outer == null ? PoseCalculator.diveTilt(state.levitating, state.jumping, verticalAngle(entity))
                    : outer.xRot, statistics.speed(partialTicks), statistics.distance(partialTicks));
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (state.crawling) {
            POSE.reset(model);
            // The original rolled the head by the view's offset from the body yaw before easing.
            OuterFade outer = OUTERS.get(entity);
            POSE.crawl(limbSwing, limbSwingAmount, outer == null ? netHeadYaw * Mth.DEG_TO_RAD : outer.headOffset);
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (state.sliding) {
            POSE.reset(model);
            POSE.slide(limbSwing, limbSwingAmount);
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (isFlyPose(state)) {
            Player player = (Player) entity;
            float partialTicks = ageInTicks - player.tickCount;
            MotionStatistics statistics = MotionStatistics.of(player);
            float speed = statistics.speed(partialTicks);
            float target = PoseCalculator.flyTilt(state.jumping, speed, verticalAngle(entity));
            OuterFade outer = OUTERS.get(entity);
            POSE.reset(model);
            POSE.fly(outer == null ? target : outer.xRot, target, speed, statistics.distance(partialTicks),
                    ageInTicks);
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (state.headJumping) {
            OuterFade outer = OUTERS.get(entity);
            POSE.reset(model);
            POSE.headJump(outer == null ? Mth.PI / 2 - verticalAngle(entity) : outer.xRot, verticalAngle(entity),
                    armLimit(entity));
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (state.fallingAnimation) {
            Player player = (Player) entity;
            POSE.reset(model);
            POSE.fall(MotionStatistics.of(player).distance(ageInTicks - player.tickCount));
            swingArm(model, entity, state, netHeadYaw);
            POSE.applyTo(model);
        } else if (isAngleJumping(state)) {
            POSE.reset(model);
            OuterFade outer = OUTERS.get(entity);
            POSE.angleJumpLegs(model, state.angleJumpType, outer == null ? 0 : outer.viewOffset);
        } else {
            return;
        }
        POSED.add(model);
        posedEntity = entity;
        posedBreast = POSE.breastTransform();
        posedTilt = POSE.tilt();
    }

    /**
     * {@code CapeLayer#render}, before the cape moves to the back: it hangs from the breast, like Smart Render's
     * ({@code ModelCapeRenderer}), and the more the body tilts forward, the less it swings back.
     */
    public static void beforeCape(Entity entity, PoseStack poseStack) {
        capeSwingLimit = Float.POSITIVE_INFINITY;
        if (followBreast(entity, poseStack)) {
            capeSwingLimit = Math.max(70.523f - posedTilt * Mth.RAD_TO_DEG, 6);
        }
    }

    /** The cape's swing back in degrees, at most {@code localAngleMax} while it hangs from a posed breast. */
    public static float capeSwing(float degrees) {
        return Math.min(degrees, capeSwingLimit);
    }

    /**
     * Moves {@code poseStack} to the breast of {@code entity}'s pose, if its model was just posed. The elytra
     * did not exist in the original; it follows the breast like the cape.
     */
    public static boolean followBreast(Entity entity, PoseStack poseStack) {
        if (entity != posedEntity) {
            return false;
        }
        poseStack.mulPoseMatrix(posedBreast);
        return true;
    }

    /**
     * Swings the attacking arm of a lying pose while it attacks ({@link PoseCalculator#swingArm}). The original
     * turned the shoulder in screen space ({@code workingAngle}), which in the third person view from behind
     * comes to this: other players' arms face their view; the own player's face its view minus the body yaw
     * stored in the entity, which sliding, head jumping, climbing, swimming, diving and Smart Moving's flying set
     * to the view, so there they face the body.
     */
    private static void swingArm(HumanoidModel<?> model, Entity entity, MovingState state, float netHeadYaw) {
        if (model.attackTime <= 0) {
            return;
        }
        LivingEntity living = (LivingEntity) entity;
        HumanoidArm arm = living.swingingArm == InteractionHand.MAIN_HAND
                ? living.getMainArm() : living.getMainArm().getOpposite();
        float shoulderYaw = netHeadYaw * Mth.DEG_TO_RAD;
        if (entity instanceof LocalPlayer) {
            OuterFade outer = OUTERS.get(entity);
            if (facesView(entity, state) && !isAngleJumping(state)) {
                shoulderYaw = 0;
            } else if (outer != null) {
                shoulderYaw = outer.viewOffset;
            }
        }
        POSE.swingArm(arm, model.attackTime, shoulderYaw);
    }

    /**
     * {@code HumanoidArmorLayer#renderArmorPiece}, right after the armor model took the player model's part
     * poses: the original stretched armor less than the body ({@code scaleArmType}, {@code scaleLegType}). No
     * armor arm stretches; leggings stretch with the leg; boots keep their length and move up the leg instead,
     * by half a block per whole length lost ({@code offsetY}, along the leg).
     */
    public static void afterArmorCopy(HumanoidModel<?> armor, Entity entity, EquipmentSlot slot) {
        if (stateOf(entity) == null) {
            return;
        }
        armor.rightArm.yScale = 1;
        armor.leftArm.yScale = 1;
        if (slot != EquipmentSlot.LEGS) {
            liftBoot(armor.rightLeg);
            liftBoot(armor.leftLeg);
        }
    }

    private static void liftBoot(ModelPart leg) {
        float lift = (1 - leg.yScale) * 0.5f * 16;
        Vector3f shift = new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot).transform(0, -lift, 0,
                new Vector3f());
        leg.x += shift.x;
        leg.y += shift.y;
        leg.z += shift.z;
        leg.yScale = 1;
    }

    /** Once per client tick for every player: the movement statistics of the climbing animation. */
    public static void tickPlayer(Player player) {
        MovingState state = stateOf(player);
        MotionStatistics.update(player, state != null && state.climbJumping);
    }

    /**
     * How high the player's box is above the ground, at most {@link #OVER_GROUND_RANGE}
     * ({@code getOverGroundHeight}): the highest block collision below it within that range.
     */
    private static float overGroundHeight(Player player) {
        AABB box = player.getBoundingBox();
        double ground = box.minY - OVER_GROUND_RANGE;
        for (VoxelShape shape : player.level().getBlockCollisions(player,
                new AABB(box.minX, box.minY - OVER_GROUND_RANGE, box.minZ, box.maxX, box.minY, box.maxZ))) {
            ground = Math.max(ground, Math.min(shape.max(Direction.Axis.Y), box.minY));
        }
        return (float) (box.minY - ground);
    }

    /** The angle of the last tick's movement above the horizontal ({@code currentVerticalAngle}). */
    private static float verticalAngle(Entity entity) {
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        float angle = (float) Math.atan((entity.getY() - entity.yo) / Math.sqrt(dx * dx + dz * dz));
        return Float.isNaN(angle) ? Mth.PI / 2 : angle;
    }

    /**
     * How far a head jumping player's arms may close in: a fifth of the height above the ground
     * ({@code getOverGroundHeight(5)}), but only while the block at the bottom of its box is solid. The original
     * looked for the first block from there down, which in 1.7.10 is always that one block, air included, and
     * then required a solid one; so in flight there is no limit, and it only applies on touching down.
     */
    private static float armLimit(Entity entity) {
        Vec3 feet = entity.position();
        if (!entity.level().getBlockState(BlockPos.containing(feet)).blocksMotion()) {
            return 1;
        }
        HitResult hit = entity.level().clip(new ClipContext(feet, feet.subtract(0, OVER_GROUND_RANGE, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        double height = hit.getType() == HitResult.Type.MISS ? OVER_GROUND_RANGE : feet.y - hit.getLocation().y;
        return (float) (height / OVER_GROUND_RANGE);
    }

    /**
     * {@code PlayerRenderer#setupRotations} TAIL. The original drew a crawling, sliding or head jumping player
     * one block lower than its position: its model lies down around the torso, not the feet. Vanilla's lying
     * rotation stays off, since {@link MovingController#suppressSwimAmount} keeps its swim amount at 0.
     */
    public static void setupRotations(AbstractClientPlayer player, PoseStack poseStack) {
        if (!renderingInventory && isLying(player)) {
            poseStack.translate(0, -1, 0);
        }
    }
}
