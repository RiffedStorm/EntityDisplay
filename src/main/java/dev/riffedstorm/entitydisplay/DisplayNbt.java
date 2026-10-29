package dev.riffedstorm.entitydisplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

/**
 * The display NBT keys this mod is allowed to read and write. Edits travel as vanilla
 * display NBT, so the server applies them exactly like {@code /data merge entity}.
 */
public final class DisplayNbt {
    /** List of keys the client wants removed (e.g. "brightness" to drop the override). */
    public static final String REMOVE = "entitydisplay:remove";

    private static final Set<String> SHARED = Set.of(
            "transformation", "interpolation_duration", "teleport_duration", "billboard", "brightness",
            "view_range", "shadow_radius", "shadow_strength", "width", "height", "glow_color_override", "Glowing");
    private static final Set<String> BLOCK = Set.of("block_state");
    private static final Set<String> ITEM = Set.of("item", "item_display");
    private static final Set<String> TEXT = Set.of(
            "text", "line_width", "text_opacity", "background", "alignment", "shadow", "see_through", "default_background");

    private DisplayNbt() {}

    public static boolean isDisplayType(EntityType<?> type) {
        return type == EntityType.BLOCK_DISPLAY || type == EntityType.ITEM_DISPLAY || type == EntityType.TEXT_DISPLAY;
    }

    public static Set<String> allowedKeys(EntityType<?> type) {
        Set<String> keys = new HashSet<>(SHARED);
        if (type == EntityType.BLOCK_DISPLAY) keys.addAll(BLOCK);
        else if (type == EntityType.ITEM_DISPLAY) keys.addAll(ITEM);
        else if (type == EntityType.TEXT_DISPLAY) keys.addAll(TEXT);
        return keys;
    }

    /** Copies only whitelisted keys (and a whitelisted remove list) from {@code in}. */
    public static CompoundTag filter(EntityType<?> type, CompoundTag in) {
        Set<String> allowed = allowedKeys(type);
        CompoundTag out = new CompoundTag();
        for (String key : in.getAllKeys()) {
            if (allowed.contains(key)) out.put(key, in.get(key).copy());
        }
        ListTag remove = new ListTag();
        for (Tag t : in.getList(REMOVE, Tag.TAG_STRING)) {
            if (allowed.contains(t.getAsString())) remove.add(StringTag.valueOf(t.getAsString()));
        }
        if (!remove.isEmpty()) out.put(REMOVE, remove);
        return out;
    }

    /** Merges a filtered patch into a full entity save tag, replacing (not deep-merging) each key. */
    public static void applyPatch(CompoundTag full, CompoundTag filteredPatch) {
        for (Tag t : filteredPatch.getList(REMOVE, Tag.TAG_STRING)) full.remove(t.getAsString());
        for (String key : filteredPatch.getAllKeys()) {
            if (!key.equals(REMOVE)) full.put(key, filteredPatch.get(key).copy());
        }
    }
}
