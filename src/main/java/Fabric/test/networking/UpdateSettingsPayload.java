package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateSettingsPayload(
    boolean allowPrivateMessages,
    boolean allowTpaRequests,
    boolean allowTrades,
    boolean showChatNotifications,
    boolean showConnectionAlerts
) implements CustomPacketPayload {
    public static final Type<UpdateSettingsPayload> TYPE = new Type<>(ModMessages.UPDATE_SETTINGS);
    public static final StreamCodec<FriendlyByteBuf, UpdateSettingsPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.allowPrivateMessages);
            buf.writeBoolean(p.allowTpaRequests);
            buf.writeBoolean(p.allowTrades);
            buf.writeBoolean(p.showChatNotifications);
            buf.writeBoolean(p.showConnectionAlerts);
        },
        buf -> new UpdateSettingsPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
