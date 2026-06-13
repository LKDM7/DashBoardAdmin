package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ReportImagePayload(String playerName, byte[] imageData) implements CustomPacketPayload {
    public static final Type<ReportImagePayload> TYPE = new Type<>(ModMessages.REPORT_IMAGE);
    public static final StreamCodec<FriendlyByteBuf, ReportImagePayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.playerName);
            buf.writeByteArray(p.imageData);
        },
        buf -> new ReportImagePayload(buf.readUtf(), buf.readByteArray(ReportSubmitPayload.MAX_IMAGE_BYTES))
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
