package dev.riffedstorm.entitydisplay.client;

import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/** Approximate shapes of display entities, used for click-selection and outlines (displays have no hitbox). */
public final class DisplayBounds {
    private DisplayBounds() {}

    /** The 8 world-space corners of the display's (rotated, scaled) box; index bits are x|y<<1|z<<2. */
    public static Vec3[] corners(Display display, Vec3 pos, float yRot, float xRot, CompoundTag data) {
        float[] box = localBox(display.getType(), data);
        Transformation t = DisplayData.transformation(data);
        Matrix4f m = new Matrix4f().rotation(DisplayData.entityRotation(yRot, xRot)).mul(new Matrix4f(t.getMatrix()));
        Vec3[] out = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vector3f v = m.transformPosition(new Vector3f(
                    box[(i & 1) == 0 ? 0 : 3], box[(i & 2) == 0 ? 1 : 4], box[(i & 4) == 0 ? 2 : 5]));
            out[i] = pos.add(v.x, v.y, v.z);
        }
        return out;
    }

    private static float[] localBox(EntityType<?> type, CompoundTag data) {
        if (type == EntityType.BLOCK_DISPLAY) return new float[]{0, 0, 0, 1, 1, 1};
        if (type == EntityType.TEXT_DISPLAY) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            Component text = Component.empty();
            if (data.contains("text") && mc.level != null) {
                Component parsed = Component.Serializer.fromJson(data.getString("text"), mc.level.registryAccess());
                if (parsed != null) text = parsed;
            }
            int lineWidth = data.contains("line_width") ? data.getInt("line_width") : 200;
            List<FormattedCharSequence> lines = font.split(text, Math.max(1, lineWidth));
            int width = 0;
            for (FormattedCharSequence line : lines) width = Math.max(width, font.width(line));
            float w = Math.max(10, width) * 0.025F / 2 + 0.025F;
            float h = Math.max(1, lines.size()) * font.lineHeight * 0.025F + 0.025F;
            return new float[]{-w, 0, -0.05F, w, h, 0.05F};
        }
        return new float[]{-0.5F, -0.5F, -0.5F, 0.5F, 0.5F, 0.5F};
    }

    public static AABB aabb(Vec3[] corners) {
        AABB box = new AABB(corners[0], corners[0]);
        for (Vec3 c : corners) box = box.minmax(new AABB(c, c));
        return box;
    }

    /** The nearest display whose box the ray hits, or null. */
    public static Display pick(Level level, Vec3 origin, Vec3 dir, double range) {
        Vec3 end = origin.add(dir.scale(range));
        Display best = null;
        double bestDist = Double.MAX_VALUE;
        for (Display display : level.getEntitiesOfClass(Display.class, new AABB(origin, end).inflate(8))) {
            AABB box = aabb(corners(display, display.position(), display.getYRot(), display.getXRot(), DisplayData.read(display)));
            double dist;
            if (box.contains(origin)) {
                dist = 0;
            } else {
                Optional<Vec3> hit = box.clip(origin, end);
                if (hit.isEmpty()) continue;
                dist = hit.get().distanceTo(origin);
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = display;
            }
        }
        return best;
    }
}
