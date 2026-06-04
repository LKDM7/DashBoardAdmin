package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlayerLogsPayload(String playerName, String logsSerialized) implements CustomPacketPayload {
    public static final Type<PlayerLogsPayload> TYPE = new Type<>(ModMessages.PLAYER_LOGS);

    public static final StreamCodec<FriendlyByteBuf, PlayerLogsPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.playerName);
            buf.writeUtf(p.logsSerialized);
        },
        buf -> new PlayerLogsPayload(buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
