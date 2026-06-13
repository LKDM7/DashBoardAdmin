package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// S2C: sent every few ticks to group members with positions + health + AFK
public record GroupUpdatePayload(String updates) implements CustomPacketPayload {
    // format: "uuid:x:y:z:health:isAfk:colorIdx|..."
    public static final Type<GroupUpdatePayload> TYPE = new Type<>(ModMessages.GROUP_UPDATE);
    public static final StreamCodec<FriendlyByteBuf, GroupUpdatePayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.updates()),
        buf -> new GroupUpdatePayload(buf.readUtf())
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
