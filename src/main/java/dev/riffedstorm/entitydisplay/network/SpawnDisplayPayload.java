package dev.riffedstorm.entitydisplay.network;

import dev.riffedstorm.entitydisplay.EntityDisplayMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Client → server: spawn a display entity of {@code type} with the given display NBT. */
public record SpawnDisplayPayload(ResourceLocation entityType, Vec3 pos, float yRot, float xRot, CompoundTag data)
        implements CustomPacketPayload {
    public static final Type<SpawnDisplayPayload> TYPE = new Type<>(EntityDisplayMod.id("spawn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnDisplayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpawnDisplayPayload::write, SpawnDisplayPayload::read);

    private static SpawnDisplayPayload read(RegistryFriendlyByteBuf buf) {
        return new SpawnDisplayPayload(buf.readResourceLocation(), NetUtil.readVec3(buf), buf.readFloat(), buf.readFloat(),
                NetUtil.readTag(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(entityType);
        NetUtil.writeVec3(buf, pos);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
        buf.writeNbt(data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
