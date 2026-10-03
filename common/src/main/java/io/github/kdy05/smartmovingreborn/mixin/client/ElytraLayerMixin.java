package io.github.kdy05.smartmovingreborn.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin {
    /** Before the elytra moves to the back of the body. */
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", ordinal = 0, target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void smartmovingreborn$beforeElytra(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                float partialTicks, float ageInTicks, float netHeadYaw,
                                                float headPitch, CallbackInfo ci) {
        SmartMovingRender.followBreast(entity, poseStack);
    }
}
