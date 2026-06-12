package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenSettingsPayload(
    boolean allowPrivateMessages,
    boolean allowTpaRequests,
    boolean allowTrades,
    boolean showChatNotifications,
    boolean showConnectionAlerts,
    String commands,
    String homes,
    String lockedBlocks,
    String trustedPlayers,
    String stats,
    String buildInfo,
    String warps
) implements CustomPacketPayload {
    public static final Type<OpenSettingsPayload> TYPE = new Type<>(ModMessages.OPEN_SETTINGS);
    public static final StreamCodec<FriendlyByteBuf, OpenSettingsPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.allowPrivateMessages);
            buf.writeBoolean(p.allowTpaRequests);
            buf.writeBoolean(p.allowTrades);
            buf.writeBoolean(p.showChatNotifications);
            buf.writeBoolean(p.showConnectionAlerts);
            buf.writeUtf(p.commands);
            buf.writeUtf(p.homes);
            buf.writeUtf(p.lockedBlocks);
            buf.writeUtf(p.trustedPlayers);
            buf.writeUtf(p.stats);
            buf.writeUtf(p.buildInfo);
            buf.writeUtf(p.warps);
        },
        buf -> new OpenSettingsPayload(
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(),
            buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
            buf.readUtf(), buf.readUtf(), buf.readUtf()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
