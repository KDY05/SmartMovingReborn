package io.github.kdy05.smartmovingreborn.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Smart Moving's own key mappings. Each loader registers {@link #ALL} during client setup. */
public final class KeyBindings {
    private static final String CATEGORY = "key.categories." + SmartMovingReborn.MOD_ID;

    public static final KeyMapping GRAB = new KeyMapping("key." + SmartMovingReborn.MOD_ID + ".grab",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    public static final KeyMapping TOGGLE = new KeyMapping("key." + SmartMovingReborn.MOD_ID + ".toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY);

    public static final List<KeyMapping> ALL = List.of(GRAB, TOGGLE);

    private KeyBindings() {
    }
}
