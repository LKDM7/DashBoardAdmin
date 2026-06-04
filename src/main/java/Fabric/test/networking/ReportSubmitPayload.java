package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ReportSubmitPayload(String message, byte[] imageData) implements CustomPacketPayload {
    public static final Type<ReportSubmitPayload> TYPE = new Type<>(ModMessages.REPORT_SUBMIT);
    public static final StreamCodec<FriendlyByteBuf, ReportSubmitPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.message);
            buf.writeByteArray(p.imageData);
        },
        buf -> new ReportSubmitPayload(buf.readUtf(), buf.readByteArray())
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
