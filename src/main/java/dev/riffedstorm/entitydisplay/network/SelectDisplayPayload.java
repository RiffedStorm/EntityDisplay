package dev.riffedstorm.entitydisplay.network;

import dev.riffedstorm.entitydisplay.EntityDisplayMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: select a display the player just spawned. */
public record SelectDisplayPayload(int entityId) implements CustomPacketPayload {
    public static final Type<SelectDisplayPayload> TYPE = new Type<>(EntityDisplayMod.id("select"));
    public static final StreamCodec<ByteBuf, SelectDisplayPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SelectDisplayPayload::new, SelectDisplayPayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
