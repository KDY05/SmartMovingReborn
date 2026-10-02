package io.github.kdy05.smartmovingreborn.render;

import io.github.kdy05.smartmovingreborn.render.ModelJoint.RotationOrder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The Smart Render skeleton ({@code SmartRenderModel}) and Smart Moving's poses on it ({@code SmartMovingModel}).
 * Vanilla's {@link HumanoidModel} is flat; this adds the joints it lacks (torso, breast, neck, shoulders and
 * pelvis) so the original angles can be used unchanged, then hands each vanilla part its composed transform.
 */
final class PoseCalculator {
    private static final float PI = Mth.PI;
    private static final float DEGREES_TO_RADIANS = PI / 180;
    /** Leg swing speed at which the crawling animation reaches its full amplitude. */
    private static final float CRAWL_FULL_SPEED = 0.12951545f;

    private final ModelJoint outer = new ModelJoint(null);
    private final ModelJoint torso = new ModelJoint(outer);
    private final ModelJoint body = new ModelJoint(torso);
    private final ModelJoint breast = new ModelJoint(torso);
    private final ModelJoint neck = new ModelJoint(breast);
    private final ModelJoint head = new ModelJoint(neck);
    private final ModelJoint rightShoulder = new ModelJoint(breast);
    private final ModelJoint rightArm = new ModelJoint(rightShoulder);
    private final ModelJoint leftShoulder = new ModelJoint(breast);
    private final ModelJoint leftArm = new ModelJoint(leftShoulder);
    private final ModelJoint pelvic = new ModelJoint(torso);
    private final ModelJoint rightLeg = new ModelJoint(pelvic);
    private final ModelJoint leftLeg = new ModelJoint(pelvic);

    /**
     * Resets every joint to the rest pose ({@code SmartRenderModel.reset}). The shoulder and leg pivots come
     * from the model instead of the original's constants, since 1.20.1 moved them (slim arms, legs at 1.9).
     */
    void reset(HumanoidModel<?> model) {
        for (ModelJoint joint : new ModelJoint[] {outer, torso, body, breast, neck, head, rightShoulder, rightArm,
                leftShoulder, leftArm, pelvic, rightLeg, leftLeg}) {
            joint.reset();
        }
        PartPose rightArmPose = model.rightArm.getInitialPose();
        PartPose leftArmPose = model.leftArm.getInitialPose();
        PartPose rightLegPose = model.rightLeg.getInitialPose();
        PartPose leftLegPose = model.leftLeg.getInitialPose();
        rightShoulder.setRotationPoint(rightArmPose.x, rightArmPose.y, rightArmPose.z);
        leftShoulder.setRotationPoint(leftArmPose.x, leftArmPose.y, leftArmPose.z);
        pelvic.setRotationPoint(0, 12, 0);
        rightLeg.setRotationPoint(rightLegPose.x, rightLegPose.y - 12, rightLegPose.z);
        leftLeg.setRotationPoint(leftLegPose.x, leftLegPose.y - 12, leftLegPose.z);
    }

    /** Puts every part {@link #applyTo} writes back to its initial pose, scale included. */
    void resetParts(HumanoidModel<?> model) {
        model.head.resetPose();
        model.hat.resetPose();
        model.body.resetPose();
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.rightLeg.resetPose();
        model.leftLeg.resetPose();
    }

    void applyTo(HumanoidModel<?> model) {
        head.applyTo(model.head);
        model.hat.copyFrom(model.head);
        body.applyTo(model.body);
        rightArm.applyTo(model.rightArm);
        leftArm.applyTo(model.leftArm);
        rightLeg.applyTo(model.rightLeg);
        leftLeg.applyTo(model.leftLeg);
    }

    /**
     * Crawling, from {@code SmartMovingModel.setRotationAngles}. {@code limbSwing} and {@code limbSwingAmount} are
     * vanilla's walk animation values, which Smart Render computed the same way.
     */
    void crawl(float limbSwing, float limbSwingAmount, float netHeadYaw) {
        float distance = limbSwing * 1.3f;
        float walkFactor = factor(limbSwingAmount, 0, CRAWL_FULL_SPEED);
        float standFactor = factor(limbSwingAmount, CRAWL_FULL_SPEED, 0);

        head.zRot = -netHeadYaw * DEGREES_TO_RADIANS;
        head.xRot = -PI / 4;
        head.z = -2;

        torso.order = RotationOrder.YZX;
        torso.xRot = 1.3744469f;
        torso.y = 3;
        torso.zRot = Mth.cos(distance + PI / 2) * 0.09817477f * walkFactor;
        body.yRot = Mth.cos(distance + PI) * 0.09817477f * walkFactor;

        rightLeg.xRot = (Mth.cos(distance - PI / 2) * 0.09817477f + PI / 16) * walkFactor + PI / 16 * standFactor;
        leftLeg.xRot = (Mth.cos(distance - PI - PI / 2) * 0.09817477f + PI / 16) * walkFactor + PI / 16 * standFactor;
        rightLeg.zRot = (Mth.cos(distance - PI / 2) + 1) * 0.25f * walkFactor + PI / 16 * standFactor;
        leftLeg.zRot = (Mth.cos(distance - PI / 2) - 1) * 0.25f * walkFactor - PI / 16 * standFactor;
        rightLeg.yScale = 1 + (Mth.cos(distance) - 1) * 0.25f * walkFactor;
        leftLeg.yScale = 1 + (Mth.cos(distance - PI) - 1) * 0.25f * walkFactor;

        rightArm.order = RotationOrder.YZX;
        leftArm.order = RotationOrder.YZX;
        rightArm.xRot = PI * 5 / 4;
        leftArm.xRot = PI * 5 / 4;
        rightArm.zRot = (Mth.cos(distance + PI) * 0.09817477f + PI / 16) * walkFactor + PI / 8 * standFactor;
        leftArm.zRot = (Mth.cos(distance + PI) * 0.09817477f - PI / 16) * walkFactor - PI / 8 * standFactor;
        rightArm.yRot = -PI / 2;
        leftArm.yRot = PI / 2;
        rightArm.yScale = 1 + (Mth.cos(distance + PI / 2) - 1) * 0.15f * walkFactor;
        leftArm.yScale = 1 + (Mth.cos(distance - PI / 2) - 1) * 0.15f * walkFactor;
    }

    /**
     * Sliding, from {@code SmartMovingModel.setRotationAngles}: lying face down, arms stretched ahead, the body
     * rocking a little with the distance slid. The body already faces the slide's direction (see
     * {@link SmartMovingRender#beforeRender}). The original rolled the head by the view's offset from the body
     * yaw, which it had just set to the view direction, so the head does not follow the view.
     */
    void slide(float limbSwing, float limbSwingAmount) {
        float distance = limbSwing * 0.7f;
        float walkFactor = factor(limbSwingAmount, 0, 1) * 0.8f;

        head.xRot = -PI * 3 / 8;
        head.z = -2;

        outer.y = 5;
        outer.xRot = PI / 2;
        body.order = RotationOrder.YXZ;
        body.yOffset = -0.4f;
        body.y = 6.5f;
        body.xRot = Mth.cos(distance - PI / 4) * 0.09817477f * walkFactor;
        body.yRot = Mth.cos(distance + PI / 4) * 0.09817477f * walkFactor;

        rightLeg.xRot = Mth.cos(distance + PI) * 0.09817477f * walkFactor + 0.09817477f;
        leftLeg.xRot = Mth.cos(distance + PI / 2) * 0.09817477f * walkFactor + 0.09817477f;
        rightLeg.zRot = PI / 16;
        leftLeg.zRot = -PI / 16;

        rightArm.order = RotationOrder.YZX;
        leftArm.order = RotationOrder.YZX;
        rightArm.xRot = Mth.cos(distance + PI / 2) * 0.09817477f * walkFactor + PI - 0.09817477f;
        leftArm.xRot = Mth.cos(distance - PI) * 0.09817477f * walkFactor + PI - 0.09817477f;
        rightArm.zRot = PI / 8;
        leftArm.zRot = -PI / 8;
        rightArm.yRot = -PI / 2;
        leftArm.yRot = PI / 2;
    }

    /**
     * Head jumping, from {@code SmartMovingModel.setRotationAngles}: the body tilts along the flight path, the
     * arms reach ahead and close in as the flight turns down, and the limbs bend while flying flat. The body
     * already faces the flight's direction (see {@link SmartMovingRender#beforeRender}). The head stays in line
     * with the body: the original skipped the head turn for its own poses.
     *
     * @param tilt           the body's tilt, easing towards {@code PI / 2 - verticalAngle} ({@link OuterFade})
     * @param verticalAngle  the flight path's angle above the horizontal, -pi/2 to pi/2
     * @param armLimit       at most this much closing in of the arms, 0 to 1
     */
    void headJump(float tilt, float verticalAngle, float armLimit) {
        outer.xRot = tilt;
        // Set before the original faded the outer joint, so from the unfaded tilt.
        head.xRot = -(PI / 2 - verticalAngle) / 2;

        float bendFactor = Math.min(factor(verticalAngle, PI / 2, 0), factor(verticalAngle, -PI / 2, 0));
        rightArm.xRot = bendFactor * -PI / 4;
        leftArm.xRot = bendFactor * -PI / 4;
        rightLeg.xRot = bendFactor * -PI / 4;
        leftLeg.xRot = bendFactor * -PI / 4;

        float armFactorZ = Math.min(factor(verticalAngle, PI / 2, -PI / 2), armLimit);
        rightArm.zRot = PI * 7 / 8 + armFactorZ * PI / 4;
        leftArm.zRot = -PI * 7 / 8 - armFactorZ * PI / 4;
        float legFactorZ = factor(verticalAngle, -PI / 2, PI / 2);
        rightLeg.zRot = 0.09817477f * legFactorZ;
        leftLeg.zRot = -0.09817477f * legFactorZ;
    }

    /**
     * Climbing and crawl climbing, from {@code SmartMovingModel.setRotationAngles}: the body faces the view (see
     * {@link SmartMovingRender#beforeRender}) and the head only looks up and down; the arms reach up and swing
     * with the climbed height and sideways with the horizontal movement, the legs step with the height. Vine
     * climbing spreads and shortens the arms and bends the legs; crawl climbing bends the body and legs by the
     * height above the ground; feet without hands lean the body back.
     *
     * @param handsType          the hands' animation: 0 none, 1 hold, 2 reach up (1 for reaching up a vine)
     * @param feetType           the feet's animation: 0 none, 1 step
     * @param limbs              the arms' and legs' settings for these types, eased ({@link ClimbFade})
     * @param verticalSpeed      the eased vertical speed ({@code currentVerticalSpeed})
     * @param verticalDistance   the climbed height so far ({@code totalVerticalDistance})
     * @param horizontalSpeed    vanilla's walking animation speed ({@code currentHorizontalSpeed})
     * @param horizontalDistance vanilla's walking animation position ({@code totalHorizontalDistance})
     * @param distance           the whole distance moved so far ({@code totalDistance})
     * @param overGroundHeight   for crawl climbing, the box's height above the ground, at most 5, otherwise NaN
     */
    void climb(float headPitch, int handsType, int feetType, float[] limbs, boolean handsVine, boolean feetVine,
               float verticalSpeed, float verticalDistance, float horizontalSpeed, float horizontalDistance,
               float distance, float overGroundHeight) {
        head.yRot = 0;
        head.xRot = headPitch * DEGREES_TO_RADIANS;
        leftLeg.order = RotationOrder.YZX;
        rightLeg.order = RotationOrder.YZX;
        float vertical = Math.min(0.5f, verticalSpeed);
        float horizontal = Math.min(0.5f, horizontalSpeed);

        float frequency = 0.6662f;
        float handsUp = limbs[ClimbFade.HANDS_UP];
        float handsUpOffset = limbs[ClimbFade.HANDS_UP_OFFSET];
        // The original divided 0.3 by the vertical speed and multiplied it back, which gave no motion at all
        // (NaN) while not moving up or down.
        float feetUp = limbs[ClimbFade.FEET_UP];
        float feetUpOffset = limbs[ClimbFade.FEET_UP_OFFSET];
        float feetSide = limbs[ClimbFade.FEET_SIDE];

        rightArm.xRot = Mth.cos(verticalDistance * frequency + PI) * vertical * handsUp + handsUpOffset;
        leftArm.xRot = Mth.cos(verticalDistance * frequency) * vertical * handsUp + handsUpOffset;
        rightArm.yRot = Mth.cos(horizontalDistance * frequency + PI / 2) * horizontal;
        leftArm.yRot = Mth.cos(horizontalDistance * frequency) * horizontal;
        if (handsVine) {
            leftArm.yRot = leftArm.yRot * (1 + frequency) + PI / 4;
            rightArm.yRot = rightArm.yRot * (1 + frequency) - PI / 4;
            rightArm.yScale = Math.abs(Mth.cos(rightArm.xRot));
            leftArm.yScale = Math.abs(Mth.cos(leftArm.xRot));
        }

        if (!feetVine) {
            rightLeg.xRot = Mth.cos(verticalDistance * frequency) * feetUp + feetUpOffset;
            leftLeg.xRot = Mth.cos(verticalDistance * frequency + PI) * feetUp + feetUpOffset;
        }
        rightLeg.zRot = -(Mth.cos(horizontalDistance * frequency) - 1) * horizontal * feetSide;
        leftLeg.zRot = -(Mth.cos(horizontalDistance * frequency + PI / 2) + 1) * horizontal * feetSide;
        if (feetVine) {
            float bend = (Mth.cos(distance + PI) + 1) * PI / 16 + PI / 8;
            rightLeg.xRot = -bend;
            leftLeg.xRot = -bend;
            float spread = Math.max(0, Mth.cos(distance - PI / 2)) * 0.09817477f;
            leftLeg.zRot -= spread;
            rightLeg.zRot += spread;
            rightLeg.yScale = Math.abs(Mth.cos(rightLeg.xRot));
            leftLeg.yScale = Math.abs(Mth.cos(leftLeg.xRot));
        }

        if (!Float.isNaN(overGroundHeight)) {
            float height = overGroundHeight + 0.25f;
            float bodyLength = 0.7f;
            float legLength = 0.55f;
            float bodyAngle = 0;
            float legAngle = 0;
            float legSpread = 0;
            if (height < bodyLength) {
                bodyAngle = Math.max(0, (float) Math.acos(height / bodyLength));
                legAngle = PI / 2 - bodyAngle;
                legSpread = PI / 16;
            } else if (height < bodyLength + legLength) {
                legAngle = Math.max(0, (float) Math.acos((height - bodyLength) / legLength));
                legSpread = PI / 16 * (legAngle / 1.537f);
            }
            torso.xRot = bodyAngle;
            rightShoulder.xRot = -bodyAngle;
            leftShoulder.xRot = -bodyAngle;
            head.xRot = -bodyAngle;
            rightLeg.xRot = legAngle;
            leftLeg.xRot = legAngle;
            rightLeg.zRot = legSpread;
            leftLeg.zRot = -legSpread;
        }

        if (handsType == 0 && feetType != 0) {
            torso.xRot = 0.5f;
            head.xRot -= 0.5f;
            pelvic.xRot -= 0.5f;
            torso.z = -6;
        }
    }

    /**
     * How far a ceiling climber's body twists with its hand over hand movement ({@code rotateY}), in radians; the
     * whole body turns by it (see {@link SmartMovingRender#beforeRender}), the limbs and head turn back.
     */
    static float ceilingSway(float limbSwing, float limbSwingAmount) {
        return Mth.cos(limbSwing * 0.7f) * 0.44f * factor(limbSwingAmount, 0, CRAWL_FULL_SPEED);
    }

    /**
     * Climbing along a ceiling, from {@code SmartMovingModel.setRotationAngles}: both arms up, swinging with the
     * horizontal distance, the legs swinging a little. Like the original, the head does not follow the view.
     */
    void ceilingClimb(float limbSwing, float limbSwingAmount) {
        float distance = limbSwing * 0.7f;
        float walkFactor = factor(limbSwingAmount, 0, CRAWL_FULL_SPEED);
        float standFactor = factor(limbSwingAmount, CRAWL_FULL_SPEED, 0);
        leftArm.xRot = (Mth.cos(distance) * 0.52f + PI) * walkFactor + PI * standFactor;
        rightArm.xRot = (Mth.cos(distance + PI) * 0.52f - PI) * walkFactor - PI * standFactor;
        leftLeg.xRot = -Mth.cos(distance) * 0.12f * walkFactor;
        rightLeg.xRot = -Mth.cos(distance + PI) * 0.32f * walkFactor;
        float sway = ceilingSway(limbSwing, limbSwingAmount);
        rightArm.yRot = -sway;
        leftArm.yRot = -sway;
        rightLeg.yRot = -sway;
        leftLeg.yRot = -sway;
        head.yRot = -sway;
    }

    /** Jumping off a climbing hold, from {@code SmartMovingModel.setRotationAngles}: both arms up. */
    void climbJump() {
        rightArm.xRot = PI * 9 / 8;
        leftArm.xRot = PI * 9 / 8;
        rightArm.zRot = -PI / 16;
        leftArm.zRot = PI / 16;
    }

    /**
     * The attack swing of a crawling, sliding or head jumping arm ({@code animateNonStandardWorking}, then
     * {@code animateWorkingArms}): the shoulder turns as if standing, ignoring the lying body, and the arm swings
     * from there. The original did this for the right arm only; 1.20.1 also swings the left one. The swing is
     * vanilla 1.20.1's arm part of {@code setupAttackAnimation} (the original's was 1.7.10's), without the body
     * twist, which the original left out for these poses. Its lift still follows the pose's head pitch.
     *
     * @param arm         the swinging arm
     * @param attackTime  the swing's progress, above 0
     * @param shoulderYaw the standing facing's yaw from the model's, in radians
     */
    void swingArm(HumanoidArm arm, float attackTime, float shoulderYaw) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelJoint shoulder = right ? rightShoulder : leftShoulder;
        ModelJoint swinging = right ? rightArm : leftArm;
        shoulder.ignoreParentRotation = true;
        shoulder.xRot = 0;
        shoulder.yRot = shoulderYaw;
        shoulder.zRot = 0;
        swinging.reset();

        float bodyYaw = Mth.sin(Mth.sqrt(attackTime) * Mth.TWO_PI) * 0.2f * (right ? 1 : -1);
        float progress = 1 - attackTime;
        progress *= progress;
        progress *= progress;
        progress = 1 - progress;
        float lift = Mth.sin(attackTime * PI) * -(head.xRot - 0.7f) * 0.75f;
        swinging.xRot -= Mth.sin(progress * PI) * 1.2f + lift;
        swinging.yRot += bodyYaw * 2;
        swinging.zRot += Mth.sin(attackTime * PI) * -0.4f;
    }

    /**
     * The arms of side and back jumps, from {@code SmartMovingModel.animateAngleJumping}: they lift to the side.
     * Like the original's, they replace only vanilla's walking swing, so item holding, the attack swing and arm
     * bobbing still apply on top.
     *
     * @param angleJumpType the jump direction in eighths of a turn, 2 (left) to 6 (right)
     */
    static void angleJumpArms(HumanoidModel<?> model, int angleJumpType) {
        float angle = angleJumpType * PI / 4;
        float backness = 1 - Math.abs(angle - PI) / (PI / 2);
        float leftness = -Math.min(angle - PI, 0) / (PI / 2);
        float rightness = Math.max(angle - PI, 0) / (PI / 2);
        model.leftArm.zRot = -PI / 8 * rightness;
        model.rightArm.zRot = PI / 8 * leftness;
        model.leftArm.xRot = -PI / 4 * backness;
        model.rightArm.xRot = -PI / 4 * backness;
    }

    /**
     * The legs of side and back jumps, from {@code SmartMovingModel.animateAngleJumping}: they turn towards the
     * jump and spread. The head and body stay vanilla's and only the legs are written. The body turns towards
     * the view direction (see {@link SmartMovingRender#beforeRender}), and the pelvis makes up what is left so
     * that the jump angle counts from the view.
     *
     * @param angleJumpType the jump direction in eighths of a turn, 2 (left) to 6 (right)
     * @param pelvisYaw     the view yaw minus the body's target yaw, in radians
     */
    void angleJumpLegs(HumanoidModel<?> model, int angleJumpType, float pelvisYaw) {
        float angle = angleJumpType * PI / 4;
        pelvic.yRot = pelvisYaw;
        float backness = 1 - Math.abs(angle - PI) / (PI / 2);
        float leftness = -Math.min(angle - PI, 0) / (PI / 2);
        float rightness = Math.max(angle - PI, 0) / (PI / 2);

        leftLeg.order = RotationOrder.ZXY;
        rightLeg.order = RotationOrder.ZXY;
        leftLeg.xRot = PI / 16 * (1 + rightness);
        rightLeg.xRot = PI / 16 * (1 + leftness);
        leftLeg.yRot = -angle;
        rightLeg.yRot = -angle;
        leftLeg.zRot = PI / 16 * backness;
        rightLeg.zRot = -PI / 16 * backness;
        leftLeg.applyTo(model.leftLeg);
        rightLeg.applyTo(model.rightLeg);
    }

    /** 0 at {@code x0}, 1 at {@code x1}, linear and clamped in between (the original's {@code Factor}). */
    private static float factor(float x, float x0, float x1) {
        if (x0 > x1) {
            return x <= x1 ? 1 : x >= x0 ? 0 : (x0 - x) / (x0 - x1);
        }
        return x >= x1 ? 1 : x <= x0 ? 0 : (x - x0) / (x1 - x0);
    }
}
