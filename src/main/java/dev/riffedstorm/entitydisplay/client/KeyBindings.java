package dev.riffedstorm.entitydisplay.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.riffedstorm.entitydisplay.EntityDisplayMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EntityDisplayMod.MOD_ID, value = Dist.CLIENT)
public final class KeyBindings {
    private static final String CATEGORY = "key.categories.entitydisplay";

    public static final KeyMapping TOGGLE_TOOL = key("toggle_tool", GLFW.GLFW_KEY_Y);
    public static final KeyMapping PALETTE = key("palette", GLFW.GLFW_KEY_U);
    public static final KeyMapping INSPECTOR = key("inspector", GLFW.GLFW_KEY_I);
    public static final KeyMapping CYCLE_MODE = key("cycle_mode", GLFW.GLFW_KEY_R);
    public static final KeyMapping DELETE = key("delete", GLFW.GLFW_KEY_DELETE);
    public static final KeyMapping DUPLICATE = key("duplicate", GLFW.GLFW_KEY_N);
    public static final KeyMapping FREE = key("free", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping UNDO = new KeyMapping("key.entitydisplay.undo", KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    private KeyBindings() {}

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.entitydisplay." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : new KeyMapping[]{TOGGLE_TOOL, PALETTE, INSPECTOR, CYCLE_MODE, DELETE, DUPLICATE, FREE, UNDO}) {
            event.register(k);
        }
    }
}
