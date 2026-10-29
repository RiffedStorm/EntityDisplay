package dev.riffedstorm.entitydisplay.server;

import dev.riffedstorm.entitydisplay.DisplayNbt;
import dev.riffedstorm.entitydisplay.ServerConfig;
import dev.riffedstorm.entitydisplay.network.DeleteDisplayPayload;
import dev.riffedstorm.entitydisplay.network.SelectDisplayPayload;
import dev.riffedstorm.entitydisplay.network.SpawnDisplayPayload;
import dev.riffedstorm.entitydisplay.network.UpdateDisplayPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Server-side validation and application of every display edit. */
public final class DisplayEditService {
    private DisplayEditService() {}

    public static void handleSpawn(SpawnDisplayPayload payload, IPayloadContext context) {
        ServerPlayer player = editor(context);
        if (player == null || !inReach(player, payload.pos())) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(payload.entityType()).orElse(null);
        if (type == null || !DisplayNbt.isDisplayType(type)) return;

        ServerLevel level = player.serverLevel();
        Entity entity = type.create(level);
        if (!(entity instanceof Display display)) return;
        display.moveTo(payload.pos().x, payload.pos().y, payload.pos().z, payload.yRot(), payload.xRot());
        applyData(display, payload.data());
        level.addFreshEntity(display);
        PacketDistributor.sendToPlayer(player, new SelectDisplayPayload(display.getId()));
    }

    public static void handleUpdate(UpdateDisplayPayload payload, IPayloadContext context) {
        ServerPlayer player = editor(context);
        if (player == null) return;
        Display display = target(player, payload.entityId());
        if (display == null || !inReach(player, payload.pos())) return;
        applyData(display, payload.data());
        display.moveTo(payload.pos().x, payload.pos().y, payload.pos().z, payload.yRot(), payload.xRot());
    }

    public static void handleDelete(DeleteDisplayPayload payload, IPayloadContext context) {
        ServerPlayer player = editor(context);
        if (player == null) return;
        Display display = target(player, payload.entityId());
        if (display != null) display.discard();
    }

    /** Same mechanism as /data merge: save, replace whitelisted keys, load. */
    private static void applyData(Display display, CompoundTag patch) {
        CompoundTag filtered = DisplayNbt.filter(display.getType(), patch);
        if (filtered.isEmpty()) return;
        UUID uuid = display.getUUID();
        CompoundTag full = display.saveWithoutId(new CompoundTag());
        DisplayNbt.applyPatch(full, filtered);
        display.load(full);
        display.setUUID(uuid);
    }

    private static ServerPlayer editor(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) return null;
        boolean allowed = player.hasPermissions(ServerConfig.PERMISSION_LEVEL.get())
                || (ServerConfig.ALLOW_CREATIVE.get() && player.isCreative());
        if (!allowed) {
            player.displayClientMessage(Component.translatable("entitydisplay.denied").withStyle(ChatFormatting.RED), true);
            return null;
        }
        return player;
    }

    private static Display target(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof Display display) || !inReach(player, display.position())) return null;
        return display;
    }

    private static boolean inReach(ServerPlayer player, Vec3 pos) {
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return false;
        double max = ServerConfig.MAX_DISTANCE.get();
        return player.position().distanceToSqr(pos) <= max * max;
    }
}
