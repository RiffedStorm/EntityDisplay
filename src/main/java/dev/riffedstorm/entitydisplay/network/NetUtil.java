package dev.riffedstorm.entitydisplay.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

final class NetUtil {
    private NetUtil() {}

    static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    static void writeVec3(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    static CompoundTag readTag(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag != null ? tag : new CompoundTag();
    }
}
