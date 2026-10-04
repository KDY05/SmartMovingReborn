package io.github.kdy05.smartmovingreborn.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The game overlay ({@code SmartMovingRender.renderGuiIngame}): the jump charge bar above the health bar
 * ({@code move.gui.jump.charge.bar}), for charged jumps and head jumps. The exhaustion bar is left out, since
 * Easy never fills it.
 */
public final class SmartMovingHud {
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(SmartMovingReborn.MOD_ID, "textures/gui/icons.png");
    private static final int ICON_SIZE = 9;

    private SmartMovingHud() {
    }

    /** Called after vanilla has drawn its overlay. */
    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        SmartMovingClientConfig config = SmartMovingReborn.CLIENT_CONFIG;
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.gameMode == null
                || !minecraft.gameMode.canHurtPlayer() || !config.displayJumpChargeBar.get()) {
            return;
        }
        float jumpMaximum = config.jumpChargeMaximum.get();
        float jumpCharge = Math.min(MovingController.jumpCharge(), jumpMaximum);
        float headJumpMaximum = config.headJumpChargeMaximum.get();
        float headJumpCharge = Math.min(MovingController.headJumpCharge(), headJumpMaximum);
        float maximum = jumpCharge > headJumpCharge ? jumpMaximum : headJumpMaximum;
        float charge = Math.max(jumpCharge, headJumpCharge);
        if (charge <= 0) {
            return;
        }

        boolean full = charge == maximum;
        int fulls = full ? 10 : (int) Math.ceil((charge - 2) * 10.0 / maximum);
        int half = full ? 0 : (int) Math.ceil(charge * 10.0 / maximum) - fulls;
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 39 - 10 - (minecraft.player.getArmorValue() > 0 ? 10 : 0);
        RenderSystem.enableBlend();
        for (int i = 0; i < fulls + half; i++) {
            graphics.blit(ICONS, x + i * 8, y, (i < fulls ? 2 : 3) * ICON_SIZE, 0, ICON_SIZE, ICON_SIZE);
        }
        RenderSystem.disableBlend();
    }
}
