package Fabric.test.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// S2C: full group state sent when /menu is opened or group changes
public record OpenGroupPayload(
    String members,      // "uuid:name:colorIdx:isAfk:isLeader|..." (empty = not in a group)
    String onlinePlayers,// "name|name|..." (for invite list)
    String pendingInviteFrom, // inviter name if pending invite, else ""
    int    myColorIdx,   // 0-7
    boolean showNames,
    boolean groupTrust,
    String groupName     // custom group name set by leader
) implements CustomPacketPayload {
    public static final Type<OpenGroupPayload> TYPE = new Type<>(ModMessages.OPEN_GROUP);
    public static final StreamCodec<FriendlyByteBuf, OpenGroupPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.members());
            buf.writeUtf(p.onlinePlayers());
            buf.writeUtf(p.pendingInviteFrom());
            buf.writeVarInt(p.myColorIdx());
            buf.writeBoolean(p.showNames());
            buf.writeBoolean(p.groupTrust());
            buf.writeUtf(p.groupName());
        },
        buf -> new OpenGroupPayload(
            buf.readUtf(), buf.readUtf(), buf.readUtf(),
            buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
            buf.readUtf()
        )
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
