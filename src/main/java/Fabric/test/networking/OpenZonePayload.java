package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenZonePayload(String zonesSerialized, String onlinePlayers)
        implements CustomPacketPayload {

    public static final Type<OpenZonePayload> TYPE = new Type<>(ModMessages.OPEN_ZONE);
    public static final StreamCodec<FriendlyByteBuf, OpenZonePayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.zonesSerialized()); buf.writeUtf(p.onlinePlayers()); },
        buf -> new OpenZonePayload(buf.readUtf(), buf.readUtf())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
