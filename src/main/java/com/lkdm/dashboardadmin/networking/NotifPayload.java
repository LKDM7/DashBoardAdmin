package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NotifPayload(String notifType, String message) implements CustomPacketPayload {
    public static final Type<NotifPayload> TYPE = new Type<>(ModMessages.NOTIF);
    public static final StreamCodec<FriendlyByteBuf, NotifPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.notifType); buf.writeUtf(p.message); },
        buf -> new NotifPayload(buf.readUtf(), buf.readUtf())
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
