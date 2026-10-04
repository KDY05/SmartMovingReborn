package io.github.kdy05.smartmovingreborn.mixin.client;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Redirect(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double smartmovingreborn$fieldOfViewSpeed(AbstractClientPlayer player, Holder<Attribute> attribute) {
        return MovingController.fieldOfViewSpeed((Player) player, player.getAttributeValue(attribute));
    }
}
