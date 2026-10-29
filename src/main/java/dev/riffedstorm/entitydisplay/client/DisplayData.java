package dev.riffedstorm.entitydisplay.client;

import com.mojang.math.Transformation;
import dev.riffedstorm.entitydisplay.DisplayNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;

/** Reads and writes the vanilla display NBT on the client (for the inspector and live previews). */
public final class DisplayData {
    private DisplayData() {}

    /** The editable display keys of {@code display}, as vanilla would save them. */
    public static CompoundTag read(Display display) {
        CompoundTag full = display.saveWithoutId(new CompoundTag());
        CompoundTag out = new CompoundTag();
        for (String key : DisplayNbt.allowedKeys(display.getType())) {
            Tag tag = full.get(key);
            if (tag != null) out.put(key, tag.copy());
        }
        return out;
    }

    /** Like {@link #read}, plus a remove list for missing keys, so applying it restores this exact state. */
    public static CompoundTag readForRestore(Display display) {
        CompoundTag data = read(display);
        ListTag remove = new ListTag();
        for (String key : DisplayNbt.allowedKeys(display.getType())) {
            if (!data.contains(key)) remove.add(StringTag.valueOf(key));
        }
        if (!remove.isEmpty()) data.put(DisplayNbt.REMOVE, remove);
        return data;
    }

    /** Applies the edit to the client copy right away, so the player sees it before the server echoes it. */
    public static void preview(Display display, CompoundTag data, double x, double y, double z, float yRot, float xRot) {
        CompoundTag full = display.saveWithoutId(new CompoundTag());
        DisplayNbt.applyPatch(full, DisplayNbt.filter(display.getType(), data));
        display.load(full);
        display.moveTo(x, y, z, yRot, xRot);
    }

    public static Transformation transformation(CompoundTag data) {
        Tag tag = data.get("transformation");
        if (tag == null) return Transformation.identity();
        return Transformation.EXTENDED_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(Transformation.identity());
    }

    public static void setTransformation(CompoundTag data, Transformation t) {
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, t).result().ifPresent(tag -> data.put("transformation", tag));
    }

    /** Orientation the renderer applies for a FIXED billboard before the transformation matrix. */
    public static Quaternionf entityRotation(float yRot, float xRot) {
        return new Quaternionf().rotationYXZ((float) Math.toRadians(-yRot), (float) Math.toRadians(xRot), 0.0F);
    }
}
