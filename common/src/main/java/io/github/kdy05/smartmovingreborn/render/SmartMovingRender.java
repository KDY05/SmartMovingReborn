package io.github.kdy05.smartmovingreborn.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kdy05.smartmovingreborn.logic.MovingController;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Player rendering entry points (the original's {@code SmartMovingRender}). Render thread only. */
public final class SmartMovingRender {
    private static final PoseCalculator POSE = new PoseCalculator();
    /** Models whose parts carry a Smart Moving pose, which vanilla's setupAnim does not fully overwrite. */
    private static final Set<HumanoidModel<?>> POSED = Collections.newSetFromMap(new WeakHashMap<>());

    /** Set while the first person hand is drawn, which reuses the player model and must keep vanilla's pose. */
    private static boolean renderingHand;

    private SmartMovingRender() {
    }

    public static void setRenderingHand(boolean renderingHand) {
        SmartMovingRender.renderingHand = renderingHand;
    }

    /** Whether {@code entity} is a crawling player (by the local logic for the own player). */
    public static boolean isCrawling(Entity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        MovingState state = MovingController.stateOf(player);
        return state != null && state.crawling;
    }

    /**
     * {@code HumanoidModel#setupAnim} HEAD: restores the rest pose of a model posed last time. Vanilla never
     * resets part scales, head roll or some positions, so they would otherwise stay after standing up.
     */
    public static void beforeSetupAnim(HumanoidModel<?> model) {
        if (POSED.remove(model)) {
            POSE.resetParts(model);
        }
    }

    /** {@code HumanoidModel#setupAnim} TAIL: replaces vanilla's pose with the Smart Moving one. */
    public static void setupAnim(HumanoidModel<?> model, Entity entity, float limbSwing, float limbSwingAmount,
                                 float netHeadYaw) {
        if (renderingHand || !isCrawling(entity)) {
            return;
        }
        POSE.reset(model);
        POSE.crawl(limbSwing, limbSwingAmount, netHeadYaw);
        POSE.applyTo(model);
        POSED.add(model);
    }

    /**
     * {@code PlayerRenderer#setupRotations} TAIL. The original drew a crawling player one block lower than its
     * position: its model lies down around the torso, not the feet. Vanilla's lying rotation is suppressed
     * separately, since the model pose already lies down.
     */
    public static void setupRotations(AbstractClientPlayer player, PoseStack poseStack) {
        if (isCrawling(player)) {
            poseStack.translate(0, -1, 0);
        }
    }
}
