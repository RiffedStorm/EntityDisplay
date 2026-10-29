package dev.riffedstorm.entitydisplay.network;

import dev.riffedstorm.entitydisplay.EntityDisplayMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: remove a display entity. */
public record DeleteDisplayPayload(int entityId) implements CustomPacketPayload {
    public static final Type<DeleteDisplayPayload> TYPE = new Type<>(EntityDisplayMod.id("delete"));
    public static final StreamCodec<ByteBuf, DeleteDisplayPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(DeleteDisplayPayload::new, DeleteDisplayPayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
