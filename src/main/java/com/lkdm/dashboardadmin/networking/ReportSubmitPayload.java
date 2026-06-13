package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ReportSubmitPayload(String message, byte[] imageData) implements CustomPacketPayload {
    /** Plafond de taille pour une capture de report (sous la limite vanilla de 1 MiB par payload).
     *  Empêche un client malveillant de saturer la mémoire serveur via des images géantes. */
    public static final int MAX_IMAGE_BYTES = 768 * 1024;

    public static final Type<ReportSubmitPayload> TYPE = new Type<>(ModMessages.REPORT_SUBMIT);
    public static final StreamCodec<FriendlyByteBuf, ReportSubmitPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.message);
            buf.writeByteArray(p.imageData);
        },
        buf -> new ReportSubmitPayload(buf.readUtf(), buf.readByteArray(MAX_IMAGE_BYTES))
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
