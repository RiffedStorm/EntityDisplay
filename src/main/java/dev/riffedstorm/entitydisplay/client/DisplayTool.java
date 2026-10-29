package dev.riffedstorm.entitydisplay.client;

import dev.riffedstorm.entitydisplay.client.screen.InspectorScreen;
import dev.riffedstorm.entitydisplay.network.DeleteDisplayPayload;
import dev.riffedstorm.entitydisplay.network.SpawnDisplayPayload;
import dev.riffedstorm.entitydisplay.network.UpdateDisplayPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Deque;

/** Client-side state of the display tool: selection, active drag, undo history, and every edit action. */
public final class DisplayTool {
    /** What the gizmo and outline should show for the selected display (live drag values while dragging). */
    public record View(Display display, Vec3 pos, float yRot, float xRot, CompoundTag data) {
        public Vec3[] corners() {
            return DisplayBounds.corners(display, pos, yRot, xRot, data);
        }
    }

    private enum UndoKind { EDIT, RESPAWN, DELETE }

    private record UndoEntry(UndoKind kind, int entityId, ResourceLocation type, Vec3 pos, float yRot, float xRot,
                             CompoundTag data) {}

    public static final double REACH = 64;
    private static final int UNDO_LIMIT = 64;

    private static boolean active;
    private static Gizmo.Mode mode = Gizmo.Mode.MOVE;
    private static int selectedId = -1;
    private static CompoundTag selectedData = new CompoundTag();
    private static int pendingId = -1;
    private static int pendingTicks;
    private static Vec3[] hoveredCorners;
    private static Gizmo.Handle hoveredHandle;
    private static Gizmo.Drag drag;
    private static int sendCooldown;
    private static int skipSpawnUndo;
    private static final Deque<UndoEntry> UNDO = new ArrayDeque<>();

    private DisplayTool() {}

    // ---- state ---------------------------------------------------------------------------------

    public static boolean isActive() {
        return active;
    }

    /** False on servers that don't have this mod: there is nothing to send edits to. */
    public static boolean serverSupported() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(SpawnDisplayPayload.TYPE)) return true;
        message(Component.translatable("entitydisplay.unsupported"));
        return false;
    }

    public static void toggle() {
        if (!active && !serverSupported()) return;
        active = !active;
        if (!active) drag = null;
        message(Component.translatable(active ? "entitydisplay.tool.on" : "entitydisplay.tool.off"));
    }

    public static Gizmo.Mode mode() {
        return mode;
    }

    public static void cycleMode() {
        if (drag == null) mode = mode.next();
    }

    public static Display selected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || selectedId < 0) return null;
        return mc.level.getEntity(selectedId) instanceof Display d && d.isAlive() ? d : null;
    }

    public static void select(Display display) {
        selectedId = display == null ? -1 : display.getId();
        selectedData = display == null ? new CompoundTag() : DisplayData.read(display);
    }

    /** Called when the server confirms a spawn; the entity itself may arrive a few ticks later. */
    public static void selectWhenLoaded(int entityId) {
        if (skipSpawnUndo > 0) skipSpawnUndo--;
        else pushUndo(new UndoEntry(UndoKind.DELETE, entityId, null, null, 0, 0, null));
        pendingId = entityId;
        pendingTicks = 40;
        active = true;
    }

    public static Gizmo.Handle highlightedHandle() {
        return drag != null ? drag.handle : hoveredHandle;
    }

    public static Vec3[] hoveredCorners() {
        return hoveredCorners;
    }

    public static View view() {
        Display display = selected();
        if (display == null) return null;
        if (drag != null) return new View(display, drag.pos, drag.yRot, drag.xRot, drag.data);
        return new View(display, display.position(), display.getYRot(), display.getXRot(), selectedData);
    }

    public static void reset() {
        active = false;
        drag = null;
        selectedId = pendingId = -1;
        hoveredCorners = null;
        hoveredHandle = null;
        UNDO.clear();
    }

    // ---- per tick / per frame ------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (pendingId >= 0) {
            if (mc.level.getEntity(pendingId) instanceof Display d) {
                select(d);
                pendingId = -1;
            } else if (--pendingTicks <= 0) {
                pendingId = -1;
            }
        }

        Display selected = selected();
        if (selected == null) selectedId = -1;

        if (drag != null) {
            if (selected == null || selected.getId() != drag.entityId) {
                drag = null;
            } else {
                // Re-apply every tick: server echoes of older packets would otherwise make it flicker.
                DisplayData.preview(selected, drag.data, drag.pos.x, drag.pos.y, drag.pos.z, drag.yRot, drag.xRot);
                if (drag.dirty && --sendCooldown <= 0) {
                    sendUpdate(drag.entityId, drag.pos, drag.yRot, drag.xRot, drag.data);
                    drag.dirty = false;
                    sendCooldown = 2;
                }
            }
        } else if (selected != null) {
            selectedData = DisplayData.read(selected);
        }

        hoveredCorners = null;
        if (active && drag == null && mc.screen == null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Display hovered = DisplayBounds.pick(mc.level, camera.getPosition(), new Vec3(camera.getLookVector()), REACH);
            if (hovered != null && hovered != selected) {
                hoveredCorners = DisplayBounds.corners(hovered, hovered.position(), hovered.getYRot(), hovered.getXRot(),
                        DisplayData.read(hovered));
            }
        }
    }

    public static void frame(Vec3 origin, Vec3 dir, boolean snap) {
        if (drag != null) {
            drag.update(origin, dir, snap);
            return;
        }
        View view = view();
        hoveredHandle = view == null ? null : Gizmo.pick(mode, view.pos(), Gizmo.size(view.pos(), origin),
                Gizmo.axes(mode, view.yRot(), view.xRot(), view.data()), origin, dir);
    }

    public static void onLeftPress(Vec3 origin, Vec3 dir, Vec3 cameraUp) {
        View view = view();
        if (view != null) {
            Gizmo.Handle handle = Gizmo.pick(mode, view.pos(), Gizmo.size(view.pos(), origin),
                    Gizmo.axes(mode, view.yRot(), view.xRot(), view.data()), origin, dir);
            if (handle != null) {
                drag = Gizmo.Drag.begin(handle, view.display().getId(), view.pos(), view.yRot(), view.xRot(),
                        view.data().copy(), origin, dir, cameraUp);
                sendCooldown = 0;
                if (drag != null) return;
            }
        }
        Minecraft mc = Minecraft.getInstance();
        select(DisplayBounds.pick(mc.level, origin, dir, REACH));
    }

    public static void onLeftRelease() {
        if (drag == null) return;
        Gizmo.Drag finished = drag;
        drag = null;
        if (finished.pos.equals(finished.startPos) && finished.data.equals(finished.startData)) return;
        pushUndo(new UndoEntry(UndoKind.EDIT, finished.entityId, null, finished.startPos, finished.yRot, finished.xRot,
                finished.startData));
        sendUpdate(finished.entityId, finished.pos, finished.yRot, finished.xRot, finished.data);
        selectedData = finished.data;
    }

    // ---- actions -------------------------------------------------------------------------------

    public static void spawn(EntityType<?> type, Vec3 pos, float yRot, float xRot, CompoundTag data) {
        send(new SpawnDisplayPayload(BuiltInRegistries.ENTITY_TYPE.getKey(type), pos, yRot, xRot, data));
    }

    /** Applies an edit from the inspector (undoable). */
    public static void edit(Display display, Vec3 pos, float yRot, float xRot, CompoundTag data) {
        pushUndo(new UndoEntry(UndoKind.EDIT, display.getId(), null, display.position(), display.getYRot(),
                display.getXRot(), DisplayData.readForRestore(display)));
        DisplayData.preview(display, data, pos.x, pos.y, pos.z, yRot, xRot);
        sendUpdate(display.getId(), pos, yRot, xRot, data);
        if (display.getId() == selectedId) selectedData = data.copy();
    }

    public static void duplicate(Display display) {
        spawn(display.getType(), display.position(), display.getYRot(), display.getXRot(), DisplayData.read(display));
    }

    public static void delete(Display display) {
        pushUndo(new UndoEntry(UndoKind.RESPAWN, -1, BuiltInRegistries.ENTITY_TYPE.getKey(display.getType()),
                display.position(), display.getYRot(), display.getXRot(), DisplayData.read(display)));
        send(new DeleteDisplayPayload(display.getId()));
        if (display.getId() == selectedId) select(null);
    }

    public static void undo() {
        UndoEntry entry = UNDO.poll();
        if (entry == null) {
            message(Component.translatable("entitydisplay.undo.empty"));
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (entry.kind()) {
            case EDIT -> {
                if (mc.level.getEntity(entry.entityId()) instanceof Display d) {
                    DisplayData.preview(d, entry.data(), entry.pos().x, entry.pos().y, entry.pos().z, entry.yRot(), entry.xRot());
                }
                sendUpdate(entry.entityId(), entry.pos(), entry.yRot(), entry.xRot(), entry.data());
            }
            case RESPAWN -> {
                skipSpawnUndo++;
                send(new SpawnDisplayPayload(entry.type(), entry.pos(), entry.yRot(), entry.xRot(), entry.data()));
            }
            case DELETE -> {
                send(new DeleteDisplayPayload(entry.entityId()));
                if (entry.entityId() == selectedId) select(null);
            }
        }
    }

    public static void openInspector() {
        if (!serverSupported()) return;
        Minecraft mc = Minecraft.getInstance();
        Display target = selected();
        if (target == null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            target = DisplayBounds.pick(mc.level, camera.getPosition(), new Vec3(camera.getLookVector()), REACH);
        }
        if (target == null) {
            message(Component.translatable("entitydisplay.inspector.none"));
            return;
        }
        select(target);
        mc.setScreen(new InspectorScreen(target));
    }

    /** Where a new display goes: against the block face you look at, or 3 blocks ahead. */
    public static Vec3 spawnPoint() {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        BlockPos pos;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            pos = blockHit.getBlockPos().relative(blockHit.getDirection());
        } else {
            Entity player = mc.player;
            pos = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(3)));
        }
        return Vec3.atCenterOf(pos);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static void sendUpdate(int entityId, Vec3 pos, float yRot, float xRot, CompoundTag data) {
        send(new UpdateDisplayPayload(entityId, pos, yRot, xRot, data));
    }

    private static void send(CustomPacketPayload payload) {
        if (serverSupported()) PacketDistributor.sendToServer(payload);
    }

    private static void pushUndo(UndoEntry entry) {
        UNDO.push(entry);
        while (UNDO.size() > UNDO_LIMIT) UNDO.removeLast();
    }

    private static void message(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(text, true);
    }
}
