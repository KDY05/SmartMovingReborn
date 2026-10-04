package io.github.kdy05.smartmovingreborn.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@code setPartVisibility} rather than the call to it in {@code renderArmorPiece}, which NeoForge moves into
 * an overload with more arguments.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    /** The entity whose armor is being drawn. */
    @Unique
    private LivingEntity smartmovingreborn$entity;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"))
    private void smartmovingreborn$render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                          LivingEntity entity, float limbSwing, float limbSwingAmount,
                                          float partialTicks, float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo ci) {
        smartmovingreborn$entity = entity;
    }

    /** Right after the armor model took the player model's part poses. */
    @Inject(method = "setPartVisibility", at = @At("TAIL"))
    private void smartmovingreborn$afterCopy(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo ci) {
        if (smartmovingreborn$entity != null) {
            SmartMovingRender.afterArmorCopy(model, smartmovingreborn$entity, slot);
        }
    }
}
