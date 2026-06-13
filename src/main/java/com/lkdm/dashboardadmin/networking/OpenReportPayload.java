package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenReportPayload() implements CustomPacketPayload {
    public static final Type<OpenReportPayload> TYPE = new Type<>(ModMessages.OPEN_REPORT);
    public static final StreamCodec<FriendlyByteBuf, OpenReportPayload> CODEC = StreamCodec.of(
        (buf, p) -> {},
        buf -> new OpenReportPayload()
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
