package dev.riffedstorm.entitydisplay.client.screen;

import com.mojang.math.Transformation;
import dev.riffedstorm.entitydisplay.client.DisplayData;
import dev.riffedstorm.entitydisplay.client.DisplayTool;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/** Pick a block, an item or type some text, and it spawns as a display where you are looking. */
public class PaletteScreen extends Screen {
    private enum Tab {
        BLOCKS("Blocks"), ITEMS("Items"), TEXT("Text");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int CELL = 20, COLS = 10, ROWS = 7;
    private static final int PANEL_W = COLS * CELL + 16;
    private static final int PANEL_H = 28 + 22 + ROWS * CELL + 8;

    private static Tab tab = Tab.BLOCKS;
    private static String query = "";
    private static String textDraft = "Hello!";
    private static List<ItemStack> blockStacks, itemStacks;

    private List<ItemStack> filtered = List.of();
    private int scroll;
    private int left, top;
    private EditBox search;

    public PaletteScreen() {
        super(Component.literal("Spawn Display"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        int tabW = (PANEL_W - 16) / 3;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.literal(t.label), btn -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + 8 + t.ordinal() * tabW, top + 6, tabW - 2, 18).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }

        if (tab == Tab.TEXT) {
            MultiLineEditBox text = new MultiLineEditBox(font, left + 8, top + 30, PANEL_W - 16, ROWS * CELL - 6,
                    Component.literal("Type your text..."), Component.literal("Text"));
            text.setCharacterLimit(2000);
            text.setValue(textDraft);
            text.setValueListener(v -> textDraft = v);
            addRenderableWidget(text);
            setInitialFocus(text);
            addRenderableWidget(Button.builder(Component.literal("Spawn Text"), b -> spawnText())
                    .bounds(left + 8, top + 30 + ROWS * CELL, PANEL_W - 16, 20).build());
        } else {
            search = new EditBox(font, left + 8, top + 30, PANEL_W - 16, 16, Component.literal("Search"));
            search.setHint(Component.literal("Search..."));
            search.setValue(query);
            search.setResponder(v -> {
                query = v;
                scroll = 0;
                refilter();
            });
            addRenderableWidget(search);
            setInitialFocus(search);
            refilter();
        }
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        filtered = stacks().stream().filter(s -> q.isEmpty()
                || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                || BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains(q)).toList();
    }

    private static List<ItemStack> stacks() {
        if (blockStacks == null) {
            blockStacks = BuiltInRegistries.ITEM.stream().filter(i -> i instanceof BlockItem && i != Items.AIR)
                    .map(ItemStack::new).toList();
            itemStacks = BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).map(ItemStack::new).toList();
        }
        return tab == Tab.BLOCKS ? blockStacks : itemStacks;
    }

    private int maxScroll() {
        return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0181A20);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF3A3F4B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top - 12, 0xFFFFFF);
        if (tab == Tab.TEXT) return;

        int gridX = left + 8, gridY = top + 50;
        ItemStack hovered = null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (row + scroll) * COLS + col;
                if (index >= filtered.size()) break;
                int x = gridX + col * CELL, y = gridY + row * CELL;
                boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, over ? 0x60FFFFFF : 0x20FFFFFF);
                g.renderItem(filtered.get(index), x + 2, y + 2);
                if (over) hovered = filtered.get(index);
            }
        }
        if (maxScroll() > 0) {
            int trackH = ROWS * CELL;
            int thumbH = Math.max(10, trackH * ROWS / (ROWS + maxScroll()));
            int thumbY = gridY + (trackH - thumbH) * scroll / maxScroll();
            g.fill(left + PANEL_W - 6, thumbY, left + PANEL_W - 3, thumbY + thumbH, 0xFF8A8F9B);
        }
        if (filtered.isEmpty()) g.drawCenteredString(font, "No matches", width / 2, gridY + 20, 0x888888);
        if (hovered != null) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (tab == Tab.TEXT || button != 0) return false;
        int col = (int) ((mouseX - left - 8) / CELL), row = (int) ((mouseY - top - 50) / CELL);
        if (mouseX < left + 8 || mouseY < top + 50 || col >= COLS || row >= ROWS) return false;
        int index = (row + scroll) * COLS + col;
        if (index >= filtered.size()) return false;
        spawnStack(filtered.get(index));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.TEXT) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    private void spawnStack(ItemStack stack) {
        CompoundTag data = new CompoundTag();
        if (tab == Tab.BLOCKS && stack.getItem() instanceof BlockItem blockItem) {
            data.put("block_state", NbtUtils.writeBlockState(blockItem.getBlock().defaultBlockState()));
            // Offset by -0.5 so the entity origin (the gizmo pivot) sits in the middle of the block.
            DisplayData.setTransformation(data, new Transformation(new Vector3f(-0.5F), new Quaternionf(),
                    new Vector3f(1), new Quaternionf()));
            DisplayTool.spawn(EntityType.BLOCK_DISPLAY, DisplayTool.spawnPoint(), 0, 0, data);
        } else {
            data.put("item", stack.save(minecraft.level.registryAccess()));
            data.putString("item_display", "fixed");
            float yaw = Math.round(facingPlayerYaw() / 90) * 90;
            DisplayTool.spawn(EntityType.ITEM_DISPLAY, DisplayTool.spawnPoint(), yaw, 0, data);
        }
        onClose();
    }

    private void spawnText() {
        if (textDraft.isBlank()) return;
        CompoundTag data = new CompoundTag();
        data.putString("text", Component.Serializer.toJson(Component.literal(textDraft), minecraft.level.registryAccess()));
        DisplayTool.spawn(EntityType.TEXT_DISPLAY, DisplayTool.spawnPoint().subtract(0, 0.5, 0), facingPlayerYaw(), 0, data);
        onClose();
    }

    /** Yaw that makes the front of a FIXED display face the player. */
    private float facingPlayerYaw() {
        return Mth.wrapDegrees(minecraft.player.getYRot() + 180);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
