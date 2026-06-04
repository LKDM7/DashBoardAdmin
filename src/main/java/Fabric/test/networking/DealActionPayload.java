package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DealActionPayload(String action, int slot) implements CustomPacketPayload {

    public static final Type<DealActionPayload> TYPE = new Type<>(ModMessages.DEAL_ACTION);
    public static final StreamCodec<FriendlyByteBuf, DealActionPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.action()); buf.writeVarInt(p.slot()); },
        buf -> new DealActionPayload(buf.readUtf(), buf.readVarInt())
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
