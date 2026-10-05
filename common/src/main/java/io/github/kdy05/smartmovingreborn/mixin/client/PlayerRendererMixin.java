package io.github.kdy05.smartmovingreborn.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    private static final String SETUP_ROTATIONS =
            "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V";

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void smartmovingreborn$render(AbstractClientPlayer player, float entityYaw, float partialTicks,
                                          PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                          CallbackInfo ci) {
        SmartMovingRender.beforeRender(player, partialTicks);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void smartmovingreborn$afterRender(AbstractClientPlayer player, float entityYaw, float partialTicks,
                                               PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                               CallbackInfo ci) {
        SmartMovingRender.afterRender(player);
    }

    @Inject(method = SETUP_ROTATIONS, at = @At("TAIL"))
    private void smartmovingreborn$setupRotations(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
                                                  float bodyYaw, float partialTicks, float scale, CallbackInfo ci) {
        SmartMovingRender.setupRotations(player, poseStack, scale);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void smartmovingreborn$beforeRenderHand(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                    AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
                                                    CallbackInfo ci) {
        SmartMovingRender.setRenderingHand(true);
    }

    @Inject(method = "renderHand", at = @At("TAIL"))
    private void smartmovingreborn$afterRenderHand(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                   AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
                                                   CallbackInfo ci) {
        SmartMovingRender.setRenderingHand(false);
    }
}
