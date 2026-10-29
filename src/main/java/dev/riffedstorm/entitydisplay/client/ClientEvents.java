package dev.riffedstorm.entitydisplay.client;

import dev.riffedstorm.entitydisplay.EntityDisplayMod;
import dev.riffedstorm.entitydisplay.client.screen.PaletteScreen;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EntityDisplayMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        while (KeyBindings.TOGGLE_TOOL.consumeClick()) DisplayTool.toggle();

        // The toggle key is the master switch: with the tool off, every other key does nothing.
        // Presses are still consumed so they don't pile up and fire when the tool is turned on.
        boolean active = DisplayTool.isActive();
        while (KeyBindings.PALETTE.consumeClick()) {
            if (active) mc.setScreen(new PaletteScreen());
        }
        while (KeyBindings.INSPECTOR.consumeClick()) {
            if (active) DisplayTool.openInspector();
        }
        while (KeyBindings.CYCLE_MODE.consumeClick()) {
            if (active) DisplayTool.cycleMode();
        }
        while (KeyBindings.DELETE.consumeClick()) {
            Display selected = DisplayTool.selected();
            if (active && selected != null) DisplayTool.delete(selected);
        }
        while (KeyBindings.DUPLICATE.consumeClick()) {
            Display selected = DisplayTool.selected();
            if (active && selected != null) DisplayTool.duplicate(selected);
        }
        while (KeyBindings.UNDO.consumeClick()) {
            if (active) DisplayTool.undo();
        }

        DisplayTool.tick(mc);
    }

    /** While the tool is on, left click selects displays and drags gizmo handles instead of attacking. */
    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || mc.screen != null || mc.player == null
                || !DisplayTool.isActive()) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            event.setCanceled(true);
            Camera camera = mc.gameRenderer.getMainCamera();
            DisplayTool.onLeftPress(camera.getPosition(), new Vec3(camera.getLookVector()), new Vec3(camera.getUpVector()));
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            DisplayTool.onLeftRelease();
        }
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !DisplayTool.isActive()) return;
        Camera camera = event.getCamera();
        Vec3 origin = camera.getPosition();
        DisplayTool.frame(origin, new Vec3(camera.getLookVector()), !KeyBindings.FREE.isDown(),
                KeyBindings.isHeld(KeyBindings.PROPORTIONAL));
        GizmoRenderer.render(event.getModelViewMatrix(), origin, DisplayTool.view(), DisplayTool.hoveredCorners());
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!DisplayTool.isActive() || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        Display selected = DisplayTool.selected();
        String snap = KeyBindings.FREE.isDown() ? "off" : "on";
        g.drawString(mc.font, "Display Tool  |  Mode: " + DisplayTool.mode() + " ["
                + KeyBindings.CYCLE_MODE.getTranslatedKeyMessage().getString() + "]  |  Snap: " + snap, 6, 6, 0xFFFFFF, true);
        String second = selected == null
                ? "Click a display to select it  |  [" + KeyBindings.PALETTE.getTranslatedKeyMessage().getString() + "] spawn palette"
                : "Selected: " + selected.getType().getDescription().getString() + "  |  ["
                + KeyBindings.INSPECTOR.getTranslatedKeyMessage().getString() + "] inspector";
        g.drawString(mc.font, second, 6, 18, 0xAAAAAA, true);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DisplayTool.reset();
    }
}
