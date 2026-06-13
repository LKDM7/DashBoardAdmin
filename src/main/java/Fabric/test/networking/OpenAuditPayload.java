package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenAuditPayload(String data) implements CustomPacketPayload {
    public static final Type<OpenAuditPayload> TYPE = new Type<>(ModMessages.OPEN_AUDIT);
    public static final StreamCodec<FriendlyByteBuf, OpenAuditPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.data),
        buf -> new OpenAuditPayload(buf.readUtf())
    );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
