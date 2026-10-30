package dev.riffedstorm.entitydisplay.client.screen;

import com.mojang.math.Transformation;
import dev.riffedstorm.entitydisplay.DisplayNbt;
import dev.riffedstorm.entitydisplay.client.DisplayData;
import dev.riffedstorm.entitydisplay.client.DisplayTool;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Side panel for exact values. The world stays visible on the left, the gizmo keeps rendering. */
public class InspectorScreen extends Screen {
    private enum Tab {
        TRANSFORM("Transform"), DISPLAY("Display"), CONTENT("Content");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Label(String text, int x, int y) {}

    private static final class InvalidField extends Exception {
        InvalidField(String field) {
            super("Invalid value: " + field);
        }
    }

    private static final int PANEL_W = 236, ROW = 20, LABEL_W = 48;
    private static final List<String> BILLBOARDS = List.of("fixed", "vertical", "horizontal", "center");
    private static final List<String> ITEM_MODES = List.of("none", "fixed", "ground", "gui", "head",
            "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand");
    private static final List<String> ALIGNMENTS = List.of("center", "left", "right");

    /** Scroll-wheel step per field: {normal, with Shift (fine), with Ctrl (coarse), min, max}. */
    private static final Map<String, double[]> SCROLL = new HashMap<>();

    static {
        double inf = Double.MAX_VALUE;
        for (String k : List.of("px", "py", "pz", "tx", "ty", "tz")) SCROLL.put(k, new double[]{1 / 16.0, 1 / 64.0, 1, -inf, inf});
        for (String k : List.of("sx", "sy", "sz")) SCROLL.put(k, new double[]{1 / 16.0, 1 / 64.0, 1, 1 / 64.0, inf});
        for (String k : List.of("yaw", "pitch", "rx", "ry", "rz")) SCROLL.put(k, new double[]{15, 1, 45, -inf, inf});
        for (String k : List.of("block_light", "sky_light")) SCROLL.put(k, new double[]{1, 1, 5, 0, 15});
        for (String k : List.of("view_range", "shadow_radius", "shadow_strength")) SCROLL.put(k, new double[]{0.1, 0.01, 1, 0, inf});
        for (String k : List.of("width", "height")) SCROLL.put(k, new double[]{0.25, 1 / 16.0, 1, 0, inf});
        SCROLL.put("interp", new double[]{1, 1, 10, 0, inf});
        SCROLL.put("teleport", new double[]{1, 1, 10, 0, 59});
        SCROLL.put("line_width", new double[]{10, 1, 50, 1, inf});
        SCROLL.put("opacity", new double[]{5, 1, 25, 0, 255});
    }

    private static Tab tab = Tab.TRANSFORM;

    private final Display display;
    private final Map<String, String> values = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private final Map<EditBox, String> boxKeys = new HashMap<>();
    private CompoundTag original;
    private Quaternionf rightRotation;
    private String billboard, itemDisplay, alignment;
    private boolean brightnessOn, glowing, textShadow, seeThrough, defaultBackground;
    private String status;
    private int statusColor;
    private int left, x, contentW;

    public InspectorScreen(Display display) {
        super(Component.literal("Display Inspector"));
        this.display = display;
        readEntity();
    }

    // ---- entity <-> fields ---------------------------------------------------------------------

    private void readEntity() {
        original = DisplayData.read(display);
        CompoundTag d = original;
        Vec3 p = display.position();
        put("px", p.x);
        put("py", p.y);
        put("pz", p.z);
        put("yaw", display.getYRot());
        put("pitch", display.getXRot());

        Transformation t = DisplayData.transformation(d);
        Vector3f tr = new Vector3f(t.getTranslation());
        Vector3f euler = new Quaternionf(t.getLeftRotation()).getEulerAnglesXYZ(new Vector3f());
        Vector3f sc = new Vector3f(t.getScale());
        rightRotation = new Quaternionf(t.getRightRotation());
        put("tx", tr.x);
        put("ty", tr.y);
        put("tz", tr.z);
        put("rx", Math.toDegrees(euler.x));
        put("ry", Math.toDegrees(euler.y));
        put("rz", Math.toDegrees(euler.z));
        put("sx", sc.x);
        put("sy", sc.y);
        put("sz", sc.z);

        billboard = d.contains("billboard") ? d.getString("billboard") : "fixed";
        glowing = d.getBoolean("Glowing");
        brightnessOn = d.contains("brightness");
        CompoundTag light = d.getCompound("brightness");
        values.put("block_light", String.valueOf(brightnessOn ? light.getInt("block") : 15));
        values.put("sky_light", String.valueOf(brightnessOn ? light.getInt("sky") : 15));
        put("view_range", d.contains("view_range") ? d.getFloat("view_range") : 1);
        put("shadow_radius", d.getFloat("shadow_radius"));
        put("shadow_strength", d.contains("shadow_strength") ? d.getFloat("shadow_strength") : 1);
        put("width", d.getFloat("width"));
        put("height", d.getFloat("height"));
        int glow = d.contains("glow_color_override") ? d.getInt("glow_color_override") : -1;
        values.put("glow", glow == -1 ? "" : String.format("%06X", glow & 0xFFFFFF));
        values.put("interp", String.valueOf(d.getInt("interpolation_duration")));
        values.put("teleport", String.valueOf(d.getInt("teleport_duration")));

        EntityType<?> type = display.getType();
        if (type == EntityType.BLOCK_DISPLAY) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), d.getCompound("block_state"));
            values.put("block", BlockStateParser.serialize(state));
        } else if (type == EntityType.ITEM_DISPLAY) {
            values.put("item", d.getCompound("item").getString("id"));
            itemDisplay = d.contains("item_display") ? d.getString("item_display") : "none";
        } else {
            values.put("text", textForEditing(d.getString("text")));
            values.put("line_width", String.valueOf(d.contains("line_width") ? d.getInt("line_width") : 200));
            values.put("background", String.format("%08X", d.contains("background") ? d.getInt("background") : 0x40000000));
            values.put("opacity", String.valueOf(d.contains("text_opacity") ? d.getByte("text_opacity") & 0xFF : 255));
            alignment = d.contains("alignment") ? d.getString("alignment") : "center";
            textShadow = d.getBoolean("shadow");
            seeThrough = d.getBoolean("see_through");
            defaultBackground = d.getBoolean("default_background");
        }
    }

    /** Plain text when the component is plain, otherwise its raw JSON. */
    private String textForEditing(String json) {
        if (json.isEmpty()) return "";
        try {
            Component c = Component.Serializer.fromJson(json, registries());
            if (c == null) return json;
            return Component.literal(c.getString()).equals(c) ? c.getString() : json;
        } catch (Exception e) {
            return json;
        }
    }

    private CompoundTag buildData() throws InvalidField {
        CompoundTag d = original.copy();
        ListTag remove = new ListTag();

        Quaternionf left = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(f("rx", "Rotation X")), (float) Math.toRadians(f("ry", "Rotation Y")),
                (float) Math.toRadians(f("rz", "Rotation Z")));
        DisplayData.setTransformation(d, new Transformation(
                new Vector3f(f("tx", "Offset X"), f("ty", "Offset Y"), f("tz", "Offset Z")), left,
                new Vector3f(f("sx", "Scale X"), f("sy", "Scale Y"), f("sz", "Scale Z")), rightRotation));

        d.putString("billboard", billboard);
        d.putBoolean("Glowing", glowing);
        if (brightnessOn) {
            CompoundTag light = new CompoundTag();
            light.putInt("block", Math.clamp(i("block_light", "Block light"), 0, 15));
            light.putInt("sky", Math.clamp(i("sky_light", "Sky light"), 0, 15));
            d.put("brightness", light);
        } else {
            d.remove("brightness");
            remove.add(StringTag.valueOf("brightness"));
        }
        d.putFloat("view_range", Math.max(0, f("view_range", "View range")));
        d.putFloat("shadow_radius", Math.max(0, f("shadow_radius", "Shadow radius")));
        d.putFloat("shadow_strength", Math.max(0, f("shadow_strength", "Shadow strength")));
        d.putFloat("width", Math.max(0, f("width", "Cull width")));
        d.putFloat("height", Math.max(0, f("height", "Cull height")));
        String glow = values.get("glow").trim();
        d.putInt("glow_color_override", glow.isEmpty() ? -1 : hex(glow, "Glow color") & 0xFFFFFF);
        d.putInt("interpolation_duration", Math.max(0, i("interp", "Interpolation")));
        d.putInt("teleport_duration", Math.clamp(i("teleport", "Teleport duration"), 0, 59));

        EntityType<?> type = display.getType();
        if (type == EntityType.BLOCK_DISPLAY) {
            try {
                BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), values.get("block").trim(), false).blockState();
                d.put("block_state", NbtUtils.writeBlockState(state));
            } catch (Exception e) {
                throw new InvalidField("Block state");
            }
        } else if (type == EntityType.ITEM_DISPLAY) {
            String id = values.get("item").trim();
            if (!id.equals(original.getCompound("item").getString("id"))) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
                if (item == null) throw new InvalidField("Item id");
                d.put("item", new ItemStack(item).save(registries()));
            }
            d.putString("item_display", itemDisplay);
        } else {
            d.putString("text", textJson(values.get("text")));
            d.putInt("line_width", Math.max(1, i("line_width", "Line width")));
            d.putInt("background", hex(values.get("background").trim(), "Background"));
            d.putByte("text_opacity", (byte) Math.clamp(i("opacity", "Opacity"), 0, 255));
            d.putString("alignment", alignment);
            d.putBoolean("shadow", textShadow);
            d.putBoolean("see_through", seeThrough);
            d.putBoolean("default_background", defaultBackground);
        }
        if (!remove.isEmpty()) d.put(DisplayNbt.REMOVE, remove);
        return d;
    }

    private String textJson(String raw) throws InvalidField {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
            try {
                Component c = Component.Serializer.fromJson(trimmed, registries());
                if (c != null) return Component.Serializer.toJson(c, registries());
            } catch (Exception ignored) {
                // fall through
            }
            throw new InvalidField("Text JSON");
        }
        return Component.Serializer.toJson(Component.literal(raw), registries());
    }

    private void apply() {
        apply(false);
    }

    private void apply(boolean fromScroll) {
        try {
            CompoundTag data = buildData();
            Vec3 pos = new Vec3(f("px", "Position X"), f("py", "Position Y"), f("pz", "Position Z"));
            DisplayTool.edit(display, pos, f("yaw", "Yaw"), f("pitch", "Pitch"), data, fromScroll);
            original = data.copy();
            original.remove(DisplayNbt.REMOVE);
            setStatus("Applied", 0x7CFC9A);
        } catch (InvalidField e) {
            setStatus(e.getMessage(), 0xFF6B6B);
        }
    }

    // ---- layout --------------------------------------------------------------------------------

    @Override
    protected void init() {
        labels.clear();
        boxKeys.clear();
        left = width - PANEL_W;
        x = left + 6;
        contentW = PANEL_W - 12;

        int tabW = contentW / 3;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.literal(t.label), btn -> {
                tab = t;
                rebuildWidgets();
            }).bounds(x + t.ordinal() * tabW, 22, tabW - 2, 18).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }

        int y = 46;
        switch (tab) {
            case TRANSFORM -> {
                triple(y, "Position", "px", "py", "pz");
                pair(y += ROW, "Yaw", "yaw", "Pitch", "pitch");
                triple(y += ROW, "Offset", "tx", "ty", "tz");
                triple(y += ROW, "Rotation", "rx", "ry", "rz");
                triple(y += ROW, "Scale", "sx", "sy", "sz");
                y += ROW + 2;
                int w = (contentW - 4) / 3;
                button(x, y, w, "Reset rot.", () -> {
                    for (String k : new String[]{"rx", "ry", "rz", "yaw", "pitch"}) values.put(k, "0");
                });
                button(x + w + 2, y, w, "Reset scale", () -> {
                    for (String k : new String[]{"sx", "sy", "sz"}) values.put(k, "1");
                });
                button(x + 2 * (w + 2), y, w, "Center block", () -> {
                    for (String k : new String[]{"px", "py", "pz"}) {
                        try {
                            put(k, Math.floor(Double.parseDouble(values.get(k))) + 0.5);
                        } catch (NumberFormatException ignored) {
                            // leave invalid input for the user to fix
                        }
                    }
                });
            }
            case DISPLAY -> {
                int half = contentW / 2 - 1;
                cycle(x, y, half, "Billboard", BILLBOARDS, billboard, v -> billboard = v);
                toggle(x + half + 2, y, half, "Glowing", glowing, v -> glowing = v);
                toggle(x, y += ROW, contentW, "Light override", brightnessOn, v -> brightnessOn = v);
                pair(y += ROW, "Block lt", "block_light", "Sky lt", "sky_light");
                pair(y += ROW, "View", "view_range", "Glow hex", "glow");
                pair(y += ROW, "Shadow r", "shadow_radius", "Shadow s", "shadow_strength");
                pair(y += ROW, "Interp", "interp", "Teleport", "teleport");
                pair(y += ROW, "Cull W", "width", "Cull H", "height");
            }
            case CONTENT -> contentTab(y);
        }

        int bw = (contentW - 6) / 4;
        int by = height - 26;
        button(x, by, bw, "Apply", this::apply);
        button(x + (bw + 2), by, bw, "Duplicate", () -> {
            DisplayTool.duplicate(display);
            onClose();
        });
        button(x + 2 * (bw + 2), by, bw, "Delete", () -> {
            DisplayTool.delete(display);
            onClose();
        });
        button(x + 3 * (bw + 2), by, bw, "Close", this::onClose);
    }

    private void contentTab(int y) {
        EntityType<?> type = display.getType();
        if (type == EntityType.BLOCK_DISPLAY) {
            labels.add(new Label("Block state", x, y));
            box(x, y + 10, contentW, "block").setMaxLength(512);
            labels.add(new Label("e.g. minecraft:oak_stairs[facing=east]", x, y + 32));
        } else if (type == EntityType.ITEM_DISPLAY) {
            labels.add(new Label("Item id", x, y));
            box(x, y + 10, contentW, "item");
            cycle(x, y + 32, contentW, "Display mode", ITEM_MODES, itemDisplay, v -> itemDisplay = v);
        } else {
            labels.add(new Label("Text (plain, or JSON component)", x, y));
            MultiLineEditBox text = new MultiLineEditBox(font, x, y + 10, contentW, 60,
                    Component.literal("Text..."), Component.literal("Text"));
            text.setCharacterLimit(4000);
            text.setValue(values.get("text"));
            text.setValueListener(v -> values.put("text", v));
            addRenderableWidget(text);
            y += 76;
            pair(y, "Width", "line_width", "Opacity", "opacity");
            pair(y += ROW, "Bg ARGB", "background", null, null);
            cycle(x + contentW / 2 + 1, y, contentW / 2 - 1, "Align", ALIGNMENTS, alignment, v -> alignment = v);
            int w = (contentW - 4) / 3;
            toggle(x, y += ROW, w, "Shadow", textShadow, v -> textShadow = v);
            toggle(x + w + 2, y, w, "See-thru", seeThrough, v -> seeThrough = v);
            toggle(x + 2 * (w + 2), y, w, "Def. bg", defaultBackground, v -> defaultBackground = v);
        }
    }

    private void triple(int y, String label, String k1, String k2, String k3) {
        labels.add(new Label(label, x, y + 4));
        int w = (contentW - LABEL_W - 4) / 3;
        box(x + LABEL_W, y, w, k1);
        box(x + LABEL_W + w + 2, y, w, k2);
        box(x + LABEL_W + 2 * (w + 2), y, w, k3);
    }

    private void pair(int y, String l1, String k1, String l2, String k2) {
        int half = contentW / 2;
        labels.add(new Label(l1, x, y + 4));
        box(x + LABEL_W, y, half - LABEL_W - 2, k1);
        if (k2 == null) return;
        labels.add(new Label(l2, x + half + 2, y + 4));
        box(x + half + 2 + LABEL_W, y, half - LABEL_W - 2, k2);
    }

    private EditBox box(int bx, int y, int w, String key) {
        EditBox b = new EditBox(font, bx, y, w, 16, Component.literal(key));
        b.setMaxLength(256);
        b.setValue(values.getOrDefault(key, ""));
        b.setResponder(v -> values.put(key, v));
        boxKeys.put(b, key);
        addRenderableWidget(b);
        return b;
    }

    private void button(int bx, int y, int w, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> {
            action.run();
            if (minecraft.screen == this) rebuildWidgets();
        }).bounds(bx, y, w, 18).build());
    }

    private void cycle(int bx, int y, int w, String label, List<String> options, String current, Consumer<String> set) {
        addRenderableWidget(CycleButton.<String>builder(Component::literal).withValues(options)
                .withInitialValue(options.contains(current) ? current : options.getFirst())
                .create(bx, y, w, 18, Component.literal(label), (b, v) -> set.accept(v)));
    }

    private void toggle(int bx, int y, int w, String label, boolean current, Consumer<Boolean> set) {
        addRenderableWidget(CycleButton.onOffBuilder(current).create(bx, y, w, 18, Component.literal(label),
                (b, v) -> set.accept(v)));
    }

    // ---- screen --------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(left, 0, width, height, 0xD0181A20);
        g.fill(left, 0, left + 1, height, 0xFF3A3F4B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, display.getType().getDescription().getString() + "  #" + display.getId(), x, 8, 0xFFFFFF);
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), 0xB8BCC6, false);
        if (status != null) g.drawString(font, status, x, height - 38, statusColor);
    }

    @Override
    public void tick() {
        if (!display.isAlive()) onClose();
    }

    /** Hover a number field and scroll to nudge it; Shift = fine steps, Ctrl = big steps. Applies live. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (Map.Entry<EditBox, String> e : boxKeys.entrySet()) {
            EditBox box = e.getKey();
            double[] step = SCROLL.get(e.getValue());
            if (step == null || scrollY == 0 || !box.isMouseOver(mouseX, mouseY)) continue;
            double current;
            try {
                current = Double.parseDouble(box.getValue().trim());
            } catch (NumberFormatException ex) {
                return true;
            }
            double amount = hasShiftDown() ? step[1] : hasControlDown() ? step[2] : step[0];
            double next = Math.clamp(current + Math.signum(scrollY) * amount, step[3], step[4]);
            put(e.getValue(), next);
            box.setValue(values.get(e.getValue()));
            apply(true);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof EditBox) {
            apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- small helpers -------------------------------------------------------------------------

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    private HolderLookup.Provider registries() {
        return display.level().registryAccess();
    }

    private void put(String key, double value) {
        String s = new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        values.put(key, s.equals("-0") ? "0" : s);
    }

    private float f(String key, String name) throws InvalidField {
        try {
            float v = Float.parseFloat(values.getOrDefault(key, "").trim());
            if (Float.isFinite(v)) return v;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new InvalidField(name);
    }

    private int i(String key, String name) throws InvalidField {
        try {
            return Integer.parseInt(values.getOrDefault(key, "").trim());
        } catch (NumberFormatException e) {
            throw new InvalidField(name);
        }
    }

    private static int hex(String s, String name) throws InvalidField {
        try {
            return Integer.parseUnsignedInt(s.startsWith("#") ? s.substring(1) : s, 16);
        } catch (NumberFormatException e) {
            throw new InvalidField(name);
        }
    }
}
