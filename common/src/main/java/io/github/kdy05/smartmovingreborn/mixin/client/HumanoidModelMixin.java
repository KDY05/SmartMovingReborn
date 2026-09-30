package io.github.kdy05.smartmovingreborn.mixin.client;

import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armor needs nothing extra: {@code HumanoidArmorLayer} copies these part poses (scale included) into the
 * armor model, and {@code PlayerModel} copies them into the sleeves and trousers after this returns.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void smartmovingreborn$beforeSetupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                   float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        SmartMovingRender.beforeSetupAnim((HumanoidModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void smartmovingreborn$setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                             float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        SmartMovingRender.setupAnim((HumanoidModel<?>) (Object) this, entity, limbSwing, limbSwingAmount, netHeadYaw);
    }
}
