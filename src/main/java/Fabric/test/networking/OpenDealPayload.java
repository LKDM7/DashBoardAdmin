package Fabric.test.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record OpenDealPayload(
    String partnerName,
    boolean closed,
    String closeReason,
    boolean myAccepted,
    boolean theirAccepted,
    List<ItemStack> myItems,
    List<ItemStack> theirItems
) implements CustomPacketPayload {

    public static final Type<OpenDealPayload> TYPE = new Type<>(ModMessages.OPEN_DEAL);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDealPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.partnerName());
            buf.writeBoolean(p.closed());
            buf.writeUtf(p.closeReason());
            buf.writeBoolean(p.myAccepted());
            buf.writeBoolean(p.theirAccepted());
            buf.writeVarInt(p.myItems().size());
            p.myItems().forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack));
            buf.writeVarInt(p.theirItems().size());
            p.theirItems().forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack));
        },
        buf -> {
            String partnerName = buf.readUtf();
            boolean closed     = buf.readBoolean();
            String closeReason = buf.readUtf();
            boolean myAcc      = buf.readBoolean();
            boolean theirAcc   = buf.readBoolean();
            int n1 = buf.readVarInt();
            if (n1 < 0 || n1 > 27) throw new IllegalArgumentException("Invalid item count: " + n1);
            List<ItemStack> myItems = new ArrayList<>();
            for (int i = 0; i < n1; i++) myItems.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            int n2 = buf.readVarInt();
            if (n2 < 0 || n2 > 27) throw new IllegalArgumentException("Invalid item count: " + n2);
            List<ItemStack> theirItems = new ArrayList<>();
            for (int i = 0; i < n2; i++) theirItems.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            return new OpenDealPayload(partnerName, closed, closeReason, myAcc, theirAcc, myItems, theirItems);
        }
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
