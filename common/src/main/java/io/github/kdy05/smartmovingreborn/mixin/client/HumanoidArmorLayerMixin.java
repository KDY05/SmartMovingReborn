package io.github.kdy05.smartmovingreborn.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    /** Right after the armor model took the player model's part poses. */
    @Inject(method = "renderArmorPiece", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void smartmovingreborn$afterCopy(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity,
                                             EquipmentSlot slot, int packedLight, HumanoidModel<?> model,
                                             CallbackInfo ci) {
        SmartMovingRender.afterArmorCopy(model, entity, slot);
    }
}
