package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenSanctionsPayload(String data) implements CustomPacketPayload {
    public static final Type<OpenSanctionsPayload> TYPE = new Type<>(ModMessages.OPEN_SANCTIONS);
    public static final StreamCodec<FriendlyByteBuf, OpenSanctionsPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.data),
        buf -> new OpenSanctionsPayload(buf.readUtf())
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
