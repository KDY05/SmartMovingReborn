package io.github.kdy05.smartmovingreborn.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    private static final String RENDER = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V";

    /** Before the cape moves to the back of the body. */
    @Inject(method = RENDER, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void smartmovingreborn$beforeCape(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                              AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                              float partialTicks, float ageInTicks, float netHeadYaw,
                                              float headPitch, CallbackInfo ci) {
        SmartMovingRender.beforeCape(player, poseStack);
    }

    /** How far the cape swings back, in degrees. */
    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"))
    private float smartmovingreborn$capeSwing(float degrees) {
        return SmartMovingRender.capeSwing(degrees);
    }
}
