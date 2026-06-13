package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AdminActionPayload(String action, String target, String value) implements CustomPacketPayload {
    public static final Type<AdminActionPayload> TYPE = new Type<>(ModMessages.ADMIN_ACTION);
    
    public static final StreamCodec<FriendlyByteBuf, AdminActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.action);
                buf.writeUtf(payload.target);
                buf.writeUtf(payload.value);
            },
            buf -> new AdminActionPayload(buf.readUtf(), buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
