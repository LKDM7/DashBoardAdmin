package com.lkdm.dashboardadmin.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S : demande au serveur de consommer instantanément l'aliment du slot donné
 * (index d'inventaire 0..35 + offhand 40). Permet l'auto-manger sans animation
 * d'utilisation, la faim étant gérée côté serveur. Le serveur valide que le slot
 * contient bien de la nourriture et que le joueur peut manger.
 */
public record AutoEatPayload(int slot) implements CustomPacketPayload {
    public static final Type<AutoEatPayload> TYPE = new Type<>(ModMessages.AUTO_EAT);

    public static final StreamCodec<FriendlyByteBuf, AutoEatPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.slot),
            buf -> new AutoEatPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
