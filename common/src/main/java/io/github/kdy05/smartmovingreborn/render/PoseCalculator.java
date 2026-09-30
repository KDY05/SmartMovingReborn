package io.github.kdy05.smartmovingreborn.render;

import io.github.kdy05.smartmovingreborn.render.ModelJoint.RotationOrder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

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

    /** 0 at {@code x0}, 1 at {@code x1}, linear and clamped in between (the original's {@code Factor}). */
    private static float factor(float x, float x0, float x1) {
        if (x0 > x1) {
            return x <= x1 ? 1 : x >= x0 ? 0 : (x0 - x) / (x0 - x1);
        }
        return x >= x1 ? 1 : x <= x0 ? 0 : (x - x0) / (x1 - x0);
    }
}
