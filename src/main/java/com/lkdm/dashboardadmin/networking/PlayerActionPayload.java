package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlayerActionPayload(String action, String value) implements CustomPacketPayload {
    public static final Type<PlayerActionPayload> TYPE = new Type<>(ModMessages.PLAYER_ACTION);

    public static final StreamCodec<FriendlyByteBuf, PlayerActionPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.action);
            buf.writeUtf(p.value);
        },
        buf -> new PlayerActionPayload(buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
