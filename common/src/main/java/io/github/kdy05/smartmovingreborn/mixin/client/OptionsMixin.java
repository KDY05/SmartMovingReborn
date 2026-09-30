package io.github.kdy05.smartmovingreborn.mixin.client;

import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.BooleanSupplier;

@Mixin(Options.class)
public abstract class OptionsMixin {
    /**
     * Grab uses Left Ctrl, vanilla's sprint default. Move sprint's default to C. A key already saved in
     * options.txt still wins, so this only affects new installs and "Reset Keys".
     */
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ToggleKeyMapping;<init>(Ljava/lang/String;ILjava/lang/String;Ljava/util/function/BooleanSupplier;)V"),
            index = 1)
    private int smartmovingreborn$sprintDefaultKey(String name, int key, String category, BooleanSupplier needsToggle) {
        return "key.sprint".equals(name) ? GLFW.GLFW_KEY_C : key;
    }
}
