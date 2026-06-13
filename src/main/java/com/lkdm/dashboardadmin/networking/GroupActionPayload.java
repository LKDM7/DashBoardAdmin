package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// C2S: group management actions
public record GroupActionPayload(String action, String value) implements CustomPacketPayload {
    public static final Type<GroupActionPayload> TYPE = new Type<>(ModMessages.GROUP_ACTION);
    public static final StreamCodec<FriendlyByteBuf, GroupActionPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.action()); buf.writeUtf(p.value()); },
        buf -> new GroupActionPayload(buf.readUtf(), buf.readUtf())
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
