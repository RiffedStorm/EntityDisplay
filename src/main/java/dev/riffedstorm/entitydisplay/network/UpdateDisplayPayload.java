package dev.riffedstorm.entitydisplay.network;

import dev.riffedstorm.entitydisplay.EntityDisplayMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Client → server: move/rotate a display and merge display NBT into it. */
public record UpdateDisplayPayload(int entityId, Vec3 pos, float yRot, float xRot, CompoundTag data)
        implements CustomPacketPayload {
    public static final Type<UpdateDisplayPayload> TYPE = new Type<>(EntityDisplayMod.id("update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateDisplayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(UpdateDisplayPayload::write, UpdateDisplayPayload::read);

    private static UpdateDisplayPayload read(RegistryFriendlyByteBuf buf) {
        return new UpdateDisplayPayload(buf.readVarInt(), NetUtil.readVec3(buf), buf.readFloat(), buf.readFloat(),
                NetUtil.readTag(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
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
