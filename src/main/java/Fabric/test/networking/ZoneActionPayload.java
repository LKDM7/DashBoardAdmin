package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ZoneActionPayload(String action, String zoneName, String value)
        implements CustomPacketPayload {

    public static final Type<ZoneActionPayload> TYPE = new Type<>(ModMessages.ZONE_ACTION);
    public static final StreamCodec<FriendlyByteBuf, ZoneActionPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.action()); buf.writeUtf(p.zoneName()); buf.writeUtf(p.value()); },
        buf -> new ZoneActionPayload(buf.readUtf(), buf.readUtf(), buf.readUtf())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
